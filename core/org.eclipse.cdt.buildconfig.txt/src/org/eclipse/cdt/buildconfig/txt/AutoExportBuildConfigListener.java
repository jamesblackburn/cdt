/*******************************************************************************
 * Copyright (c) 2009 Broadcom Corporation
 *
 * This material is the confidential trade secret and proprietary information of
 * Broadcom Corporation. It may not be reproduced, used, sold or transferred to
 * any third party without the prior written consent of Broadcom Corporation.
 * All rights reserved.
 *
 ******************************************************************************/
package org.eclipse.cdt.buildconfig.txt;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.cdt.core.settings.model.CProjectDescriptionEvent;
import org.eclipse.cdt.core.settings.model.ICProjectDescriptionListener;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

/**
 * This class schedules automatically exports build configuration to a
 * text file in the project on modification of the build model.
 *
 * A decorator (very low priority) job is scheduled a few hundred ms
 * after the change to the configuration, and the configuration is
 * serialized to a standard place /ProjectName/build_config.txt
 * to automatically log the build configuration changes 500ms after
 * a build configuration change
 */
public class AutoExportBuildConfigListener implements ICProjectDescriptionListener {

	private static final Map<IProject, IProject> projectsToExport = new ConcurrentHashMap<IProject, IProject>();

	private final Job exportConfigurationJob = new Job("Exporting CDT Build Configuration...") {
		{{
			setPriority(Job.DECORATE);
			setUser(false);
		}}
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			while (!projectsToExport.isEmpty()) {
				final IProject project = projectsToExport.keySet().iterator().next();
				projectsToExport.remove(project);
				// If the file in the default location starts with our magic string, then overwrite it.
				final IFile configFile = project.getFile(CDTBuildSettingsExporter.DEFAULT_BUILD_CONFIG_FILE);
				// Check whether, if the file exists, it's one we created previously...
				if (configFile.exists()) {
					BufferedReader r = null;
					try {
						r = new BufferedReader(new InputStreamReader(configFile.getContents(true)));
						String s = r.readLine();
						// If the build config file does not start with our magic text then don't overwrite
						if (!s.startsWith(CDTBuildSettingsExporter.BUILD_CONFIG_MAGIC_LINE))
							continue;
					} catch (Exception e) {
						Activator.log(e);
					} finally {
						if (r != null)
							try {r.close();} catch (Exception e) {/* Don't care */}
					}
				}
				// Export the settings
				new CDTBuildSettingsExporter().printConfigurationSettings(project, configFile);
			}
			return Status.OK_STATUS;
		}
	};

	/** Schedule the job after a delay of 500ms */
	private static final int SCHEDULE_DELAY = 500;

	public void handleEvent(CProjectDescriptionEvent event) {
		if (event.getEventType() == CProjectDescriptionEvent.APPLIED) {
			projectsToExport.put(event.getProject(), event.getProject());
			exportConfigurationJob.schedule(SCHEDULE_DELAY);
		}
	}

}
