/*******************************************************************************
 * Copyright (c) 2011 Broadcom and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     James Blackburn (Broadcom Corp.) - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.core.resources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.settings.model.ICReferenceEntry;
import org.eclipse.core.resources.IBuildConfiguration;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.jobs.Job;

/**
 * Helper class responsible for reconciling CDT build configurations
 * with platform build configurations.  This happens during pre-build to ensure that
 * the Workspace build configurations remain in-sync with CDT.
 */
public class BuildConfigReconciler implements IResourceChangeListener {

	/**
	 * Provide a hook to allow a 3rd party to override the set of Platform level references.
	 * This allows a 3rd party to do funky things with their builder, for example have multiple
	 * platform configurations for a single CDT configuration.
	 */
	public static interface BuildConfigReconcilerHook {
		/**
		 * Hook to notify listener of impending changes to the references of a buildconfiguration.
		 * The hook can modify the passed collection which will then be persisted.
		 * @param config the configuration which is doing the referencing
		 * @param refs the build configurations references of the configuration
		 */
		public abstract void updatedPlatformReferences(IBuildConfiguration config, Collection<IBuildConfiguration> refs);
	}

	volatile static BuildConfigReconciler instance;
	static IWorkspace ws = ResourcesPlugin.getWorkspace();
	ConcurrentLinkedQueue<IProject> projectsToUpdateBuildConfigs = new ConcurrentLinkedQueue<IProject>();
	volatile BuildConfigReconcilerHook hook;

	private BuildConfigReconciler() {}

	/**
	 * Startup the BuildConfig Reconciler
	 */
	public static void startup() {
		instance = new BuildConfigReconciler();
		ws.addResourceChangeListener(instance, IResourceChangeEvent.PRE_BUILD);
		// On startup, reconcile all the WS projects
		instance.projectsToUpdateBuildConfigs.addAll(Arrays.asList(ws.getRoot().getProjects()));
	}

	/**
	 * Shutdown the BuildConfig reconciler
	 */
	public static void shutdown() {
		ws.removeResourceChangeListener(instance);
		instance = null;
	}

	public static BuildConfigReconciler getInstance() {
		return instance;
	}

	/**
	 * @param hook called back when build configuration references are about to change
	 */
	public void setBuildConfigReconcilerHook(BuildConfigReconcilerHook hook) {
		this.hook = hook;
	}

	/**
	 * Updates the platform configurations based on the passed in project configuration
	 *
	 * @param project
	 * @param desc
	 * @return boolean if anything changed
	 * @throws CoreException
	 */
	public boolean updatePlatformConfigurations(IProject project, IProjectDescription desc) throws CoreException {
		boolean changed = false;

		// Get the CProjectDescription
		ICProjectDescription cDesc = CoreModel.getDefault().getProjectDescription(project, false);
		if (cDesc == null)
			return changed;

		// Add any build configurations
		ICConfigurationDescription[] cfgs = cDesc.getConfigurations();
		IBuildConfiguration[] existingPlatformConfigs = project.getBuildConfigs();

		// Add any configurations which aren't already in the .project
		LinkedHashSet<IBuildConfiguration> newConfigs = new LinkedHashSet<IBuildConfiguration>();
		for (ICConfigurationDescription cfg : cfgs)
			newConfigs.add(ws.newBuildConfig(project.getName(), cfg.getName()));
		newConfigs.addAll(Arrays.asList(existingPlatformConfigs));
		// Ensure newConfigs doesn't contain the 'default' configurations
		newConfigs.remove(ws.newBuildConfig(project.getName(), IBuildConfiguration.DEFAULT_CONFIG_NAME));

		IBuildConfiguration[] newPlatformConfigs = newConfigs.toArray(new IBuildConfiguration[newConfigs.size()]);
		if (!Arrays.equals(newPlatformConfigs, existingPlatformConfigs)) {
			String[] configs = new String[newPlatformConfigs.length];
			for (int i = 0 ; i < configs.length; i++)
				configs[i] = newPlatformConfigs[i].getName();
			desc.setBuildConfigs(configs);
			changed = true;
		}

		// Has the active configuration changed?
		if (!project.getActiveBuildConfig().getName().equals(cDesc.getActiveConfiguration().getName())) {
			desc.setActiveBuildConfig(cDesc.getActiveConfiguration().getName());
			changed = true;
		}

		// Get the platform references
		LinkedHashSet<IProject> projectRefs = new LinkedHashSet<IProject>(Arrays.asList(desc.getReferencedProjects()));
		// Need to add all reference information to core resources, not just the active configuration
		for (ICConfigurationDescription cfg : cfgs) {
			ICReferenceEntry[] refEntries = cfg.getReferenceEntries();

			// Update references
			List<IBuildConfiguration> refs = new ArrayList<IBuildConfiguration>();
			for (ICReferenceEntry refEntry : refEntries) {
				// By default reference the active configuration
				IBuildConfiguration ref = ws.newBuildConfig(refEntry.getProject(), null);
				if (refEntry.getConfiguration().length() > 0) {
					ICProjectDescription pdesc = CoreModel.getDefault().getProjectDescription(ws.getRoot().getProject(refEntry.getProject()), false);
					if (pdesc != null) {
						ICConfigurationDescription config = pdesc.getConfigurationById(refEntry.getConfiguration());
						if (config != null)
							ref = ws.newBuildConfig(refEntry.getProject(), config.getName());
					}
				}
				refs.add(ref);
				// Adding a dynamic reference removes the static project level reference.
				if (projectRefs.contains(ref.getProject())) {
					projectRefs.remove(ref.getProject());
					changed = true;
				}
			}

			if (hook != null)
				hook.updatedPlatformReferences(ws.newBuildConfig(project.getName(), cfg.getName()), refs);

			IBuildConfiguration[] refArr = refs.toArray(new IBuildConfiguration[refs.size()]);
			IBuildConfiguration[] origArr =desc.getBuildConfigReferences(cfg.getName());
			if (!Arrays.equals(refArr, origArr)) {
				desc.setBuildConfigReferences(cfg.getName(), refArr);
				changed = true;
			}
		}
		if (changed)
			desc.setReferencedProjects(projectRefs.toArray(new IProject[projectRefs.size()]));
		return changed;
	}

	/**
	 * Helper method to update the core.resources build configurations on a project
	 * @param project
	 */
	private void updatePlatformConfigurationsOnProject(IProject project) {
		try {
			try {
			// Need the scheduling rule so we don't conflict with any other jobs during project creation
			// WR is needed for setting description
			Job.getJobManager().beginRule(ws.getRoot(), null);

			boolean changed = false;
			if (!project.isAccessible())
				return;

			final IProjectDescription desc = project.getDescription();
			changed = updatePlatformConfigurations(project, desc);

			// Ensure any projects which reference this one have references up-to-date
			if (changed) {
				project.setDescription(desc, null);
				for (IProject ref : project.getReferencingProjects())
					if (!projectsToUpdateBuildConfigs.contains(ref))
						projectsToUpdateBuildConfigs.add(ref);
			}
			} finally {
				Job.getJobManager().endRule(ws.getRoot());
			}
		} catch (CoreException e) {
			CCorePlugin.log(e);
		}
	}

	/**
	 * This is invoked whenever a project is opened. It synchronizes CDT configuration
	 * names with platform project variant names, the active CDT configuration with
	 * the active platform project variant and CDT configuration references with
	 * platform project variant references. If the project that was opened is not
	 * a CDT project then the synchronization is skipped.
	 *
	 * Note we will pick this up by virtue of the platform resource delta as well...
	 *
	 * @param project the project that was opened
	 */
	public void projectOpened(IProject project) {
		if (projectsToUpdateBuildConfigs.contains(project))
			return;
		projectsToUpdateBuildConfigs.add(project);
	}

	/**
	 * PRE_BUILD take note of interesting changes in projects, and update the platform
	 * build configurations appropriately.
	 */
	public void resourceChanged(IResourceChangeEvent event) {
		// Must examine:
		//  - Added projects
		//  - Projects with modified Description
		//  - Projects with modified .cproject
		try {
			event.getDelta().accept(new IResourceDeltaVisitor() {
				public boolean visit(IResourceDelta delta) throws CoreException {
					IResourceDelta[] affected = delta.getAffectedChildren();
					for (IResourceDelta child : affected) {
						// Already being considered, continue
						if (projectsToUpdateBuildConfigs.contains(child.getResource().getProject()))
							continue;

						// If the project has been opened update all the workspace mappings
						if ((child.getFlags() & IResourceDelta.OPEN) != 0 &&
						     child.getResource().isAccessible()) {
							projectsToUpdateBuildConfigs.add(child.getResource().getProject());
							continue;
						}

						// If .project || .cproject file has changed
						// Then need to rebuild the part of the graph corresponding to that project.
						if ((child.getFlags() & IResourceDelta.DESCRIPTION) != 0 ||
							 child.findMember(new Path(".cproject")) != null) //$NON-NLS-1$
							projectsToUpdateBuildConfigs.add(child.getResource().getProject());
					}
					return false;
				}
			});
		} catch (CoreException e) {
			CCorePlugin.log(e);
		}

		// Now update the build configurations on the requested projects
		while (!projectsToUpdateBuildConfigs.isEmpty()) {
			IProject project = projectsToUpdateBuildConfigs.poll();
			if (project == null)
				continue;
			updatePlatformConfigurationsOnProject(project);
		}
	}
}
