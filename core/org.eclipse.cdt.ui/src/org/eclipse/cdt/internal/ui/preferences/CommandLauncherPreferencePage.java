/*******************************************************************************
 * Copyright (c) 2009 Broadcom Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     James Blackburn (Broadcom Corp.) - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.ui.preferences;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.CommandLauncherFactory;
import org.eclipse.cdt.ui.CUIPlugin;
import org.eclipse.cdt.ui.dialogs.AbstractCommandLauncherDialog;

import org.eclipse.cdt.internal.ui.dialogs.IStatusChangeListener;
import org.eclipse.cdt.internal.ui.dialogs.StatusUtil;
import org.eclipse.cdt.internal.ui.preferences.OptionsConfigurationBlock.Key;

/**
 * A page which allows users to customize the Command Launcher that's
 * used for running launches, builds and other external tools
 * @since 5.2
 */
public class CommandLauncherPreferencePage extends PropertyAndPreferencePage implements IStatusChangeListener {

	public static final String PREF_ID= "org.eclipse.cdt.ui.preferences.CommandLauncherPreferencePage"; //$NON-NLS-1$
	public static final String PROP_ID= "org.eclipse.cdt.ui.propertyPages.CommandLauncherPreferencePage"; //$NON-NLS-1$

	/**
	 * Displays the mapping between Process Types and selected Command Launcher
	 *
	 * Provides "Advance..." button to allow users to configure the selected Command Launcher
	 */
	private static class CommandLauncherConfigBlock extends OptionsConfigurationBlock {

		/** Map from ProcessType Key Id -> Preference {@link Key} */
		private final Map<String, Key> preferenceMap;

		// Widget listener to update view enablement
		SelectionListener customListener;

		public CommandLauncherConfigBlock(IStatusChangeListener context,
				IProject project,
				Map<String, Key> procTypeKeyMap,
				IWorkbenchPreferenceContainer container) {
			super(context, project, procTypeKeyMap.values().toArray(new Key[procTypeKeyMap.size()]), true, container);
			preferenceMap = procTypeKeyMap;
		}

		@Override
		protected Control createContents(Composite parent) {
			Composite composite= new Composite(parent, SWT.NONE);
			composite.setLayout(new GridLayout());

			String label;
			Button advanced;

			final Group globalGroup = new Group(composite, SWT.NONE);
			globalGroup.setLayout(new GridLayout(4, false));
			globalGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			// Checkbox for specifying custom vs global Command Launcher
			label = CommandLauncherFactory.getGlobalCommandLaunchIdNames().get(CommandLauncherFactory.USE_CUSTOM_PROCESS_TYPES);
			final Button customCheckbox = addCheckBox(composite, label, preferenceMap.get(CommandLauncherFactory.USE_CUSTOM_PROCESS_TYPES),
					new String[]{IPreferenceStore.TRUE, IPreferenceStore.FALSE}, 0);

			// Add the per processType CommandLauncher grouping
			final Group customGroup = new Group(composite, SWT.SHADOW_ETCHED_IN);
			customGroup.setText(PreferencesMessages.CommandLauncherPreferencePage_CustomCommandLaunchers);
			customGroup.setLayout(new GridLayout(4, false));
			customGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			// Widget Enablement
			// Custom checkbox event handler: Custom enabled disables the global CommandLauncher & enables the custom group
			//                                Custom disabled does the opposite
			// Enables the "Advanced..." button based on selection
			customListener = new SelectionAdapter() {
				/**
				 * Update the enablement on the elements in a Group
				 * @param g
				 */
				private void enableGroup(boolean enabled, Group g) {
					g.setEnabled(enabled);
					for (Control child : g.getChildren()) {
						if (enabled && child instanceof Button) {
							// The Advanced... button is only enabled when the CommandLauncher specified by the
							// combo supports advanced settings
							Combo ourCombo = (Combo)child.getData();
							((Button)child).setEnabled(CommandLauncherAdvancedDialogFactory.hasAdvancedConfiguration(
									((ControlData)ourCombo.getData()).getValue(ourCombo.getSelectionIndex())));
						}
						else
							child.setEnabled(enabled);
					}
				}

				@Override
				public void widgetSelected(SelectionEvent e) {
					boolean custom = customCheckbox.getSelection();
					// Global group enablement
					enableGroup(!custom, globalGroup);
					// Custom group enablement
					enableGroup(custom, customGroup);
				}
			};

			// Class to open and show the advanced dialog
			class ComboSelectionAdapter extends SelectionAdapter {
				protected void showAdvanced(Combo combo) {
					// Show the contributors "Advanced..." dialog
					final String commandLauncherID = ((ControlData)combo.getData()).getValue(combo.getSelectionIndex());
					AbstractCommandLauncherDialog acld = CommandLauncherAdvancedDialogFactory.getAdvancedConfigurationDialog(commandLauncherID);
					if (acld != null) {
						// Get the Working Copy preferences for the advanced dialog
						String processTypeID = ((ControlData)combo.getData()).getKey().getName();
						processTypeID = processTypeID.substring(processTypeID.lastIndexOf(IPath.SEPARATOR) + 1);

						// Wrap the preferences & set it on the Advanced Dialog
						acld.setPreferencesStore(getAdvancedPreferenceNode(commandLauncherID, processTypeID));
						// Set the defaults
						acld.setPreferenceContext(fProject, commandLauncherID, processTypeID);

						// Show the dialog
						acld.setParentShell(getShell());
						acld.open();
					}
				}
			}

			// Add a global CommandLauncher used for all process types by default.
			Map<String, String> commandLaunchers = CommandLauncherFactory.getSupportedCommandLaunchers(null);
			{
				label = CommandLauncherFactory.getGlobalCommandLaunchIdNames().get(CommandLauncherFactory.PROCESS_TYPE_ALL);
				final Combo combo = addComboBox(globalGroup, label, preferenceMap.get(CommandLauncherFactory.PROCESS_TYPE_ALL),
						commandLaunchers.values().toArray(new String[commandLaunchers.size()]),
						commandLaunchers.keySet().toArray(new String[commandLaunchers.size()]), 0);
				advanced = new Button(globalGroup, SWT.PUSH);
				advanced.setData(combo); // The button's data is the combo used to calculate it's enablement
				advanced.setText(PreferencesMessages.CommandLauncherPreferencePage_Advanced);
				advanced.addSelectionListener(new ComboSelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						showAdvanced(combo);
					}
				});
				combo.addSelectionListener(customListener);
			}

			// Add the per ProcessType Command Launchers
			for (Map.Entry<String, String> entry : CommandLauncherFactory.getPerProcessTypeCommandIdNames().entrySet()) {
				commandLaunchers = CommandLauncherFactory.getSupportedCommandLaunchers(entry.getKey());
				final Combo combo = addComboBox(customGroup, entry.getValue(), preferenceMap.get(entry.getKey()),
									commandLaunchers.values().toArray(new String[commandLaunchers.size()]),
									commandLaunchers.keySet().toArray(new String[commandLaunchers.size()]), 0);
				// Add an "Advanced" button for configuring Launcher specific features
				advanced = new Button(customGroup, SWT.PUSH);
				advanced.setData(combo); // The button's data is the combo used to calculate it's enablement
				advanced.setText(PreferencesMessages.CommandLauncherPreferencePage_Advanced);
				advanced.addSelectionListener(new ComboSelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						showAdvanced(combo);
					}
				});
				combo.addSelectionListener(customListener);
			}

			// Add listener on the "Custom..." checkbox and toggle enablement
			customCheckbox.addSelectionListener(customListener);
			customListener.widgetSelected(null); // Update enablement on the view
			return composite;
		}

		/**
		 * Return wrapped preference node for use by the advanced dialog
		 * @param commandLauncherId
		 * @param processTypeId
		 * @return
		 */
		private Preferences getAdvancedPreferenceNode(String commandLauncherId, String processTypeId) {
			Preferences p = CommandLauncherFactory.getAdvancedCommandLauncherPreferences(fProject, commandLauncherId, processTypeId);
			IEclipsePreferences ecPrefs;
			if (fProject != null)
				ecPrefs = new ProjectScope(fProject).getNode(CCorePlugin.PLUGIN_ID);
			else
				ecPrefs = new InstanceScope().getNode(CCorePlugin.PLUGIN_ID);
			// Wrap the preferences
			return getPreferenceContainer().getWorkingCopyManager().getWorkingCopy(ecPrefs).node(p.absolutePath());
		}

		@Override
		public void performDefaults() {
			// Reset the Advanced preference nodes for all command launchers and associated processTypes
			// NB this only works for keys
			for (Combo combo : fComboBoxes) {
				for (int i = 0; i < combo.getItemCount(); i++) {
					final String commandLauncherID = ((ControlData)combo.getData()).getValue(i);
					AbstractCommandLauncherDialog acld = CommandLauncherAdvancedDialogFactory.getAdvancedConfigurationDialog(commandLauncherID);
					if (acld != null) {
						// Get the Working Copy preferences for the advanced dialog
						String processTypeID = ((ControlData)combo.getData()).getKey().getName();
						processTypeID = processTypeID.substring(processTypeID.lastIndexOf(IPath.SEPARATOR) + 1);
						// Remove the preference node (restore to defaults...)
						try {
							Preferences p = getAdvancedPreferenceNode(commandLauncherID, processTypeID);
							// Remove Advanced scope defined keys
							for (String key : p.keys())
								p.remove(key);
							Preferences inherited = CommandLauncherFactory.getDefaultAdvancedCommandLauncherPreferences(fProject, commandLauncherID, processTypeID);
							for (String key : inherited.keys())
								p.put(key, inherited.get(key, "")); //$NON-NLS-1$
						} catch (BackingStoreException e) {
							CUIPlugin.log(e);
						}
					}
				}
			}
			// Parent's perform defaults
			super.performDefaults();
			// Ensure the widgets have the right enablement
			if (fProject == null || hasProjectSpecificOptions(fProject))
				customListener.widgetSelected(null);
		}

		@Override
		protected void validateSettings(Key changedKey, String oldValue,
				String newValue) {
			// No validation needed
		}

	}

	/**
	 * Helper method to get the processType -> Key mappings for the project
	 * @param project
	 * @return
	 */
	private static Map<String, Key> getKeys(IProject project) {
		// Defaults (may be workspace defaults if we're at project scope)
		final Map<String, String> defaults = CommandLauncherFactory.getDefaultPreferenceProcessTypeMappings(project);

		final Map<String, Key> preferenceMap = new HashMap<String, Key>();
		for (String id : CommandLauncherFactory.getPerProcessTypeCommandIdNames().keySet())
			preferenceMap.put(id, new Key(CommandLauncherFactory.PREFS_BASE, id, defaults.get(id)));
		for (String id : CommandLauncherFactory.getGlobalCommandLaunchIdNames().keySet())
			preferenceMap.put(id, new Key(CommandLauncherFactory.PREFS_BASE, id, defaults.get(id)));
		return preferenceMap;
	}

	private OptionsConfigurationBlock fConfigBlock;

	public CommandLauncherPreferencePage() {
		setTitle(PreferencesMessages.CommandLauncherPreferencePage_CommandLauncher);
		setDescription(PreferencesMessages.CommandLauncherPreferencePage_CustomiseCommandRunning);
	}

	@Override
	protected Control createPreferenceContent(Composite composite) {
		fConfigBlock = new CommandLauncherConfigBlock(this, getProject(), getKeys(getProject()), (IWorkbenchPreferenceContainer)getContainer());
		return fConfigBlock.createContents(composite);
	}

	@Override
	protected String getPreferencePageID() {
		return PREF_ID;
	}

	@Override
	protected String getPropertyPageID() {
		return PROP_ID;
	}

	@Override
	protected void performApply() {
		fConfigBlock.performApply();
		super.performApply();
	}

	@Override
	protected void performDefaults() {
		fConfigBlock.performDefaults();
		super.performDefaults();
	}

	@Override
	protected boolean hasProjectSpecificOptions(IProject project) {
		return CommandLauncherFactory.hasProjectScopedCommandLaunchPreferences(project);
	}

	@Override
	protected void enableProjectSpecificSettings(boolean useProjectSpecificSettings) {
		super.enableProjectSpecificSettings(useProjectSpecificSettings);
		if (fConfigBlock != null)
			fConfigBlock.useProjectSpecificSettings(useProjectSpecificSettings);
	}

	public void statusChanged(IStatus status) {
		setValid(!status.matches(IStatus.ERROR));
		StatusUtil.applyToStatusLine(this, status);
	}

}
