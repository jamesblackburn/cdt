/*******************************************************************************
 * Copyright (c) 2011 Broadcom Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.ui.build;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.resources.IBuildConfiguration;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

import org.eclipse.cdt.ui.CUIPlugin;
import org.eclipse.cdt.ui.PreferenceConstants;

/**
 * Class responsible for handling history of which projects and configurations
 * were built.
 *
 * This state is global to the workspace
 */
public class BuildHistory {

	private static final String buildHistoryKey = "org.eclise.cdt.ui.build.historyStore"; //$NON-NLS-1$

	/**
	 * Last N-things built
	 * @guarded buildHistory
	 */
	private static LinkedList<BuildHistoryEntry> buildHistory = new LinkedList<BuildHistoryEntry>();

	/**
	 * Add the BuildHistoryEntry as the most recent thing built
	 * @param be
	 */
	public static void addBuildHistory(BuildHistoryEntry be) {
		synchronized (buildHistory) {
			buildHistory.remove(be);
			buildHistory.addFirst(be);
			// New item add to the build history, update.
			while (buildHistory.size() > PreferenceConstants.getPreference(PreferenceConstants.PREF_BUILD_HISTORY_SIZE, null,
																		   BuildPreferencePage.PREF_BUILD_HISTORY_SIZE_DEFAULT))
				buildHistory.removeLast();
		}
	}

	/**
	 * @return the last thing built or null if no previous builds
	 */
	public static BuildHistoryEntry getLastBuilt() {
		synchronized (buildHistory) {
			if (buildHistory.isEmpty())
				return null;
			return buildHistory.getFirst();
		}
	}

	/**
	 * @return full build history
	 */
	public static BuildHistoryEntry[] getBuildHistory() {
		synchronized (buildHistory) {
			return buildHistory.toArray(new BuildHistoryEntry[buildHistory.size()]);
		}
	}


	static  {
		// Load the buildHistory when the class is accessed.
		Preferences prefs = new InstanceScope().getNode(CUIPlugin.PLUGIN_ID).node(buildHistoryKey);
		// History Entries are stored
		// 1 -- entry number
		//   <project_name>
		//      <config_name>
		//      <config_name2>
		//   <project_name_2>
		//   ..
		// 2 -- entry number2
		try {
			if (prefs != null) {
				IWorkspace ws = ResourcesPlugin.getWorkspace();
				Map<Integer, BuildHistoryEntry> history = new TreeMap<Integer, BuildHistoryEntry>();
				ArrayList<IBuildConfiguration> buildConfigs = new ArrayList<IBuildConfiguration>();
				for (String i : prefs.childrenNames()) {
					for (String proj : prefs.node(i).childrenNames())
						for (String config : prefs.node(i).node(proj).childrenNames())
							buildConfigs.add(ws.newBuildConfig(proj, config));
					history.put(Integer.valueOf(i), new BuildHistoryEntry(buildConfigs.toArray(new IBuildConfiguration[buildConfigs.size()])));
					buildConfigs.clear();
				}
				synchronized (buildHistory) {
					buildHistory.clear();
					buildHistory.addAll(history.values());
				}
			}
		} catch (BackingStoreException e) {
			CUIPlugin.log(e);
		}
	}

	public static void save() {
		synchronized (buildHistory) {
			Preferences prefs = new InstanceScope().getNode(CUIPlugin.PLUGIN_ID).node(buildHistoryKey);
			try {
				// Clear existing history
				for (String child : prefs.childrenNames())
					prefs.node(child).removeNode();

				int i = 0;
				for (BuildHistoryEntry e : buildHistory) {
					for (IBuildConfiguration config : e.configs)
						// Create a node with dummy data so it's written out
						prefs.node(Integer.toString(i)).node(config.getProject().getName()).node(config.getName()).put("exists", "1");  //$NON-NLS-1$//$NON-NLS-2$
					i++;
				}
				prefs.flush();
			} catch (BackingStoreException e) {
				CUIPlugin.log(e);
			}
		}
	}

}
