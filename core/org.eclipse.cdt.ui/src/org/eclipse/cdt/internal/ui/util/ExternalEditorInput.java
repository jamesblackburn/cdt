package org.eclipse.cdt.internal.ui.util;

/*
 * (c) Copyright QNX Software Systems Ltd. 2002.
 * All Rights Reserved.
 */

import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.cdt.core.model.ITranslationUnit;
import org.eclipse.cdt.internal.ui.editor.ITranslationUnitEditorInput;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;

import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.IPersistableElement;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.ILocationProvider;


/**
 * An EditorInput for a JarEntryFile.
 */
public class ExternalEditorInput implements ITranslationUnitEditorInput {
           
	private IStorage externalFile;
	private ITranslationUnit unit;

	/*
	*/
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof ExternalEditorInput))
			return false;
		ExternalEditorInput other = (ExternalEditorInput)obj;
		return externalFile.equals(other.externalFile);
	}

	/*
	* @see IEditorInput#exists()
	*/
	public boolean exists() {
		// External file ca not be deleted
		return true;
	}

	/*
	* @see IAdaptable#getAdapter(Class)
	*/
	public Object getAdapter(Class adapter) {
		if (ILocationProvider.class.equals(adapter))
			return this;
		return Platform.getAdapterManager().getAdapter(this, adapter);
	}

	/*
	* @see IEditorInput#getContentType()
	*/
	public String getContentType() {
		return externalFile.getFullPath().getFileExtension();
	}

	/*
	* @see IEditorInput#getFullPath()
	*/
	public String getFullPath() {
		return externalFile.getFullPath().toString();
	}

	/*
	* @see IEditorInput#getImageDescriptor()
	*/
	public ImageDescriptor getImageDescriptor() {
		IEditorRegistry registry= PlatformUI.getWorkbench().getEditorRegistry();
		return registry.getImageDescriptor(externalFile.getFullPath().getFileExtension());
	}

	/*
	* @see IEditorInput#getName()
	*/
	public String getName() {
		return externalFile.getName();
	}

	/*
	* @see IEditorInput#getPersistable()
	*/
	public IPersistableElement getPersistable() {
		return null;
	}

	/*
	* see IStorageEditorInput#getStorage()
	*/
	public IStorage getStorage() {
		return externalFile;
	}

	/*
	* @see IEditorInput#getToolTipText()
	*/
	public String getToolTipText() {
		return externalFile.getFullPath().toString();
	}

	public ExternalEditorInput(IStorage exFile) {
		externalFile = exFile;
	}

	public ExternalEditorInput(ITranslationUnit unit, IStorage exFile) {
		this(exFile);
		this.unit = unit;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.internal.ui.editor.ITranslationUnitEditorInput#getTranslationUnit()
	 */
	public ITranslationUnit getTranslationUnit() {
		return unit;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.editors.text.ILocationProvider#getPath(java.lang.Object)
	 */
	public IPath getPath(Object element) {
		return externalFile.getFullPath();
	}
}
