/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package org.archicontribs.grafico;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMLResourceFactoryImpl;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;

import com.archimatetool.editor.model.IArchiveManager;
import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.model.IModelImporter;
import com.archimatetool.editor.preferences.Preferences;
import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelReference;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IIdentifier;



/**
 * GRAFICO Model Importer
 * GRAFICO (Git fRiendly Archi FIle COllection) is a way to persist an ArchiMate
 * model in a bunch of XML files (one file per ArchiMate element or view).
 * 
 * @author Jean-Baptiste Sarrodie
 * @author Quentin Varquet
 * @author Phillip Beauvoir
 */
public class MyImporter implements IModelImporter {
	// ID -> Object lookup table
    Map<String, IIdentifier> idLookup;
    MultiStatus resolveErrors;
    
	ResourceSet resourceSet;
	
    @Override
    public void doImport() throws IOException {
    	File folder = askOpenFolder();
    	
    	if(folder == null) {
            return;
        }
    	
    	// Define source folders for model and images
    	File modelFolder = new File(folder, MyExporter.MODEL_FOLDER);
    	File imagesFolder = new File(folder, MyExporter.IMAGES_FOLDER);
    	
    	if (!modelFolder.isDirectory()) {
    		return;
    	}
    	
    	// Create ResourceSet
    	resourceSet = new ResourceSetImpl();
    	resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMLResourceFactoryImpl()); //$NON-NLS-1$
    	
    	// Reset the ID -> Object lookup table
    	idLookup = new HashMap<String, IIdentifier>();
        // Load the Model from files (it will contain unresolved proxies)
    	IArchimateModel model = (IArchimateModel) loadModel(modelFolder);
    	// Remove model from its resource (needed to save it back to a .archimate file)
    	resourceSet.getResource(URI.createFileURI((new File(modelFolder, MyExporter.FOLDER_XML)).getAbsolutePath()), true).getContents().remove(model);
    	
    	// Resolve proxies
    	resolveErrors = null;
    	resolveProxies(model);
    	
    	if(imagesFolder.isDirectory()) {
    		loadImages(model, imagesFolder);
    	}
    	
    	// Open the Model in the Editor
        IEditorModelManager.INSTANCE.openModel(model);
        
        // Show warnings and errors (if any)
        if (resolveErrors != null)
	        org.eclipse.jface.dialogs.ErrorDialog.openError( 	
	        		Display.getCurrent().getActiveShell(), 	
	        		Messages.MyImporter_1,
	        		Messages.MyImporter_2,
	        		resolveErrors);
    }
    
    
    /**
     * Read images from images subfolder and load them into the model
     * 
     * @param fModel
     * @param folder
     * @throws IOException
     */
    private void loadImages(IArchimateModel fModel, File folder) throws IOException {
    	IArchiveManager archiveManager = IArchiveManager.FACTORY.createArchiveManager(fModel);
    	byte[] bytes;
    	
    	// Add all images files
    	for (File imageFile: folder.listFiles()) {
    		if (imageFile.isFile()) {
    		      bytes = Files.readAllBytes(imageFile.toPath());
    		      // /!\ This must match the prefix used in ArchiveManager.createArchiveImagePathname
    		      archiveManager.addByteContentEntry("images/" + imageFile.getName(), bytes);
    		}
    	}
    }
    
   
    /**
     * Look for all eObject, and resolve proxies on known classes
     * 
     * @param object
     */
    private void resolveProxies(EObject object) {
    	for(Iterator<EObject> iter = object.eAllContents(); iter.hasNext();) {
            EObject eObject = iter.next();
            
            if(eObject instanceof IArchimateRelationship) {
            	// Resolve proxies for Relations
            	IArchimateRelationship relation = (IArchimateRelationship) eObject;
            	relation.setSource((IArchimateConcept) resolve(relation.getSource(), relation));
            	relation.setTarget((IArchimateConcept) resolve(relation.getTarget(), relation)); 
            } else if(eObject instanceof IDiagramModelArchimateObject) {
            	// Resolve proxies for Elements
            	IDiagramModelArchimateObject element = (IDiagramModelArchimateObject) eObject;
				element.setArchimateElement((IArchimateElement) resolve(element.getArchimateElement(), element));
				// Update cross-references
				element.getArchimateElement().getReferencingDiagramObjects().add(element);
            } else if(eObject instanceof IDiagramModelArchimateConnection) {
            	// Resolve proxies for Connections
            	IDiagramModelArchimateConnection archiConnection = (IDiagramModelArchimateConnection) eObject;
				archiConnection.setArchimateRelationship((IArchimateRelationship) resolve(archiConnection.getArchimateRelationship(), archiConnection));
				//	Update cross-reference
				archiConnection.getArchimateRelationship().getReferencingDiagramConnections().add(archiConnection);
            } else if (eObject instanceof IDiagramModelReference) {
            	// Resolve proxies for Model References
            	IDiagramModelReference element = (IDiagramModelReference) eObject;
				element.setReferencedModel((IDiagramModel) resolve(element.getReferencedModel(), element));
            }
        }
    }

    /**
     * Check if 'object' is a proxy. if yes, replace it with real object from mapping table.
     *  
     * @param object
     * @return
     */
	private EObject resolve(IIdentifier object, IIdentifier parent) {
		if (object != null & object.eIsProxy()) {
			IIdentifier newObject = idLookup.get(((InternalEObject) object).eProxyURI().fragment());
			// Log errors if proxy has not been resolved
			if (newObject == null) {
				String message = String.format(Messages.MyImporter_3, ((InternalEObject) object).eProxyURI().fragment(), parent.getClass().getSimpleName(), parent.getId());
				System.out.println(message);
				// Create resolveError the first time	
				if (resolveErrors == null)
					resolveErrors = new MultiStatus("org.archicontribs.grafico", IStatus.ERROR, Messages.MyImporter_4, null); //$NON-NLS-1$
				// Add an error to the list
				resolveErrors.add(new Status(IStatus.ERROR, "org.archicontribs.grafico", message)); //$NON-NLS-1$
			}
			return newObject == null ? object : newObject;
		} else {
			return object;
		}
	}
    
	private IArchimateModel loadModel(File folder) {
		IArchimateModel model = (IArchimateModel) loadElement(new File(folder, MyExporter.FOLDER_XML));
		IFolder tmpFolder;
		
		if (model != null) {
			List<FolderType> folderList = new ArrayList<FolderType>();
			folderList.add(FolderType.STRATEGY);
			folderList.add(FolderType.BUSINESS);
			folderList.add(FolderType.APPLICATION);
			folderList.add(FolderType.TECHNOLOGY);
			folderList.add(FolderType.MOTIVATION);
			folderList.add(FolderType.IMPLEMENTATION_MIGRATION);
			folderList.add(FolderType.OTHER);
			folderList.add(FolderType.RELATIONS);
			folderList.add(FolderType.DIAGRAMS);
			
			// Loop based on FolderType enumeration
			for (int i = 0; i < folderList.size(); i++) {
				if ((tmpFolder = loadFolder(new File(folder, folderList.get(i).toString()))) != null)
					model.getFolders().add(tmpFolder);
			}
		}
		
		return model;
	}
	
	/**
	 * Load each XML file to recreate original object
	 * 
	 * @param folder
	 * @return
	 */
    private IFolder loadFolder(File folder) {
    	if (!folder.isDirectory() | !(new File(folder, MyExporter.FOLDER_XML)).isFile()) {
    		return null;
    	}
    	
    	// Load folder object itself
    	IFolder currentFolder = (IFolder) loadElement(new File(folder, MyExporter.FOLDER_XML));
    	
    	// Load each elements (except folder.xml) and add them to folder
    	for (File fileOrFolder: folder.listFiles()) {
    		if(!fileOrFolder.getName().equals(MyExporter.FOLDER_XML)) {
				if (fileOrFolder.isFile()) {
					currentFolder.getElements().add(loadElement(fileOrFolder));
				} else {
					currentFolder.getFolders().add(loadFolder(fileOrFolder));
				}
    		}
    	}
    	
    	return currentFolder;
    }

    /**
     * Create an eObject from an XML file. Basically load a resource.
     * 
     * @param file
     * @return
     */
    private EObject loadElement(File file) {
    	// Create a new resource for selected file and add object to persist
    	XMLResource resource = (XMLResource) resourceSet.getResource(URI.createFileURI(file.getAbsolutePath()), true);
    	resource.getDefaultLoadOptions().put(XMLResource.OPTION_ENCODING, "UTF-8"); //$NON-NLS-1$
        IIdentifier element = (IIdentifier) resource.getContents().get(0);
        
        // Update an ID -> Object mapping table (used as a cache to resolve proxies)
        idLookup.put(element.getId(), element);
        
        return element;
    }
    
    /**
     * Ask user to select a folder.
     */
    private File askOpenFolder() {
        DirectoryDialog dialog = new DirectoryDialog(Display.getCurrent().getActiveShell());
        // Set default path from preference
        dialog.setFilterPath(Preferences.STORE.getString(MyExporter.PREF_LAST_FOLDER));
        dialog.setText(Messages.MyExporter_0);
        dialog.setMessage(Messages.MyImporter_0);
        String path = dialog.open();
        
        if(path == null) {
            return null;
        }
        
        // Save choosen path in preference
        Preferences.STORE.setValue(MyExporter.PREF_LAST_FOLDER, path);
        
        return new File(path);
    }
}
