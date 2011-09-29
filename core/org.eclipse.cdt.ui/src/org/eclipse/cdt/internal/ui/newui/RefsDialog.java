/*******************************************************************************
 * Copyright (c) 2010 Broadcom Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Alex Collins (Broadcom Corporation) - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.ui.newui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.StatusDialog;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.CReferenceEntry;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.settings.model.ICReferenceEntry;

/**
 * Dialog for {@link RefsTab} that allows one or more references to be added to the
 * project configuration. It allows references to more than one configuration in a
 * single project.
 * @noextend This class is not intended to be subclassed by clients.
 */
public class RefsDialog extends StatusDialog {
	private RefsContentProvider contentProvider;
	private CheckboxTreeViewer tree;
	private Button expandAll;
	private Button collapseAll;
	private List<ICReferenceEntry> newReferences = new ArrayList<ICReferenceEntry>(0);

	private static final String ACTIVE = "[" + Messages.RefsTab_Active + "]"; //$NON-NLS-1$ //$NON-NLS-2$
	private static final String EMPTY_STR = ""; //$NON-NLS-1$
	private static final String ADD_STR = Messages.FileListControl_add;
    private static final int LIST_HEIGHT = 18;

	public RefsDialog(Shell parent, IProject project, ICConfigurationDescription configuration) {
		super(parent);
		setTitle(ADD_STR);
		setHelpAvailable(false);
		contentProvider = new RefsContentProvider(project, configuration);
	}

	/** @return The list of references chosen to be added in the dialog */
	public List<ICReferenceEntry> getNewReferences() {
		return newReferences;
	}

	@Override
	protected Control createDialogArea(Composite comp) {
		GridLayout layout = new GridLayout(2, false);
		GridData data;
		comp.setLayout(layout);

		Label label = new Label(comp, SWT.NONE);
		label.setText(Messages.RefsTab_AddReferences);
		data = new GridData();
		data.horizontalSpan = 2;
		label.setLayoutData(data);

		tree = createTree(comp);
		tree.collapseAll();
		data = new GridData(SWT.FILL, SWT.FILL, true, true);
        data.heightHint = convertHeightInCharsToPixels(LIST_HEIGHT);
		tree.getTree().setLayoutData(data);

		Composite buttoncomp = new Composite(comp, SWT.NONE);
		buttoncomp.setLayoutData(new GridData(SWT.NONE, SWT.FILL, false, true));
		buttoncomp.setLayout(new GridLayout(1, false));

		expandAll = setupButton(buttoncomp, Messages.RefsTab_ExpandAll);
		collapseAll = setupButton(buttoncomp, Messages.RefsTab_CollapseAll);

		Composite bottomcomp = new Composite(comp, SWT.NONE);
		data = new GridData(SWT.RIGHT, SWT.BOTTOM, false, false);
		data.horizontalSpan = 2;
		bottomcomp.setLayoutData(data);
		bottomcomp.setLayout(new GridLayout(2, true));

		updateButtons();
		return comp;
	}

	private Button setupButton(Composite c, String text) {
		Button button = new Button(c, SWT.PUSH);
		button.setText(text);
		button.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		button.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				buttonPressed(event);
			}
		});
		return button;
	}

	/**
	 * Create the tree viewer and populate it with existing configurations from the
	 * workspace that are not already referenced by this project configuration.
	 * @param comp composite to add the tree viewer to
	 * @return the tree viewer widget
	 */
	private CheckboxTreeViewer createTree(Composite comp) {
		CheckboxTreeViewer tree = new CheckboxTreeViewer(comp, SWT.SINGLE | SWT.CHECK | SWT.BORDER);
		tree.setContentProvider(contentProvider);

		TreeViewerColumn column = new TreeViewerColumn(tree, SWT.NONE);
		column.getColumn().setWidth(150);
		column.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof String)
					return (String)element;
				else if (element instanceof Reference)
					return ((Reference)element).getCfgName();
				else
					return EMPTY_STR;
			}
		});

		tree.addCheckStateListener(new ICheckStateListener() {
			public void checkStateChanged(CheckStateChangedEvent event) {
				checkedChanged(event);
			}
		});

		tree.addTreeListener(new ITreeViewerListener() {
			public void treeCollapsed(TreeExpansionEvent event) {
				updateExpandButtons(event, false, true);
			}
			public void treeExpanded(TreeExpansionEvent event) {
				updateExpandButtons(event, true, false);
			}
		});

		tree.setInput(contentProvider.getElements(null));

		return tree;
	}

	/**
	 * Update the buttons and dialogue to reflect the fact that a project or
	 * configuration was checked/unchecked.
	 */
	private void checkedChanged(CheckStateChangedEvent event) {
		Object element = event.getElement();
		Object parent = contentProvider.getParent(element);

		// Was a project (un)checked?
		if (contentProvider.hasChildren(element)) {
			// If a project is checked, expand it.
			// If it has a currently active configuration check it, otherwise check [Active]
			if (event.getChecked()) {
				tree.setExpandedState(element, true);

				// Try checking the currently active configuration
				boolean childIsChecked = false;
				try {
					IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject((String) element);
					if (project.isAccessible()) {
						String activeCfgName = project.getActiveBuildConfig().getName();
						for (Object child : contentProvider.getChildren(element)) {
							String cfgName = ((Reference) child).getCfgName();
							if (cfgName.equals(activeCfgName)) {
								tree.setChecked(child, true);
								childIsChecked = true;
								break;
							}
						}
					}
				} catch (CoreException e) {
				}

				// If checking the currently active configuration failed, check [Active]
				if (!childIsChecked) {
					for (Object child : contentProvider.getChildren(element)) {
						String cfgId = ((Reference) child).getRef().getConfiguration();
						if (cfgId.equals(EMPTY_STR)) {
							tree.setChecked(child, true);
							break;
						}
					}
				}
			} else {
				// If a project is unchecked, uncheck all of its configurations
				for (Object child : contentProvider.getChildren(element))
					tree.setChecked(child, false);
			}
		} else {
			// Otherwise a configuration was (un)checked
			// Update the configurations project to be:
			//   - checked if at least one configuration is checked
			//   - unchecked if no configurations are checked
			boolean hasChildrenChecked = false;
			for (Object child : contentProvider.getChildren(parent)) {
				if (tree.getChecked(child)) {
					hasChildrenChecked = true;
					break;
				}
			}
			tree.setChecked(parent, hasChildrenChecked);
		}
		save();
	}

	private void updateButtons() {
		updateExpandButtons(null, false, false);
	}

	/**
	 * Update the expand/collapse buttons, based on whether any
	 * of the elements in the tree viewer are expanded/collapsed
	 */
	private void updateExpandButtons(TreeExpansionEvent event, boolean expanded, boolean collapsed) {
		for (Object item : contentProvider.getElements(null)) {
			if (expanded && collapsed)
				break;
			if (event != null && event.getElement().equals(item))
				continue;
			if (tree.getExpandedState(item))
				expanded = true;
			else
				collapsed = true;
		}
		expandAll.setEnabled(collapsed);
		collapseAll.setEnabled(expanded);
	}

	public void buttonPressed(SelectionEvent event) {
		if (event.widget.equals(expandAll)) {
			tree.expandAll();
			updateButtons();
		} else if (event.widget.equals(collapseAll)) {
			tree.collapseAll();
			updateButtons();
		}
	}

	/** Update the list of references to be added when the dialog is closed */
	private void save() {
		newReferences = new ArrayList<ICReferenceEntry>();
		for (Object element : tree.getCheckedElements()) {
			if (element instanceof Reference) {
				Reference ref = (Reference) element;
				newReferences.add(ref.getRef());
			}
		}
	}

	/**
	 * Content provider for the tree viewer that finds all configurations from the
	 * workspace that are not already referenced by this project configuration.
	 */
	private static class RefsContentProvider implements ITreeContentProvider {
		private LinkedHashSet<String> projects;
		private Map<String, Set<Reference>> refs;
		private Set<ICReferenceEntry> existingRefs;

		public RefsContentProvider(IProject currentProject, ICConfigurationDescription currentConfig) {
			projects = new LinkedHashSet<String>();
			refs = new HashMap<String, Set<Reference>>();
			existingRefs = new HashSet<ICReferenceEntry>();
			existingRefs.addAll(Arrays.asList(currentConfig.getReferenceEntries()));

			String currCfg = currentConfig.getId();
			for (IProject project : currentProject.getWorkspace().getRoot().getProjects()) {
				String projectName = project.getName();

				// Add active configuration
				if (!project.equals(currentProject))
					addRef(new CReferenceEntry(projectName, EMPTY_STR), ACTIVE);

				// Add named configurations
				ICProjectDescription desc = CoreModel.getDefault().getProjectDescription(project, false);
				if (desc == null)
					continue;
				ICConfigurationDescription[] projectCfgs = desc.getConfigurations();
				if (projectCfgs == null)
					continue;
				for (ICConfigurationDescription cfg : projectCfgs)
					// Don't allow the current configuration to be added
					if (!(project.equals(currentProject) && cfg.getId().equals(currCfg)))
						addRef(new CReferenceEntry(projectName, cfg.getId()), cfg.getName());
			}
		}

		/**
		 * Add a reference to the internal model for this dialog, if it is not currently
		 * referenced by the project configuration. Updates the project list with new
		 * projects, while maintaining project order. Adds references to refs if
		 * one does not already exist. References are displayed in the order that
		 * they are added.
		 */
		private void addRef(ICReferenceEntry ref, String cfgName) {
			if (existingRefs.contains(ref))
				return;
			if (!projects.contains(ref.getProject()))
				projects.add(ref.getProject());
			if (!refs.containsKey(ref.getProject()))
				refs.put(ref.getProject(), new LinkedHashSet<Reference>());
			Reference refObj = new Reference(ref, cfgName);
			refs.get(ref.getProject()).add(refObj);
		}

		public Object[] getElements(Object inputElement) {
			return projects.toArray();
		}

		/**
		 * For the given parent element, return all children.
		 * If the parent is null, the available projects are returned.
		 * If parent is an object of type String, specifying a project name,
		 * then the cfgs for that project are returned.
		 */
		public Object[] getChildren(Object parentElement) {
			if (parentElement instanceof Set)
				// Root
				return projects.toArray();
			else if (parentElement instanceof String)
				// A project
				return refs.get(parentElement).toArray();
			else
				// A configuration -- has no children
				return null;
		}

		public Object getParent(Object element) {
			if (element instanceof String)
				// A project
				return projects;
			else if (element instanceof Reference)
				// A configuration
				return ((Reference)element).getRef().getProject();
			else
				// Root -- has no parent
				return null;
		}

		public boolean hasChildren(Object element) {
			// Root (a Set) and projects (Strings) have children
			return element instanceof Set ||
				   (element instanceof String &&
					refs.containsKey(element) &&
					!refs.get(element).isEmpty());
		}

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

		public void dispose() {}
	}
}
