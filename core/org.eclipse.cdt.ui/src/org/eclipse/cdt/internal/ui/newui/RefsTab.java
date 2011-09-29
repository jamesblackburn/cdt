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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.CReferenceEntry;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.settings.model.ICReferenceEntry;
import org.eclipse.cdt.core.settings.model.ICResourceDescription;
import org.eclipse.cdt.core.settings.model.WriteAccessException;
import org.eclipse.cdt.ui.newui.AbstractCPropertyTab;
import org.eclipse.cdt.ui.newui.ICPropertyProvider;

/**
 * Tab for managing the configurations that a project configuration references.
 * @noextend This class is not intended to be subclassed by clients.
 */
public class RefsTab extends AbstractCPropertyTab {
	private TableViewer table;
	private RefsContentProvider contentProvider;

	static private final String ACTIVE = "[" + Messages.RefsTab_Active + "]"; //$NON-NLS-1$ //$NON-NLS-2$

	private static final int ADD_BUTTON = 0;
	private static final int DELETE_BUTTON = 1;
	private static final int MOVEUP_BUTTON = 3;
	private static final int MOVEDOWN_BUTTON = 4;

	@Override
	public void createControls(Composite parent) {
		super.createControls(parent);
		initButtons(new String[] {
				ADD_STR,
				DEL_STR,
				null,
				MOVEUP_STR,
				MOVEDOWN_STR
			}, 120);
		usercomp.setLayout(new GridLayout(1, false));

		contentProvider = new RefsContentProvider();
		table = new TableViewer(usercomp, SWT.MULTI | SWT.BORDER);
		table.setContentProvider(contentProvider);
		table.getTable().setLayoutData(new GridData(GridData.FILL_BOTH));
		table.getTable().setHeaderVisible(true);

		TableViewerColumn column1 = new TableViewerColumn(table, SWT.NONE);
		column1.getColumn().setText(Messages.RefsTab_Project);
		column1.getColumn().setWidth(150);
		column1.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((Reference)element).getRef().getProject();
			}
		});

		TableViewerColumn column2 = new TableViewerColumn(table, SWT.NONE);
		column2.getColumn().setText(Messages.RefsTab_Configuration);
		column2.getColumn().setWidth(150);
		column2.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				return ((Reference)element).getCfgName();
			}
		});

		table.getTable().getAccessible().addAccessibleListener(
				new AccessibleAdapter() {
					@Override
					public void getName(AccessibleEvent e) {
						e.result = Messages.RefsTab_ProjectsList;
					}
				}
			);

		table.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				updateButtons();
			}
		});

		initData();
	}

	@Override
	public void buttonPressed(int n) {
		switch (n) {
			case MOVEUP_BUTTON:
			case MOVEDOWN_BUTTON:
				IStructuredSelection selection = (IStructuredSelection)table.getSelection();
				Object item = selection.getFirstElement();
				if (n == MOVEUP_BUTTON)
					contentProvider.moveUp(item, selection.size());
				else
					contentProvider.moveDown(item, selection.size());
				save(contentProvider.getReferences());
				initData();
				break;
			case ADD_BUTTON:
				RefsDialog dialog = new RefsDialog(usercomp.getShell(), page.getProject(), page.getResDesc().getConfiguration());
				if (dialog.open() == Window.OK) {
					List<ICReferenceEntry> refs = contentProvider.getReferences();
					refs.addAll(dialog.getNewReferences());
					save(refs);
					initData();
				}
				break;
			case DELETE_BUTTON:
				// Find the set difference between the existing references and the selected references
				LinkedHashSet<ICReferenceEntry> selected = getSelectedReferences();
				if (!selected.isEmpty()) {
					int position = contentProvider.getPosition(((IStructuredSelection)table.getSelection()).iterator().next());
					List<ICReferenceEntry> refs = new ArrayList<ICReferenceEntry>();
					for (ICReferenceEntry ref : contentProvider.getReferences())
						if (!selected.contains(ref))
							refs.add(ref);

					save(refs);
					initData();

					//Select the reference that is now in the position of the first item that was selected
					table.getTable().setSelection(new int[]{position});
					updateDeleteButton();
				}
				break;
		}
	}

	private LinkedHashSet<ICReferenceEntry> getSelectedReferences() {
		IStructuredSelection selection = (IStructuredSelection)table.getSelection();
		LinkedHashSet<ICReferenceEntry> selected = new LinkedHashSet<ICReferenceEntry>();
		for (Reference ref : (List<Reference>) selection.toList())
			selected.add(ref.getRef());
		return selected;
	}

	@Override
	protected void updateData(ICResourceDescription cfgd) {
		if (page.isMultiCfg()) {
			setAllVisible(false, null);
		} else {
			if (!usercomp.isVisible())
				setAllVisible(true, null);
			initData();
		}
	}

	/** Persist the referenced configurations */
	private void save(List<ICReferenceEntry> refs) {
		try {
			getResDesc().getConfiguration().setReferenceEntries(refs.toArray(new ICReferenceEntry[refs.size()]));
		} catch (WriteAccessException e) {
		} catch (CoreException e) {
		}
	}

	private void initData() {
		// Persist the current selection state so that it can be restored
		IStructuredSelection currentSelection = (IStructuredSelection)table.getSelection();

		// Remove all items from the table
		table.setInput(null);

		IProject p = page.getProject();
		if (p == null)
			return;
		table.setInput(page);

		// Restore the selection state
		table.setSelection(currentSelection, true);

		updateButtons();
		table.refresh();
	}

	@Override
	protected void performApply(ICResourceDescription src, ICResourceDescription dst) {
		dst.getConfiguration().setReferenceInfo(src.getConfiguration().getReferenceInfo());
		try {
			dst.getConfiguration().setReferenceEntries(src.getConfiguration().getReferenceEntries());
		} catch (CoreException e) {
		}
	}

	// This tab can be displayed for a project only
	@Override
	public boolean canBeVisible() {
		return page.isForProject();
	}

	@Override
	protected void performDefaults() {
		if (!usercomp.isVisible())
			return;
		getResDesc().getConfiguration().setReferenceInfo(new HashMap<String, String>());
		try {
			getResDesc().getConfiguration().setReferenceEntries(new ICReferenceEntry[0]);
		} catch (Exception e) {
		}
		initData();
	}

	@Override
	public void dispose() {
		super.dispose();
	}

	@Override
	protected void updateButtons() {
		updateMoveButtons();
		updateDeleteButton();
	}

	/**
	 * Enable the move buttons when one or more references are selected,
	 * and if more than one is selected check that they are contiguous
	 */
	private void updateMoveButtons() {
		IStructuredSelection selection = (IStructuredSelection)table.getSelection();
		if (selection.size() > 0 && contentProvider.areReferencesContiguous(selection.toList())) {
			Object first = selection.getFirstElement();
			Object last = selection.toList().get(selection.size()-1);
			int firstIndex = contentProvider.getPosition(first);
			int lastIndex = contentProvider.getPosition(last);
			buttonSetEnabled(MOVEUP_BUTTON, 0 < firstIndex);
			buttonSetEnabled(MOVEDOWN_BUTTON, lastIndex < contentProvider.getElements(null).length - 1);
			return;
		}
		buttonSetEnabled(MOVEUP_BUTTON, false);
		buttonSetEnabled(MOVEDOWN_BUTTON, false);
	}

	/** Enable the delete button when at least one reference is selected */
	private void updateDeleteButton() {
		IStructuredSelection selection = (IStructuredSelection)table.getSelection();
		buttonSetEnabled(DELETE_BUTTON, selection.size() > 0);
	}

	/** Content provider that populates the list of referenced configurations */
	private static class RefsContentProvider implements IStructuredContentProvider {
		private LinkedHashSet<Reference> refs;

		private void initData(ICPropertyProvider page) {
			refs = new LinkedHashSet<Reference>();

			// Get all the enabled references (preserving order), including those not
			// in the workspace but still referenced.
			for (ICReferenceEntry ref: page.getResDesc().getConfiguration().getReferenceEntries()) {
				String cfgName = ref.getConfiguration() == null || ref.getConfiguration().equals(EMPTY_STR) ? ACTIVE : ref.getConfiguration();
				IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(ref.getProject());
				if (project != null) {
					ICProjectDescription desc = CoreModel.getDefault().getProjectDescription(project, false);
					if (desc != null) {
						ICConfigurationDescription cfg = desc.getConfigurationById(ref.getConfiguration());
						if (cfg != null)
							cfgName = cfg.getName();
					}
				}
				refs.add(new Reference(ref, cfgName));
			}

			// Get all enabled references using old API, to ensure .cproject file backwards compatibility
			Map<String, String> refInfo = page.getResDesc().getConfiguration().getReferenceInfo();
			for (Entry<String, String> entry: refInfo.entrySet()) {
				String projectName = entry.getKey();
				String cfgId = entry.getValue();
				String cfgName = cfgId.equals(EMPTY_STR) ? ACTIVE : cfgId;
				IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
				if (project != null) {
					ICProjectDescription desc = CoreModel.getDefault().getProjectDescription(project, false);
					if (desc != null) {
						ICConfigurationDescription cfg = desc.getConfigurationById(cfgId);
						if (cfg != null)
							cfgName = cfg.getName();
					}
				}
				refs.add(new Reference(new CReferenceEntry(projectName, cfgId), cfgName));
			}
		}

		/** @return true if the given list of references are contiguous in the table */
		public boolean areReferencesContiguous(List<Reference> list) {
			List<Reference> refsList = new ArrayList<Reference>(refs.size());
			refsList.addAll(refs);
			return Collections.indexOfSubList(refsList, list) != -1;
		}

		/** Move num references starting at item down by one */
		public void moveDown(Object item, int num) {
			int pos = getPosition(item);
			move(pos, pos + num - 1, true);
		}

		/** Move num references starting at item up by one */
		public void moveUp(Object item, int num) {
			int pos = getPosition(item);
			move(pos, pos + num - 1, false);
		}

		/**
		 * Move the references starting at position first up to and including the
		 * item at position last
		 */
		private void move(int first, int last, boolean down) {
			Assert.isTrue(first <= last);
			List<Reference> temp = new LinkedList<Reference>();
			temp.addAll(refs);
			if (down) {
				Reference ref = temp.remove(last+1);
				temp.add(first, ref);
			} else {
				Reference ref = temp.remove(first-1);
				temp.add(last, ref);
			}
			refs = new LinkedHashSet<Reference>(refs.size());
			refs.addAll(temp);
		}

		/** Get the position of the given item in the list */
		public int getPosition(Object item) {
			int index = 0;
			Iterator<Reference> i = refs.iterator();
			while (i.hasNext()) {
				if (i.next().equals(item))
					return index;
				index++;
			}
			return -1;
		}

		/** Get the contents of the references table as a ICReferenceEntry[] */
		public List<ICReferenceEntry> getReferences() {
			List<ICReferenceEntry> refArray = new ArrayList<ICReferenceEntry>();
			for (Reference ref : refs)
				refArray.add(ref.getRef());
			return refArray;
		}

		public Object[] getElements(Object element) {
			return refs.toArray();
		}

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			if (newInput instanceof ICPropertyProvider)
				initData((ICPropertyProvider)newInput);
			else {
				refs = new LinkedHashSet<Reference>();
			}
		}

		public void dispose() {}
	}
}
