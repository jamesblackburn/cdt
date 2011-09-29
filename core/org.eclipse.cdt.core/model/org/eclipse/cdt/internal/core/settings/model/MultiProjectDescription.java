/*******************************************************************************
 * Copyright (c) 2010 Broadcom Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * James Blackburn (Broadcom Corp.) - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.core.settings.model;

import java.util.ArrayList;
import java.util.Arrays;

import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICMultiProjectDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.settings.model.ICSettingContainer;
import org.eclipse.cdt.core.settings.model.ICSettingObject;
import org.eclipse.cdt.core.settings.model.ICStorageElement;
import org.eclipse.cdt.core.settings.model.WriteAccessException;
import org.eclipse.cdt.core.settings.model.extension.CConfigurationData;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.QualifiedName;

/**
 * Concrete implementation of MultiProjectDescription allowing easy access
 * to multiple project descriptions, and their configurations, simultaneously.
 * @noextend This class is not intended to be subclassed by clients.
 * @since 5.3
 */
public class MultiProjectDescription implements ICMultiProjectDescription {

	private final ICProjectDescription[] prjDescs;

	public MultiProjectDescription(ICProjectDescription[] prjDescs)	{
		this.prjDescs = prjDescs;
	}

	public ICProjectDescription[] getItems() {
		return prjDescs;
	}

	public ICConfigurationDescription[] getConfigurations() {
		ArrayList<ICConfigurationDescription> cfgs = new ArrayList<ICConfigurationDescription>();
		for (ICProjectDescription desc : prjDescs)
			cfgs.addAll(Arrays.asList(desc.getConfigurations()));
		return cfgs.toArray(new ICConfigurationDescription[cfgs.size()]);
	}

	/**
	 * Return the ICConfigurationDescription for the given project with the passed in ID
	 * @param project
	 * @param id
	 * @return ICConfigurationDescription or null if not found
	 */
	public ICConfigurationDescription getConfigurationById(IProject project, String id) {
		for (ICProjectDescription prjDesc : prjDescs)
			if (project.equals(prjDesc.getProject()))
				return prjDesc.getConfigurationById(id);
		return null;
	}

	public void setProjectDescriptions() throws CoreException {
		for (ICProjectDescription prjDesc : prjDescs)
			CoreModel.getDefault().setProjectDescription(prjDesc.getProject(), prjDesc);
	}

	/*
	 * The methods below are unimplemented.
	 */

	public ICConfigurationDescription getActiveConfiguration() {
		throw new UnsupportedOperationException();
	}

	public void setActiveConfiguration(ICConfigurationDescription cfg) throws WriteAccessException {
		throw new UnsupportedOperationException();
	}

	public ICConfigurationDescription createConfiguration(String id, String name,
			ICConfigurationDescription base) throws CoreException, WriteAccessException {
		throw new UnsupportedOperationException();
	}

	public ICConfigurationDescription createConfiguration(String buildSystemId, CConfigurationData data)
			throws CoreException, WriteAccessException {
		throw new UnsupportedOperationException();
	}

	public ICConfigurationDescription getConfigurationByName(String name) {
		throw new UnsupportedOperationException();
	}

	public ICConfigurationDescription getConfigurationById(String id) {
		throw new UnsupportedOperationException();
	}

	public void removeConfiguration(String name) throws WriteAccessException {
		throw new UnsupportedOperationException();
	}

	public void removeConfiguration(ICConfigurationDescription cfg) throws WriteAccessException {
		throw new UnsupportedOperationException();
	}

	public IProject getProject() {
		throw new UnsupportedOperationException();
	}

	public boolean isModified() {
		throw new UnsupportedOperationException();
	}

	public Object getSessionProperty(QualifiedName name) {
		throw new UnsupportedOperationException();
	}

	public void setSessionProperty(QualifiedName name, Object value) {
		throw new UnsupportedOperationException();
	}

	public ICConfigurationDescription getDefaultSettingConfiguration() {
		throw new UnsupportedOperationException();
	}

	public void setDefaultSettingConfiguration(ICConfigurationDescription cfg) {
		throw new UnsupportedOperationException();
	}

	public boolean isCdtProjectCreating() {
		throw new UnsupportedOperationException();
	}

	public void setCdtProjectCreated() {
		throw new UnsupportedOperationException();
	}

	public ICSettingObject[] getChildSettings() {
		throw new UnsupportedOperationException();
	}

	public String getId() {
		throw new UnsupportedOperationException();
	}

	public String getName() {
		throw new UnsupportedOperationException();
	}

	public int getType() {
		throw new UnsupportedOperationException();
	}

	public boolean isValid() {
		throw new UnsupportedOperationException();
	}

	public ICConfigurationDescription getConfiguration() {
		throw new UnsupportedOperationException();
	}

	public ICSettingContainer getParent() {
		throw new UnsupportedOperationException();
	}

	public boolean isReadOnly() {
		throw new UnsupportedOperationException();
	}

	public ICStorageElement getStorage(String id, boolean create) throws CoreException {
		throw new UnsupportedOperationException();
	}

	public void removeStorage(String id) throws CoreException {
		throw new UnsupportedOperationException();
	}

	public ICStorageElement importStorage(String id, ICStorageElement el)
			throws UnsupportedOperationException, CoreException {
		throw new UnsupportedOperationException();
	}

	public void setReadOnly(boolean readOnly, boolean keepModify) {
		throw new UnsupportedOperationException();
	}

	public int getConfigurationRelations() {
		throw new UnsupportedOperationException();
	}

	public void setConfigurationRelations(int status) {
		throw new UnsupportedOperationException();
	}

	public void useDefaultConfigurationRelations() {
		throw new UnsupportedOperationException();
	}

	public boolean isDefaultConfigurationRelations() {
		throw new UnsupportedOperationException();
	}

}
