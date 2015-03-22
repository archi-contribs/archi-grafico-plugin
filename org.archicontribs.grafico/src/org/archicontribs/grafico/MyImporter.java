/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package org.archicontribs.grafico;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMLResourceFactoryImpl;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.model.IModelImporter;
import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelReference;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IFolderContainer;
import com.archimatetool.model.IIdentifier;
import com.archimatetool.model.IRelationship;



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
    
	ResourceSet resourceSet;
	
    @Override
    public void doImport() throws IOException {
    	File folder = askOpenFolder();
    	
    	if(folder == null) {
            return;
        }
    	
    	File modelFolder = new File(folder, "model");
    	File imagesFolder = new File(folder, "images");
    	
    	// Create ResourceSet
    	resourceSet = new ResourceSetImpl();
    	resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMLResourceFactoryImpl());
    	
        // Create an ID -> IIdentifier mapping table as a cache to resolve proxies
        idLookup = new HashMap<String, IIdentifier>();
    	
        // Load the Model from files (it will contain unresolved proxies)
    	IArchimateModel model = (IArchimateModel) loadFolder(modelFolder);
    	// Remove model from its resource (needed to save in back to .archimate)
    	Resource resource = resourceSet.getResource(URI.createFileURI((new File(modelFolder, "folder.xml")).getAbsolutePath()), true);
    	resource.getContents().remove(model);
    	
    	// Resolve proxies
    	resolveRelations(model.getFolder(FolderType.RELATIONS));
    	resolveDiagramFolder(model.getFolder(FolderType.DIAGRAMS));
    	
    	// Load images in a dummy .archimate file and assign it to model
    	File dummy = createDummyArchimateFile(imagesFolder);
    	model.setFile(dummy);
    	// Open the Model in the Editor
        IEditorModelManager.INSTANCE.openModel(model);
        model.setFile(null);
    }
    
    private File createDummyArchimateFile(File folder) throws IOException {
    	byte[] buffer = new byte[1024];
    	File tmpFile = File.createTempFile("archi-", null); //$NON-NLS-1$
    	FileOutputStream fos = new FileOutputStream(tmpFile);
    	ZipOutputStream zos = new ZipOutputStream(fos);
    	
    	// Add all images files
    	for (File fileOrFolder: folder.listFiles()) {
    		ZipEntry ze = new ZipEntry("images/"+fileOrFolder.getName());
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
    	ZipEntry ze = new ZipEntry("model.xml");
		zos.putNextEntry(ze);
		zos.closeEntry();
    	
    	zos.close();
    	return tmpFile;
    }
    
    private void resolveRelations(IFolder folder) {
    	// Loop on elements
    	for (EObject object: folder.getElements()) {
    		IRelationship rel = (IRelationship) object;
    		rel.setSource((IArchimateElement) resolve(rel.getSource()));
    		rel.setTarget((IArchimateElement) resolve(rel.getTarget())); 
    	}
    	// Loop on subfolders
    	for (IFolder subfolder: folder.getFolders()) {
    		resolveRelations(subfolder);
    	}
    }
    
    private void resolveDiagramFolder(IFolder folder) {
    	// Loop on views
    	for (EObject object: folder.getElements()) {
    		resolveDiagram(object);
    	}
    	// Loop on subfolders
    	for (IFolder subfolder: folder.getFolders()) {
    		resolveDiagramFolder(subfolder);
    	}
    }
    
	private void resolveDiagram(EObject object) {
		if (!(object instanceof IDiagramModelContainer)) {
			return;
		}
		for (EObject child: ((IDiagramModelContainer) object).getChildren()) {
			if (child instanceof IDiagramModelArchimateObject) {
				// resolve Elements
				IDiagramModelArchimateObject element = (IDiagramModelArchimateObject) child;
				element.setArchimateElement((IArchimateElement) resolve(element.getArchimateElement()));
				// Update cross-references
				element.getArchimateElement().getReferencingDiagramObjects().add(element);
				// resolve Connections
				for (IDiagramModelConnection connection: element.getSourceConnections()) {
					if (connection instanceof IDiagramModelArchimateConnection) {
						// resolve Connections
						IDiagramModelArchimateConnection archiConnection = (IDiagramModelArchimateConnection) connection;
						archiConnection.setRelationship((IRelationship) resolve(archiConnection.getRelationship()));
						// Update cross-reference
						archiConnection.getRelationship().getReferencingDiagramConnections().add(archiConnection);
					}
				}
			} else if (child instanceof IDiagramModelReference) {
				// resolve Elements
				IDiagramModelReference element = (IDiagramModelReference) child;
				element.setReferencedModel((IDiagramModel) resolve(element.getReferencedModel()));
			}
			// Loop on Containers
			resolveDiagram(child);
		}
	}

	private EObject resolve(IIdentifier object) {
		if (object.eIsProxy()) {
			IIdentifier newObject = idLookup.get(((InternalEObject) object).eProxyURI().fragment());
			return newObject;
		} else {
			return object;
		}
	}
    
    private IFolderContainer loadFolder(File folder) {
    	// Load folder object itself
    	IFolderContainer currentFolder = (IFolderContainer) loadElement(new File(folder, "folder.xml"));
    	
    	// Load each elements (except folder.json) and add them to folder
    	for (File fileOrFolder: folder.listFiles()) {
    		if(!fileOrFolder.getName().equals("folder.xml")) {
    			if (fileOrFolder.isFile() & currentFolder instanceof IFolder) {
    				((IFolder) currentFolder).getElements().add(loadElement(fileOrFolder));
    			} else {
    				currentFolder.getFolders().add((IFolder) loadFolder(fileOrFolder));
    			}
    		}
    	}
    	
    	return currentFolder;
    }

    private EObject loadElement(File file) {
    	// Create a new resource for selected file and add object to persist
        Resource resource = resourceSet.getResource(URI.createFileURI(file.getAbsolutePath()), true);
        IIdentifier element = (IIdentifier) resource.getContents().get(0);
        idLookup.put(element.getId(), element);
        return element;
    }
    
    private File askOpenFolder() {
        DirectoryDialog dialog = new DirectoryDialog(Display.getCurrent().getActiveShell());
        dialog.setText(Messages.MyExporter_0);
        dialog.setMessage("Choose a folder from which to import the model.");
        String path = dialog.open();
        return (path == null)? null : new File(path);
    }
}
