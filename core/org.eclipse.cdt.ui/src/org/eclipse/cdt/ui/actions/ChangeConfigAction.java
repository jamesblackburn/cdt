/*******************************************************************************
 * Copyright (c) 2006, 2008 Intel Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Intel Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.ui.actions;

import java.util.HashSet;

import org.eclipse.core.resources.IBuildConfiguration;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.action.Action;

import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.ui.newui.AbstractPage;
import org.eclipse.cdt.ui.newui.CDTPropertyManager;

/**
 * Action which changes active build configuration of the current project to 
 * the given one.
 */
public class ChangeConfigAction extends Action {

	/** Collection of platform build configurations modified by this build action */
	final IBuildConfiguration[] buildConfigs;

	/**
	 * Constructs the action.
	 * @param projects List of selected managed-built projects
	 * @param configName Build configuration name
	 * @param accel Number to be used as accelerator
	 */
	public ChangeConfigAction(HashSet<IProject> projects, String configName, String displayName, int accel) {
		super("&" + accel + " " + displayName); //$NON-NLS-1$ //$NON-NLS-2$

		// This action assumes the same configuration exists in each project.
		IWorkspace ws = ResourcesPlugin.getWorkspace();
		buildConfigs = new IBuildConfiguration[projects.size()];
		int i = 0;
		for (IProject p : projects)
			buildConfigs[i++] = ws.newBuildConfig(p.getName(), configName);
	}

	/**
	 * Run the action (changing the active build configuration)
	 * @see org.eclipse.jface.action.IAction#run()
	 */
	@Override
	public void run() {
		for (IBuildConfiguration config : buildConfigs) {
			ICProjectDescription prjd = CDTPropertyManager.getProjectDescription(config.getProject());
			boolean changed = false;
			ICConfigurationDescription cConfig = prjd.getConfigurationByName(config.getName());
			if (cConfig != null) {
				cConfig.setActive();
				CDTPropertyManager.performOk(null);
				AbstractPage.updateViews(config.getProject());
				changed = true;
			}

			if(!changed)
				CDTPropertyManager.performCancel(null);
		}
	}
}
