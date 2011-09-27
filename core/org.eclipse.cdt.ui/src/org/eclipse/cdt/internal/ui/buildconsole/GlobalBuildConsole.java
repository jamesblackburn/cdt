/*******************************************************************************
 * Copyright (c) 2010 Broadcom Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Alex Collins (Broadcom Corp.)
 *******************************************************************************/
package org.eclipse.cdt.internal.ui.buildconsole;

import java.net.URL;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swt.widgets.Display;

import org.eclipse.cdt.ui.IBuildConsoleManager;

/**
 * Customized BuildConsole for the global console that displays its title differently
 */
public class GlobalBuildConsole extends BuildConsole implements IResourceChangeListener {
	IProject lastProject;

	public GlobalBuildConsole(IBuildConsoleManager manager, String name, String contextId, URL iconUrl) {
		super(manager, name, contextId, iconUrl);
		ResourcesPlugin.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_BUILD);
	}

	@Override
	public void setTitle(IProject project) {
		lastProject = project;
		super.setTitle(project);
	}

	public void resourceChanged(IResourceChangeEvent event) {
		if (lastProject == null)
			return;
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				setTitle(null);
			}
		});
	}

	@Override
	protected void dispose() {
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
		super.dispose();
	}
}
