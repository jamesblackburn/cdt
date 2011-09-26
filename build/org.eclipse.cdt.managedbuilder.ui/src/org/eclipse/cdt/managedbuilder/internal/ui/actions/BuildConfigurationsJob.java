/*******************************************************************************
 * Copyright (c) 2010, 2010 Andrew Gvozdev and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrew Gvozdev (Quoin Inc) - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.managedbuilder.internal.ui.actions;

import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.managedbuilder.internal.ui.Messages;
import org.eclipse.cdt.ui.CUIPlugin;
import org.eclipse.core.resources.IBuildConfiguration;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import com.ibm.icu.text.MessageFormat;

/**
 * A job to build CDT build configurations.
 */
public class BuildConfigurationsJob extends Job {
	private IBuildConfiguration[] configurations;
	private int cleanKind;
	private int buildKind;

	private static String composeJobName(ICConfigurationDescription[] cfgDescriptions, boolean isCleaning) {
		String firstProjectName = cfgDescriptions[0].getProjectDescription().getName();
		String firstConfigurationName = cfgDescriptions[0].getName();
		if (isCleaning) {
			return MessageFormat.format(Messages.BuildConfigurationsJob_Cleaning,
					new Object[] {""+cfgDescriptions.length, firstProjectName, firstConfigurationName}); //$NON-NLS-1$
		} else {
			return MessageFormat.format(Messages.BuildConfigurationsJob_Building,
					new Object[] {""+cfgDescriptions.length, firstProjectName, firstConfigurationName}); //$NON-NLS-1$
		}
	}
	
	/**
	 * Constructor.
	 * 
	 * @param cfgDescriptions - a list of configurations to build, possibly from different projects
	 * @param cleanKind - pass {@link IncrementalProjectBuilder#CLEAN_BUILD} to clean before building
	 * @param buildKind - kind of build. Can be
	 *    {@link IncrementalProjectBuilder#INCREMENTAL_BUILD}
	 *    {@link IncrementalProjectBuilder#FULL_BUILD}
	 *    {@link IncrementalProjectBuilder#AUTO_BUILD}
	 */
	public BuildConfigurationsJob(ICConfigurationDescription[] cfgDescriptions, int cleanKind, int buildKind) {
		super(composeJobName(cfgDescriptions, buildKind==0));

		configurations = new IBuildConfiguration[cfgDescriptions.length];
		for (int i = 0; i < cfgDescriptions.length; i++) {
			IProject project = cfgDescriptions[i].getProjectDescription().getProject();
			configurations[i] = ResourcesPlugin.getWorkspace().newBuildConfig(project.getName(), cfgDescriptions[i].getName());
		}
		
		this.cleanKind = cleanKind;
		this.buildKind = buildKind;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.jobs.Job#run(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	protected IStatus run(IProgressMonitor monitor) {
		// Setup the global build console
		CUIPlugin.getDefault().startGlobalConsole();

		try {
			if (cleanKind==IncrementalProjectBuilder.CLEAN_BUILD) {
				ResourcesPlugin.getWorkspace().build(configurations, IncrementalProjectBuilder.CLEAN_BUILD, true, monitor);
			}
			if (buildKind!=0) {
				ResourcesPlugin.getWorkspace().build(configurations, buildKind, true, monitor);
			}
		} catch (CoreException e) {
			return new Status(IStatus.ERROR, Messages.BuildConfigurationsJob_BuildError, e.getLocalizedMessage());
		}
		if (monitor.isCanceled()) {
			return Status.CANCEL_STATUS;
		}
		monitor.done();
		return Status.OK_STATUS;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.jobs.Job#belongsTo(java.lang.Object)
	 */
	@Override
	public boolean belongsTo(Object family) {
		return ResourcesPlugin.FAMILY_MANUAL_BUILD == family;
	}
}
