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

import org.eclipse.core.resources.IBuildConfiguration;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowPulldownDelegate2;

import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.CProjectDescriptionEvent;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescriptionListener;
import org.eclipse.cdt.core.settings.model.ICProjectDescriptionManager;

import org.eclipse.cdt.internal.ui.actions.ActionMessages;
import org.eclipse.cdt.internal.ui.build.BuildHistory;
import org.eclipse.cdt.internal.ui.build.BuildHistoryEntry;
import org.eclipse.cdt.internal.ui.build.BuildPreferencePage;
import org.eclipse.cdt.internal.ui.cview.BuildGroup;
import org.eclipse.cdt.internal.ui.cview.BuildGroup.CDTBuildAction;

/**
 * Implements a toolbar button that builds the active configuration
 * of selected projects. Also includes a menu that builds any of the
 * other configurations.
 */
public class BuildActiveConfigMenuAction extends ChangeBuildConfigActionBase
		implements IWorkbenchWindowPulldownDelegate2, ICProjectDescriptionListener {

	private CDTBuildAction buildaction;
	private IAction actionMenuCache; // cache the menu action so we can update the tool tip when the configuration changes

	/**
	 * @see org.eclipse.ui.IWorkbenchWindowPulldownDelegate2#getMenu(org.eclipse.swt.widgets.Menu)
	 */
	public Menu getMenu(Menu parent) {
		Menu menu = new Menu(parent);
		addMenuListener(menu);
		return menu;
	}

	/**
	 * @see org.eclipse.ui.IWorkbenchWindowPulldownDelegate#getMenu(org.eclipse.swt.widgets.Control)
	 */
	public Menu getMenu(Control parent) {
		Menu menu = new Menu(parent);
		addMenuListener(menu);
		return menu;
	}

	/**
	 * @see org.eclipse.ui.IWorkbenchWindowActionDelegate#dispose()
	 */
	public void dispose() {
		ICProjectDescriptionManager mngr = CoreModel.getDefault().getProjectDescriptionManager();
		mngr.removeCProjectDescriptionListener(this);
	}

	/**
	 * @see org.eclipse.ui.IWorkbenchWindowActionDelegate#init(org.eclipse.ui.IWorkbenchWindow)
	 */
	public void init(IWorkbenchWindow window) {
		buildaction = new BuildGroup.CDTBuildAction(window, IncrementalProjectBuilder.INCREMENTAL_BUILD);
		ICProjectDescriptionManager mngr = CoreModel.getDefault().getProjectDescriptionManager();
		mngr.addCProjectDescriptionListener(this, CProjectDescriptionEvent.APPLIED);
	}

	/**
	 * @see org.eclipse.ui.IActionDelegate#run(org.eclipse.jface.action.IAction)
	 */
	public void run(IAction action) {
		Object[] projects = fProjects.toArray();
		buildaction.setBuildConfigurationToBuild(null);

		if (!BuildPreferencePage.isBuildCurrentSelection() && BuildHistory.getLastBuilt() != null) {
			// Build the last thing built
			IBuildConfiguration[] configs = BuildHistory.getLastBuilt().configs;
			// Tell the build action what it is we're building.
			buildaction.setBuildConfigurationToBuild(Arrays.asList(configs));
			// Fire a selection changed so enablement etc. is happy.
			projects = new Object[configs.length];
			for (int i = 0; i < configs.length; i++)
				projects[i] = configs[i].getProject();
		} else
			// Register these projects as the last thing built
			BuildHistory.addBuildHistory(new BuildHistoryEntry(fProjects.toArray(new IProject[fProjects.size()])));

		// Fire the change event and run the CDT Build Action
		buildaction.selectionChanged(new StructuredSelection(projects));
		buildaction.run();
	}

	/**
	 * Add build history to the build menu
	 */
	@Override
	protected void additionalFill(Menu menu, int accel) {
		boolean selection_needed = !BuildPreferencePage.isBuildCurrentSelection();

		// Add the build history
		BuildHistoryEntry[] history = BuildHistory.getBuildHistory();
		if (history.length != 0) {
			new MenuItem(menu, SWT.SEPARATOR);
			for (BuildHistoryEntry e : history) {
				IAction action = makeAction(e, accel++);
				// Select the first item
				if (selection_needed) {
					// Ensure none of the existing entries are checked
					for (MenuItem item: menu.getItems())
						item.setSelection(false);
					action.setChecked(true);
					selection_needed = false;
				}
				ActionContributionItem item = new ActionContributionItem(action);
				item.fill(menu, -1);
			}
		}		
	}

	/**
	 * @see org.eclipse.ui.IActionDelegate#selectionChanged(org.eclipse.jface.action.IAction, org.eclipse.jface.viewers.ISelection)
	 */
	public void selectionChanged(IAction action, ISelection selection) {
		if (actionMenuCache == null){
			actionMenuCache = action;
		}
		onSelectionChanged(action, selection);
		// If we're building from the build history (and there is history) ensure the action remains enabled
		if (!BuildPreferencePage.isBuildCurrentSelection() && BuildHistory.getLastBuilt() != null)
			action.setEnabled(true);
		updateBuildConfigMenuToolTip(action);
	}
	
	/**
	 * Adds a listener to the given menu to re-populate it each time is is shown
	 * @param menu The menu to add listener to
	 */
	private void addMenuListener(Menu menu) {
		menu.addMenuListener(new MenuAdapter() {
			@Override
			public void menuShown(MenuEvent e) {
				fillMenu((Menu)e.widget);
			}
		});
	}

	@Override
	protected IAction makeAction(String sName, StringBuffer builder, int accel) {
		return new BuildConfigAction(fProjects, sName, builder.toString(), accel, buildaction);
	}
	
	@Override
	protected IAction makeAction(BuildHistoryEntry e, int accel) {
		return new BuildConfigAction(e, accel, buildaction);
	}

	/**
	 * Update the tool tip based on the currently selected project and active configuration.
	 * @param action - The build configuration menu to change the tool tip on
	 */
	public void updateBuildConfigMenuToolTip(IAction action){
		String toolTipText = ""; //$NON-NLS-1$
		if (BuildPreferencePage.isBuildCurrentSelection() || BuildHistory.getLastBuilt() == null) {
			if (fProjects.size() <= 5) {
				StringBuilder sb = new StringBuilder();
				for (IProject prj : fProjects) {
					if (prj != null){
						ICProjectDescription prjd = CoreModel.getDefault().getProjectDescription(prj, false);
						if (prjd != null) {
							sb.append(NLS.bind(ActionMessages.BuildActiveConfigMenuAction_buildConfigTooltip,
									prjd.getActiveConfiguration().getName(), prj.getName())).append(System.getProperty("line.separator")); //$NON-NLS-1$
						}
					}
				}
				toolTipText = sb.toString().trim();
			}
		} else {
			toolTipText = ActionMessages.BuildActiveConfigMenuAction_RunLastBuild + BuildHistory.getLastBuilt();
		}

		if (toolTipText.length() == 0)
			toolTipText = ActionMessages.BuildActiveConfigMenuAction_defaultTooltip;
		action.setToolTipText(toolTipText);
	}

	public void handleEvent(CProjectDescriptionEvent event) {
		if (actionMenuCache != null)
			updateBuildConfigMenuToolTip(actionMenuCache);
	}
}
