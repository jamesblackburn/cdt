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

package org.eclipse.cdt.internal.ui.preferences;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;

import org.eclipse.cdt.ui.CUIPlugin;
import org.eclipse.cdt.ui.dialogs.AbstractCommandLauncherDialog;

/**
 * Class responsible for loading contributed "Advanced..." configuration
 * dialogs for the CommandLauncher extension point
 * @since 5.2
 */
public class CommandLauncherAdvancedDialogFactory {
	private static final String COMMAND_LAUNCHER_DIALOG_EXT_POINT = "CommandLauncherDialog"; //$NON-NLS-1$

	/**
	 * Return whether the specified CommandLauncher provides an advanced configuration dialog
	 * @param launcherID
	 * @return boolean indicating advanced config dialog
	 */
	public static boolean hasAdvancedConfiguration(String launcherID) {
		AbstractCommandLauncherDialog clt = getCommandLauncherDialogExtensionPoints().get(launcherID);
		return clt != null;
	}

	/**
	 * Return the advanced dialog for configuring this CommandLauncher
	 * @return Object of type org.eclipse.cdt.ui.dialogs.AbstractCommandLauncherDialog
	 *         or null if one couldn't be created
	 */
	public static AbstractCommandLauncherDialog getAdvancedConfigurationDialog(String launcherID) {
		AbstractCommandLauncherDialog clt = getCommandLauncherDialogExtensionPoints().get(launcherID);
		return clt;
	}

	/** Cache of the launcher type ID -> Configuration Dialog */
	private static volatile Reference<Map<String, AbstractCommandLauncherDialog>> commandLauncherDialogs = new SoftReference<Map<String, AbstractCommandLauncherDialog>>(null);

	/**
	 * Initialize the command launcher types, returning the cached elements
	 * @return Map CommandLauncher Type ID -> CommandLauncherType
	 */
	private static Map<String, AbstractCommandLauncherDialog> getCommandLauncherDialogExtensionPoints() {
		Map<String, AbstractCommandLauncherDialog> commandLaunchers = commandLauncherDialogs.get();
		if (commandLaunchers != null)
			return commandLaunchers;
		commandLaunchers = new HashMap<String, AbstractCommandLauncherDialog>();
        IExtensionPoint extpoint = Platform.getExtensionRegistry().getExtensionPoint(CUIPlugin.PLUGIN_ID, COMMAND_LAUNCHER_DIALOG_EXT_POINT);
		for (IExtension extension : extpoint.getExtensions()) {
			for (IConfigurationElement configEl : extension.getConfigurationElements()) {
				if (configEl.getName().equalsIgnoreCase(COMMAND_LAUNCHER_DIALOG_EXT_POINT)) {
					String id = configEl.getAttribute("id"); //$NON-NLS-1$
					if (id == null) {
						CUIPlugin.log("Command Launcher Dialog extension point, id attribute can't be empty!", null); //$NON-NLS-1$
						continue;
					}
					try {
						AbstractCommandLauncherDialog cld = (AbstractCommandLauncherDialog)configEl.createExecutableExtension("class"); //$NON-NLS-1$
						commandLaunchers.put(id, cld);
					} catch (CoreException e) {
						CUIPlugin.log("Command Launcher Dialog extension point, couldn't instantiate \"class\" attribute on " + id, e); //$NON-NLS-1$
					}
				}
			}
		}
		commandLauncherDialogs = new SoftReference<Map<String, AbstractCommandLauncherDialog>>(commandLaunchers);
		return commandLaunchers;
	}
}
