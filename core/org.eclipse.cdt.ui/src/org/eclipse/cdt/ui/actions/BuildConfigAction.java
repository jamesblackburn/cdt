/*******************************************************************************
 * Copyright (c) 2007, 2011 Nokia and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nokia - initial API and implementation
 *     James Blackburn (Broadcom Corp.) - ongoing development
 *******************************************************************************/
package org.eclipse.cdt.ui.actions;

import java.util.Arrays;
import java.util.HashSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.actions.BuildAction;

import org.eclipse.cdt.internal.ui.cview.BuildGroup.CDTBuildAction;

/**
 * Action used for running a build.  It first runs {@link ChangeConfigAction}
 * causing the associated project active build configurations to be changed
 * before firing off the build
 */
public class BuildConfigAction extends ChangeConfigAction {
	
	private BuildAction buildAction;

	/**
	 * Constructs the action.
	 * @param projects List of selected managed-built projects 
	 * @param configName Build configuration name
	 * @param accel Number to be used as accelerator
	 */
	public BuildConfigAction(HashSet<IProject> projects, String configName, String displayName, int accel, BuildAction buildAction) {
		super(projects, configName, displayName, accel);
		this.buildAction = buildAction;
	}
	
	/**
	 * @see org.eclipse.jface.action.IAction#run()
	 */
	@Override
	public void run() {
		if (buildAction instanceof CDTBuildAction)
			// Just request a run of the corresponding build configurations
			((CDTBuildAction)buildAction).setBuildConfigurationToBuild(Arrays.asList(buildConfigs));
		// Changes the active configuration on the project, if needed
		super.run();
		// Sets the selection on the build action and run it
		buildAction.selectionChanged(new StructuredSelection(buildConfigs));
		buildAction.run();		
	}
}
