/*******************************************************************************
 * Copyright (c) 2007, 2010 Intel Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Intel Corporation - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.core.settings.model;

import java.util.Map;

import org.eclipse.cdt.core.cdtvariables.ICdtVariablesContributor;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.extension.CConfigurationData;
import org.eclipse.cdt.core.settings.model.extension.CConfigurationDataProvider;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.QualifiedName;

/**
 * This is the element representing configuration and thus this is the root element
 * for configuration-specific settings
 * 
 * @noextend This interface is not intended to be extended by clients.
 * @noimplement This interface is not intended to be implemented by clients.
 */
public interface ICConfigurationDescription extends ICSettingContainer, ICSettingObject, ICSettingsStorage {
	/**
	 * Returns whether or not this is an active configuration.
	 * Active configuration is the one that is built by default.
	 * This configuration is returned by the {@link ICProjectDescription#getActiveConfiguration()}
	 * call
	 *
	 * @return boolean
	 */
	boolean isActive();

	/**
	 * Returns the human-readable configuration description.
	 */
	String getDescription();

	/**
	 * Sets the configuration description
	 *
	 * @param des
	 * @throws WriteAccessException when the configuration description is read-only
	 * see {@link CoreModel#getProjectDescription(org.eclipse.core.resources.IProject, boolean)}
	 */
	void setDescription(String des) throws WriteAccessException;

	/**
	 * Returns the project description this configuration belongs to
	 */
	ICProjectDescription getProjectDescription();

	/**
	 * Returns the "root" folder description
	 * The root folder description is the default one used for the project root folder
	 * The root folder description can not be null
	 */
	ICFolderDescription getRootFolderDescription();

	/**
	 * Returns the complete set of folder descriptions defined for this configuration
	 * The folder description is the settings holder for the specified folder
	 * @see ICFolderDescription
	 */
	ICFolderDescription[] getFolderDescriptions();

	/**
	 * Returns the complete set of file descriptions defined for this configuration
	 * The file description is the settings holder for the specified file
	 * @see ICFileDescription
	 */
	ICFileDescription[] getFileDescriptions();

	/**
	 * Returns the complete set of file and folder descriptions (resource descriptions) defined
	 * for this configuration
	 * The resource description is the settings holder for the specified resource
	 * @see ICResourceDescription
	 * @see ICFileDescription
	 * @see ICFolderDescription
	 */
	ICResourceDescription[] getResourceDescriptions();

	/**
	 * Returns the resource description for the given path
	 *
	 * @param path - project-relative workspace resource path
	 * @param exactPath - when true the resource description of the given path is searched and if
	 * 		not found null is returned,
	 * if false, the resource description applicable for the given path is returned,
	 * i.e. if the configuration contains resource descriptions of the following paths "" and "a/"
	 * getResourceDescription(new Path("a/b"), true) returns null
	 * getResourceDescription(new Path("a/b"), false) returns the "a" folder description
	 * @return {@link ICResourceDescription} that is either a {@link ICFolderDescription} or
	 * 		an {@link ICFileDescription}
	 */
	ICResourceDescription getResourceDescription(IPath path, boolean exactPath);

	/**
	 * Removes the given resource description from the configuration
	 *
	 * @param des
	 * @throws CoreException
	 * @throws WriteAccessException when the configuration description is read-only
	 * see {@link CoreModel#getProjectDescription(org.eclipse.core.resources.IProject, boolean)}
	 */
	void removeResourceDescription(ICResourceDescription des) throws CoreException, WriteAccessException;

	/**
	 * Creates a new file description for the specified path
	 * @param path project-relative file workspace path
	 * @param base resource description from which settings will be coppied/inheritted
	 * @throws CoreException
	 * @throws WriteAccessException when the configuration description is read-only
	 * see {@link CoreModel#getProjectDescription(org.eclipse.core.resources.IProject, boolean)}
	 */
	ICFileDescription createFileDescription(IPath path, ICResourceDescription base) throws CoreException, WriteAccessException;

	/**
	 * Creates a new folder description for the specified path
	 * @param path project-relative folder workspace path
	 * @param base resource description from which settings will be coppied/inheritted
	 * @throws CoreException
	 * @throws WriteAccessException when the configuration description is read-only
	 * see {@link CoreModel#getProjectDescription(org.eclipse.core.resources.IProject, boolean)}
	 */
	ICFolderDescription createFolderDescription(IPath path, ICFolderDescription base) throws CoreException, WriteAccessException;

	/**
	 * Returns the ID of the build system used with this configuration
	 * i.e. the id of extension contributing to the org.eclipse.cdt.core.CConfigurationDataProvider
	 * extension point used with this configuration
	 *
	 * @return String
	 */
	String getBuildSystemId();

	/**
	 * This method should be used by the build system only for getting
	 * the build-system contributed CConfigurationData
	 * @see CConfigurationDataProvider and the org.eclipse.cdt.core.CConfigurationDataProvider
	 * extension point
	 */
	CConfigurationData getConfigurationData();

	/**
	 * Sets this configuration as active
	 * this call is equivalent to
	 * {@link ICProjectDescription#setActiveConfiguration(ICConfigurationDescription)}
	 * Active configuration is the one that is built by default.
	 * This configuration is returned by the {@link ICProjectDescription#getActiveConfiguration()}
	 * call
	 *
	 * @throws WriteAccessException when the configuration description is read-only,
	 * see {@link CoreModel#getProjectDescription(org.eclipse.core.resources.IProject, boolean)}
	 */
	void setActive() throws WriteAccessException;

	/**
	 * This method should be used by the build system only for updating
	 * the build-system contributed CConfigurationData
	 *
	 * @throws WriteAccessException when the configuration description is read-only,
	 * see {@link CoreModel#getProjectDescription(org.eclipse.core.resources.IProject, boolean)}
	 *
	 * @see CConfigurationDataProvider and the extension point
	 * 		org.eclipse.cdt.core.CConfigurationDataProvider
	 */
	void setConfigurationData(String buildSystemId, CConfigurationData data) throws WriteAccessException;

	/**
	 * Returns whether or not the configuration description was modified
	 */
	boolean isModified();

	/**
	 * Returns the target platform settings for this configuration
	 * @see ICTargetPlatformSetting
	 */
	ICTargetPlatformSetting getTargetPlatformSetting();

	/**
	 * Returns the source entries for this configuration
	 * @see ICSourceEntry
	 */
	ICSourceEntry[] getSourceEntries();

	ICSourceEntry[] getResolvedSourceEntries();

	/**
	 * Sets the source entries for this configuration
	 *
	 * @param entries
	 *
	 * @throws WriteAccessException when the configuration description is read-only,
	 * see {@link CoreModel#getProjectDescription(org.eclipse.core.resources.IProject, boolean)}
	 */
	void setSourceEntries(ICSourceEntry[] entries) throws CoreException, WriteAccessException;

	/**
	 * Returns a Map of configurations referenced by this configuration. Settings exported
	 * by a project configuration are automatically picked up by any referencing configurations.
	 * <p>
	 * This Map is keyed by project name with value equal to the referenced configuration's ID, or
	 * the empty string. The empty string is a special configuration value which indicates
	 * the reference tracks the Active configuration in the referenced Project.
	 * <p>
	 * If the current configuration does not reference any other configurations,
	 * an empty map is returned.
	 * <p>
	 * This method will fail to return all of a configurations references if there is more than
	 * one reference to a different configurations in the same project. Such a situation can only
	 * arise by mixing use of {@link #getReferenceInfo()} and {@link #setReferenceInfo(Map)} with
	 * {@link #getReferenceEntries()} and {@link #setReferenceEntries(ICReferenceEntry[])}.
	 * When multiple references like this are required, {@link #getReferenceEntries()} and
	 * {@link #setReferenceEntries(ICReferenceEntry[])} should be used.
	 *
	 * @return Map<String,String> of referenced Project -> Configuration ID
	 * @see {@link #setReferenceInfo(Map)} <br/>
	 * {@link #getExternalSettings()}<br/>
	 * {@link #createExternalSetting(String[], String[], String[], ICSettingEntry[])}
	 * {@link #getReferenceEntries()}
	 * @deprecated as this may not return all of a configurations references. Use
	 * {@link #getReferenceEntries()} instead.
	 */
	@Deprecated
	Map<String, String> getReferenceInfo();

	/**
	 * Sets the reference information for this configuration. This configuration
	 * will pick up settings exported by referenced configurations.
	 * <p>
	 * This reference information is a map from project name to configuration ID
	 * within the referenced project.
	 * The empty string is a special configuration value which indicates the reference
	 * tracks the Active configuration in the referenced Project.
	 * <p>
	 * This can only set references to one configuration in a project. If more are
	 * needed {@link #setReferenceEntries(ICReferenceEntry[])} and
	 * {@link #getReferenceEntries()} should be used instead.
	 *
	 * @param refs Map of project name -> configuration ID of referenced configurations
	 * @throws WriteAccessException when the configuration description is read-only
	 * @see {@link CoreModel#getProjectDescription(org.eclipse.core.resources.IProject, boolean)}
 	 * @see {@link #getReferenceInfo()} <br/>
	 * {@link #getExternalSettings()}<br/>
	 * {@link #createExternalSetting(String[], String[], String[], ICSettingEntry[])}
	 * {@link #setReferenceEntries(ICReferenceEntry[])}
	 * @deprecated as this can only set references to one configuration in a project. Use
	 * {@link #setReferenceEntries(ICReferenceEntry[])} instead.
	 */
	@Deprecated
	void setReferenceInfo(Map<String, String> refs) throws WriteAccessException;

	/**
	 * Returns an array of references to configurations for this configuration.
	 * Settings exported by a project configuration are automatically picked up by any referencing
	 * configurations.
	 * <p>
	 * This reference information is an array of reference objects storing a unique identifier
	 * for the referent configuration and properties of the reference.
	 * <p>
	 * If the current configuration does not reference any other configurations,
	 * an empty array is returned.
	 *
	 * @since 5.3
	 * @return Array of references to configurations; never null.
	 * @see {@link #setReferenceEntries(ICReferenceEntry[])}
	 * @see {@link #getReferenceInfo()}
	 */
	ICReferenceEntry[] getReferenceEntries();

	/**
	 * Sets the configuration references for this configuration. This configuration
	 * will pick up settings exported by the referenced configurations.
	 * <p>
	 * This reference information is an array of reference objects storing a unique identifier
	 * for the referent configuration and properties of the reference.
	 *
	 * @since 5.3
	 * @param refs An array of configuration references
	 * @throws WriteAccessException when the configuration description is read-only
	 * @see {@link #getReferenceEntries()}
	 * @see {@link #setReferenceInfo(Map)}
	 */
	void setReferenceEntries(ICReferenceEntry[] entries) throws CoreException, WriteAccessException;

	/**
	 * Returns an array of settings exported by this configuration
	 * in case some configurations refer (depend on) this configuration
	 * exported settings of this configuration get applied to those configurations
	 * @see ICExternalSetting
	 * @see #getReferenceInfo()
	 * @see #setReferenceInfo(Map)
	 */
	ICExternalSetting[] getExternalSettings();

	/**
	 * Creates/adds external setting to this configuration
	 * in case some configurations refer (depend on) this configuration
	 * exported settings of this configuration get applied to those configurations
	 * @see ICExternalSetting
	 * @see #getReferenceInfo()
	 * @see #setReferenceInfo(Map)
	 * @see #getExternalSettings()
	 *
	 * @param languageIDs
	 * @param contentTypeIds
	 * @param extensions
	 * @param entries
	 * @throws WriteAccessException when the configuration description is read-only
	 * see {@link CoreModel#getProjectDescription(org.eclipse.core.resources.IProject, boolean)}
	 */
	ICExternalSetting createExternalSetting(String languageIDs[],
			String contentTypeIds[],
			String extensions[],
			ICSettingEntry entries[]) throws WriteAccessException;

	/**
	 * Removes external setting from this configuration
	 * in case some configurations refer (depend on) this configuration
	 * exported settings of this configuration get applied to those configurations
	 * @see ICExternalSetting
	 * @see #getReferenceInfo()
	 * @see #setReferenceInfo(Map)
	 * @see #getExternalSettings()
	 *
	 * @param setting
	 * @throws WriteAccessException when the configuration description is read-only
	 * see {@link CoreModel#getProjectDescription(org.eclipse.core.resources.IProject, boolean)}
	 */
	void removeExternalSetting(ICExternalSetting setting) throws WriteAccessException;

	/**
	 * Removes all external settings from this configuration
	 * in case some configurations refer (depend on) this configuration
	 * exported settings of this configuration get applied to those configurations
	 * @see ICExternalSetting
	 * @see #getReferenceInfo()
	 * @see #setReferenceInfo(Map)
	 * @see #getExternalSettings()
	 *
	 * @throws WriteAccessException when the configuration description is read-only
	 * see {@link CoreModel#getProjectDescription(org.eclipse.core.resources.IProject, boolean)}
	 */
	void removeExternalSettings() throws WriteAccessException;

	/**
	 * Returns the build setting for this configuration
	 * @see ICBuildSetting
	 */
	ICBuildSetting getBuildSetting();

	/**
	 * Returns the CDT variable contributor that represent information on the
	 * CDT variables (Build Macros) contributed/used with this configuration
	 *
	 * @see ICdtVariablesContributor
	 */
	ICdtVariablesContributor getBuildVariablesContributor();

	/**
	 * The get/setSettionsProperty methods allow to associate the session properties mechanism on
	 * the configuration level session properties are not persisted and are not restored on the next
	 * eclipse session the scope of configuration session properties is the current configuration
	 * description, i.e. modifications to the properties are not applied until
	 * the setProjectDescription call
	 */
	Object getSessionProperty(QualifiedName name);

	/**
	 * The get/setSettionsProperty methods allow to associate the session properties mechanism on
	 * the configuration level session properties are not persisted and are not restored on the next
	 * eclipse session the scope of configuration session properties is the current configuration
	 * description, i.e. modifications to the properties are not applied until
	 * the setProjectDescription call
	 *
	 * @param name
	 * @param value
	 */
	void setSessionProperty(QualifiedName name, Object value);

	/**
	 * Sets the name for this configuration
	 *
	 * @param name
	 * @throws WriteAccessException when the configuration description is read-only
	 * see {@link CoreModel#getProjectDescription(org.eclipse.core.resources.IProject, boolean)}
	 */
	void setName(String name) throws WriteAccessException;

	ICConfigExtensionReference[] get(String extensionPointID);

	ICConfigExtensionReference create(String extensionPoint, String extension) throws CoreException;

	void remove(ICConfigExtensionReference ext) throws CoreException;

	void remove(String extensionPoint) throws CoreException;

	boolean isPreferenceConfiguration();

	/**
	 * Convenience method to return a language setting for the file
	 * with the specified project-relative path
	 *
	 * @param path - file project relative path
	 * @return ICLanguageSetting or null if not found
	 */
	ICLanguageSetting getLanguageSettingForFile(IPath path, boolean ignoreExludeStatus);

	/**
	 * Sets the external setting providers to be used for the configuration
	 *
	 * @param ids the ids of externalSettinsProvider extensions
	 */
	void setExternalSettingsProviderIds(String ids[]);

	/**
	 * Returns the ids of external setting providers used for the configuration
	 *
	 * @return the ids of externalSettinsProvider extensions
	 */
	String[] getExternalSettingsProviderIds();

	/**
	 * Tells the configuration to update the given providers
	 * In case the specified ids contain provider ids not associated with the configuration,
	 * those ids will be ignored and will NOT be added to the configuration settings
	 *
	 * @param ids the ids of externalSettinsProvider extensions
	 *
	 * @see ICProjectDescriptionManager#updateExternalSettingsProviders(String[], org.eclipse.core.runtime.IProgressMonitor)
	 */
	void updateExternalSettingsProviders(String[] ids) throws WriteAccessException;

	CConfigurationStatus getConfigurationStatus();
}
