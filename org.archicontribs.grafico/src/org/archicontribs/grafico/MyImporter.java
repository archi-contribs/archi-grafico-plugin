/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package org.archicontribs.grafico;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMLResourceFactoryImpl;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.model.IModelImporter;
import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelReference;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IIdentifier;
import com.archimatetool.model.IArchimateRelationship;



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
    Map<String, IIdentifier> idLookup  = new HashMap<String, IIdentifier>();
    
	ResourceSet resourceSet;
	
    @Override
    public void doImport() throws IOException {
    	File folder = askOpenFolder();
    	
    	if(folder == null) {
            return;
        }
    	
    	// Define source folders for model and images
    	File modelFolder = new File(folder, "model"); //$NON-NLS-1$
    	File imagesFolder = new File(folder, "images"); //$NON-NLS-1$
    	
    	if (!modelFolder.isDirectory() | !imagesFolder.isDirectory()) {
    		return;
    	}
    	
    	// Create ResourceSet
    	resourceSet = new ResourceSetImpl();
    	resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMLResourceFactoryImpl()); //$NON-NLS-1$
    	
        // Load the Model from files (it will contain unresolved proxies)
    	IArchimateModel model = (IArchimateModel) loadModel(modelFolder);
    	// Remove model from its resource (needed to save it back to a .archimate file)
    	resourceSet.getResource(URI.createFileURI((new File(modelFolder, "folder.xml")).getAbsolutePath()), true).getContents().remove(model);
    	
    	// Resolve proxies
    	resolveProxies(model);
    	
    	// Load images in a dummy .archimate file and assign it to model
    	model.setFile(createDummyArchimateFile(imagesFolder));
    	// Open the Model in the Editor
        IEditorModelManager.INSTANCE.openModel(model);
        // Clean up file reference
        model.setFile(null);
    }
    
    /**
     * Create a dummy .archimate file with empty model and all images
     * located inside folder passed as argument.
     * 
     * @param folder
     * @return
     * @throws IOException
     */
    private File createDummyArchimateFile(File folder) throws IOException {
    	byte[] buffer = new byte[1024];
    	File tmpFile = File.createTempFile("archi-", null); //$NON-NLS-1$
    	FileOutputStream fos = new FileOutputStream(tmpFile);
    	ZipOutputStream zos = new ZipOutputStream(fos);
    	
    	// Add all images files
    	for (File fileOrFolder: folder.listFiles()) {
    		ZipEntry ze = new ZipEntry("images/"+fileOrFolder.getName()); //$NON-NLS-1$
    		zos.putNextEntry(ze);
    		FileInputStream in = new FileInputStream(fileOrFolder);
 
    		int len;
    		while ((len = in.read(buffer)) > 0) {
    			zos.write(buffer, 0, len);
    		}
 
    		in.close();
    		zos.closeEntry();
    	}
    	
    	// Add a dummy model.xml
    	ZipEntry ze = new ZipEntry("model.xml"); //$NON-NLS-1$
		zos.putNextEntry(ze);
		zos.closeEntry();
    	
    	zos.close();
    	return tmpFile;
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
            	relation.setSource((IArchimateConcept) resolve(relation.getSource()));
            	relation.setTarget((IArchimateConcept) resolve(relation.getTarget())); 
            } else if(eObject instanceof IDiagramModelArchimateObject) {
            	// Resolve proxies for Elements
            	IDiagramModelArchimateObject element = (IDiagramModelArchimateObject) eObject;
				element.setArchimateElement((IArchimateElement) resolve(element.getArchimateElement()));
				// Update cross-references
				element.getArchimateElement().getReferencingDiagramObjects().add(element);
            } else if(eObject instanceof IDiagramModelArchimateConnection) {
            	// Resolve proxies for Connections
            	IDiagramModelArchimateConnection archiConnection = (IDiagramModelArchimateConnection) eObject;
				archiConnection.setArchimateRelationship((IArchimateRelationship) resolve(archiConnection.getArchimateRelationship()));
				//	Update cross-reference
				archiConnection.getArchimateRelationship().getReferencingDiagramConnections().add(archiConnection);
            } else if (eObject instanceof IDiagramModelReference) {
            	// Resolve proxies for Model References
            	IDiagramModelReference element = (IDiagramModelReference) eObject;
				element.setReferencedModel((IDiagramModel) resolve(element.getReferencedModel()));
            }
        }
    }

    /**
     * Check if 'object' is a proxy. if yes, replace it with real object from mapping table.
     *  
     * @param object
     * @return
     */
	private EObject resolve(IIdentifier object) {
		if (object != null & object.eIsProxy()) {
			IIdentifier newObject = idLookup.get(((InternalEObject) object).eProxyURI().fragment());
			return newObject == null ? object : newObject;
		} else {
			return object;
		}
	}
    
	private IArchimateModel loadModel(File folder) {
		IArchimateModel model = (IArchimateModel) loadElement(new File(folder, "folder.xml")); //$NON-NLS-1$
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
				if ((tmpFolder = loadFolder(new File(folder, folderList.get(i).toString()))) != null)  //$NON-NLS-1$
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
    	if (!folder.isDirectory() | !(new File(folder, "folder.xml")).isFile()) { //$NON-NLS-1$
    		return null;
    	}
    	
    	// Load folder object itself
    	IFolder currentFolder = (IFolder) loadElement(new File(folder, "folder.xml")); //$NON-NLS-1$
    	
    	// Load each elements (except folder.xml) and add them to folder
    	for (File fileOrFolder: folder.listFiles()) {
    		if(!fileOrFolder.getName().equals("folder.xml")) { //$NON-NLS-1$
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
        dialog.setText(Messages.MyExporter_0);
        dialog.setMessage(Messages.MyImporter_0);
        String path = dialog.open();
        return (path == null)? null : new File(path);
    }
}
