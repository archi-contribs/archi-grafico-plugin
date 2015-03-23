/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package org.archicontribs.grafico;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMLResourceFactoryImpl;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;

import com.archimatetool.editor.model.IArchiveManager;
import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.model.IModelExporter;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.model.IArchimateModel;
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
	ResourceSet resourceSet;
    
    public MyExporter() {
    }

    @Override
    public void export(IArchimateModel model) throws IOException {
    	File folder = askSaveFolder();
    	
    	if(folder == null) {
            return;
        }
    	
    	// Create ResourceSet
    	resourceSet = new ResourceSetImpl();
    	resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMLResourceFactoryImpl());
    	
    	// Work on a copy
    	IArchimateModel copy = EcoreUtil.copy(model);
    	saveImages(model, folder);
    	File modelFolder = new File(folder, "model");
    	modelFolder.mkdirs();
    	save(copy, modelFolder);
    	
    	for (Resource resource: resourceSet.getResources()) {
            try {
                resource.save(null);
            }
            catch(Exception ex) {
                throw new IOException(ex);
            }
    	}
    }
    
    private void save(EObject object, File folder) throws IOException {
    	if (object instanceof IFolder | object instanceof IArchimateModel) {
    		if (!(object instanceof IArchimateModel)) {
        		// Create subfolder
        		folder = new File(folder, ((IIdentifier)object).getId());
        		folder.mkdirs();
        		// Save each children element
        		saveElements(((IFolder)object).getElements(), folder);
    		}
    		// Save each children folders
    		saveFolders(((IFolderContainer)object).getFolders(), folder);
    		saveResource(new File(folder, "folder.xml"), object);
    	} else {
    		saveResource(new File(folder, ((IIdentifier)object).getId()+".xml"), object);
    	}
    }

    private void saveFolders(EList<IFolder> eList, File folder) throws IOException {
    	List<IFolder> objects = new ArrayList<IFolder>();
    	objects.addAll(eList);
    	
		for (EObject object: objects) {
			//eList.remove(object);
			save(object, folder);
		}
    }
    
    private void saveElements(EList<EObject> eList, File folder) throws IOException {
    	List<EObject> objects = new ArrayList<EObject>();
    	objects.addAll(eList);
    	
		for (EObject object: objects) {
			//eList.remove(object);
			save(object, folder);
		}
    }
    
    /**
     * Save the model to Resource
     */
    private void saveResource(File file, EObject object) throws IOException {
    	// Create a new resource for selected file and add object to persist
        Resource resource = resourceSet.createResource(URI.createFileURI(file.getAbsolutePath()));
        resource.getContents().add(object);
    }
    
    private void saveImages(IArchimateModel fModel, File folder) throws IOException {
        /*
         * 1. save model in a temporary .archimate file
         * 2. check if this .archimate file is a ZIP (thus contains images) or an XML file
         * 3. if XML file, then return
         * 4. if ZIP, extract images to target folder
         */
    	
    	// Step 1
    	File old = fModel.getFile();
    	File tmpFile = File.createTempFile("archi-", null); //$NON-NLS-1$
    	fModel.setFile(tmpFile);
    	IEditorModelManager.INSTANCE.saveModel(fModel);
    	
    	// Step 2
    	boolean useArchiveFormat = IArchiveManager.FACTORY.isArchiveFile(tmpFile);
    	
    	// Step 3 & 4
    	if (useArchiveFormat) {
	    	ZipFile zipFile = new ZipFile(tmpFile);
	    	for(Enumeration<? extends ZipEntry> enm = zipFile.entries(); enm.hasMoreElements();) {
	            ZipEntry zipEntry = enm.nextElement();
	            String entryName = zipEntry.getName();
	            if(entryName.startsWith("images/")) { //$NON-NLS-1$
	            	File newFile = new File(folder + File.separator + entryName.replace("/", File.separator));
	            	//create all non exists folders
	                new File(newFile.getParent()).mkdirs();
	                InputStream is = zipFile.getInputStream(zipEntry);
					FileOutputStream fos = new FileOutputStream(newFile);
					byte[] bytes = new byte[1024];
					int length;
					while ((length = is.read(bytes)) >= 0) {
						fos.write(bytes, 0, length);
					}
					is.close();
					fos.close();
	            }
	        }
	        zipFile.close();
    	}
    	
    	fModel.setFile(old);
    }
    
    private File askSaveFolder() throws IOException {
        DirectoryDialog dialog = new DirectoryDialog(Display.getCurrent().getActiveShell());
        dialog.setText(Messages.MyExporter_0);
        dialog.setMessage("Choose a folder in which to export the model.");
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
                        NLS.bind("''{0}'' is not empty. Are you sure you want to overwrite it?", folder));
                if(!result) {
                    return null;
                }
                File imagesFolder = new File(folder, "images");
                File modelFolder = new File(folder, "model");
                if (imagesFolder.exists()) {
                	FileUtils.deleteFolder(imagesFolder);
                }
                if (modelFolder.exists()) {
                	FileUtils.deleteFolder(modelFolder);
                }
            }
        }
        folder.mkdirs();
        return folder;
    }
}
