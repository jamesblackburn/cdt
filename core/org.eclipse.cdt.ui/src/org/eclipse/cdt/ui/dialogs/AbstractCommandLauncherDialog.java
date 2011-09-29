/*******************************************************************************
 * Copyright (c) 2009 Broadcom Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     James Blackburn (Broadcom Corp.) - initial API and implementation
 *******************************************************************************/

package org.eclipse.cdt.ui.dialogs;

import org.eclipse.core.resources.IProject;
import org.eclipse.swt.widgets.Shell;
import org.osgi.service.prefs.Preferences;

import org.eclipse.cdt.internal.ui.dialogs.StatusDialog;


/**
 * Provides "Advanced..." options for a contributed CommandLauncher
 *
 * These options are stored in some Preference context, and may be used
 * by the CommandLauncher at runtime to customise Process creation.
 *
 * Implementors should override {@link #createDialogArea(org.eclipse.swt.widgets.Composite)}
 * @since 5.3
 */
public abstract class AbstractCommandLauncherDialog extends StatusDialog {

	protected IProject project;
	protected String id;
	protected String processType;
	/** PrefStore where these preference keys should be persisted on OK */
	protected Preferences prefStore;

	public AbstractCommandLauncherDialog() {
		super((Shell)null);
	}

	public final void setPreferenceContext(IProject project, String id, String processType) {
		this.project = project;
		this.id = id;
		this.processType = processType;
	}

	/**
	 * Get the default value of a preference key as stored in the scoped preferences
	 * @param key
	 * @param defaultValue default value to return if key isn't found
	 * @return Default value of the key in the preference store or null
	 */
	protected String getInitialValue(String key, String defaultValue) {
		String value = prefStore.get(key, null);
		if (value != null)
			return value;
		return defaultValue;
	}

	/**
	 * The preferences to be used by the advanced dialog for storing custom
	 * advanced settings. Settings should be stored as Key's directly in the
	 * provided preference node.
	 * These preferences are provided to the Command Launcher at command launch
	 * @param prefs
	 */
	public void setPreferencesStore(Preferences prefs) {
		this.prefStore = prefs;
	}

	/**
	 * This method is called by the Pref/Properties page as the
	 * Executable extension was created using the 0-argument constructor
	 */
	@Override
	public void setParentShell(Shell newParentShell) {
		// TODO Auto-generated method stub
		super.setParentShell(newParentShell);
	}

}
