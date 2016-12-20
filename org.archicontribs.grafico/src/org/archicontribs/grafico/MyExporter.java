/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package org.archicontribs.grafico;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ExtensibleURIConverterImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMLResourceFactoryImpl;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;

import com.archimatetool.editor.model.IArchiveManager;
import com.archimatetool.editor.model.IModelExporter;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelImageProvider;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IIdentifier;
import com.archimatetool.model.IFolderContainer;


/**
 * GRAFICO Model Exporter
 * GRAFICO (Git fRiendly Archi FIle COllection) is a way to persist an ArchiMate
 * model in a bunch of XML files (one file per ArchiMate element or view).
 * 
 * @author Jean-Baptiste Sarrodie
 * @author Quentin Varquet
 * @author Phillip Beauvoir
 */
public class MyExporter implements IModelExporter {
	
	// This resourceSet will be recreated for each model export
	ResourceSet resourceSet;
	
	// Filename to use for serialization of folder elements
	static final String FOLDER_XML = "folder.xml";
	
	// Name of folders for model and images
	// /!\ IMAGES_FOLDER must match the prefix used in ArchiveManager.createArchiveImagePathname
	static final String IMAGES_FOLDER = "images";
	static final String MODEL_FOLDER = "model";
    
    public MyExporter() {
    }

    @Override
    public void export(IArchimateModel model) throws IOException {
    	File folder = askSaveFolder();
    	
    	if(folder == null) {
            return;
        }
    	
    	// Define target folders for model and images
    	// Delete them and re-create them (remark: FileUtils.deleteFolder() does sanity checks)
    	File modelFolder = new File(folder, MODEL_FOLDER);
    	FileUtils.deleteFolder(modelFolder);
    	modelFolder.mkdirs();
    	File imagesFolder = new File(folder, IMAGES_FOLDER);
    	FileUtils.deleteFolder(imagesFolder);
    	imagesFolder.mkdirs();
    	
    	// Save model images (if any): this has to be done on original model (not a copy)
    	saveImages(model, imagesFolder.getParentFile());
    	
    	// Create ResourceSet
    	resourceSet = new ResourceSetImpl();
    	resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMLResourceFactoryImpl()); //$NON-NLS-1$
    	// Add a URIConverter that will be used to map full filenames to logical names
    	resourceSet.setURIConverter(new ExtensibleURIConverterImpl());
    	
    	// Now work on a copy
    	IArchimateModel copy = EcoreUtil.copy(model);
    	// Create directory structure and prepare all Resources
    	createAndSaveResourceForFolder(copy, modelFolder);
    	
    	// Now save all Resources
    	for (Resource resource: resourceSet.getResources()) {
            resource.save(null);
    	}
    }
    
    /**
     * For each folder inside model, create a directory and a Resource to save it.
     * For each element, create a Resource to save it
     * 
     * @param folderContainer Model or folder to work on 
     * @param folder Directory in which to generate files
     * @throws IOException
     */
    private void createAndSaveResourceForFolder(IFolderContainer folderContainer, File folder) throws IOException {
		// Save each children folders
    	List<IFolder> allFolders = new ArrayList<IFolder>();
    	allFolders.addAll(folderContainer.getFolders());
		for (IFolder tmpFolder: allFolders) {
			File tmpFolderFile = new File(folder, getNameFor(tmpFolder));
			tmpFolderFile.mkdirs();
			createAndSaveResource(new File(tmpFolderFile, FOLDER_XML), tmpFolder);
			createAndSaveResourceForFolder(tmpFolder, tmpFolderFile);
		}		
		// Save each children elements
		if (folderContainer instanceof IFolder) {
    		// Save each children element
			List<EObject> allElements = new ArrayList<EObject>();
			allElements.addAll(((IFolder) folderContainer).getElements());
    		for (EObject tmpElement: allElements) {
    			createAndSaveResource(new File(folder, tmpElement.getClass().getSimpleName()+"_"+((IIdentifier)tmpElement).getId()+".xml"), tmpElement); //$NON-NLS-1$
    		}
		}
		if (folderContainer instanceof IArchimateModel) {
			createAndSaveResource(new File(folder, FOLDER_XML), folderContainer);
		}
    }
    
    /**
     * Generate a proper name for directory creation
     *  
     * @param folder
     * @return
     */
    private String getNameFor(IFolder folder) {
    	return folder.getType() == FolderType.USER ? folder.getId().toString() : folder.getType().toString(); //$NON-NLS-1$
    }
    
    /**
     * Save the model to Resource
     * 
     * @param file
     * @param object
     * @throws IOException
     */
    private void createAndSaveResource(File file, EObject object) throws IOException {
    	// Update the URIConverter
        // Map the logical name (filename) to the physical name (path+filename)
    	URI key = file.getName().equals(FOLDER_XML) ? URI.createFileURI(file.getAbsolutePath()) : URI.createFileURI(file.getName());
    	URI value = URI.createFileURI(file.getAbsolutePath());
        resourceSet.getURIConverter().getURIMap().put(key, value);

    	// Create a new resource for selected file and add object to persist
        XMLResource resource = (XMLResource) resourceSet.createResource(key);
        // Use UTF-8 and don't start with an XML declaration
        resource.getDefaultSaveOptions().put(XMLResource.OPTION_ENCODING, "UTF-8"); //$NON-NLS-1$
        resource.getDefaultSaveOptions().put(XMLResource.OPTION_DECLARE_XML,Boolean.FALSE);
        // Make the produced XML easy to read
        resource.getDefaultSaveOptions().put(XMLResource.OPTION_FORMATTED, Boolean.TRUE);
        resource.getDefaultSaveOptions().put(XMLResource.OPTION_LINE_WIDTH, new Integer(5));
        // Don't use encoded attribute. Needed to have proper references inside Diagrams
        resource.getDefaultSaveOptions().put(XMLResource.OPTION_USE_ENCODED_ATTRIBUTE_STYLE,Boolean.FALSE);
        // Use cache
        resource.getDefaultSaveOptions().put(XMLResource.OPTION_CONFIGURATION_CACHE,Boolean.TRUE);
        // Add the object to the resource
        resource.getContents().add(object);
    }
    
    /**
     * Extract and save images used inside a model
     * 
     * @param fModel
     * @param folder
     * @throws IOException
     */
    private void saveImages(IArchimateModel fModel, File folder) throws IOException {
        List<String> added = new ArrayList<String>();
        IArchiveManager archiveManager = IArchiveManager.FACTORY.createArchiveManager(fModel);
        byte[] bytes;
        
        for(Iterator<EObject> iter = fModel.eAllContents(); iter.hasNext();) {
            EObject eObject = iter.next();
            if(eObject instanceof IDiagramModelImageProvider) {
                IDiagramModelImageProvider imageProvider = (IDiagramModelImageProvider)eObject;
                String imagePath = imageProvider.getImagePath();
                if(imagePath != null && !added.contains(imagePath)) {
                	bytes = archiveManager.getBytesFromEntry(imagePath);
	                Files.write(Paths.get(folder.getAbsolutePath()+File.separator+imagePath), bytes, StandardOpenOption.CREATE);
	                added.add(imagePath);
                }
            }
        }
    }
    
    /**
     * Ask user to select a folder. Check if it is empty and, if not, ask confirmation.
     */
    private File askSaveFolder() throws IOException {
        DirectoryDialog dialog = new DirectoryDialog(Display.getCurrent().getActiveShell());
        dialog.setText(Messages.MyExporter_0);
        dialog.setMessage(Messages.MyExporter_3);
        String path = dialog.open();
        
        if(path == null) {
            return null;
        }
        
        File folder = new File(path);
        
        if(folder.exists()) {
            String[] children = folder.list();
            if(children != null && children.length > 0) {
                boolean result = MessageDialog.openQuestion(Display.getCurrent().getActiveShell(),
                		Messages.MyExporter_0,
                        NLS.bind(Messages.MyExporter_4, folder));
                if(!result) {
                    return null;
                }
            }
        } else {
        	folder.mkdirs();
        }
        
        return folder;
    }
}
