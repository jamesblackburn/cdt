package ResourceCfgDiscovery.ui;

import java.util.HashMap;
import java.util.regex.Pattern;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.cdt.managedbuilder.internal.core.Configuration;
import org.eclipse.cdt.ui.newui.IConfigManager;
import org.eclipse.cdt.ui.newui.ManageConfigSelector;
import org.eclipse.cdt.utils.ui.controls.ControlFactory;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbenchPropertyPage;
import org.eclipse.ui.dialogs.PropertyPage;

import ResourceCfgDiscovery.Activator;
import ResourceCfgDiscovery.binaryInfo.ExeChangedListener;

public class BinaryFilePropertyPage extends PropertyPage implements
IWorkbenchPropertyPage {

	public static final QualifiedName RESOURCE_TIED_TO_CONFIG_ID_KEY = new QualifiedName("com.broadcom.eclipse.cdt.core","BinaryFileConfigID");
	private static final String BUILD_CONFIGURATION_DEFAULT = "None";

	IResource resource = null;
	Combo buildConfigs;
	Button manageConfigsButton;
	private String targetConfigurationID = BUILD_CONFIGURATION_DEFAULT;

	public BinaryFilePropertyPage() {
		super();
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(new GridLayout(1, true));

		// Attempt to load the configuration ID from the IResource persistent properties
		final IResource res = (IResource)getElement().getAdapter(IResource.class);
		if (res != null && res.exists()) {
			resource = res;
			try {
				targetConfigurationID = res.getPersistentProperty(RESOURCE_TIED_TO_CONFIG_ID_KEY);
				if (targetConfigurationID == null)
					targetConfigurationID = BUILD_CONFIGURATION_DEFAULT;
			} catch (CoreException e) {
				// Don't care, resource will default to "None"
			}

			// Main text label
			Label l = new Label(comp, SWT.WRAP);
			l.setText("This tab is used to tie a Binary to a CDT Project Build configuration.\n\n");

			boolean enabled = CCorePlugin.getDefault().isNewStyleProject(res.getProject());
			if (enabled) {
				for (ICConfigurationDescription desc : CoreModel.getDefault().getProjectDescription(resource.getProject(), false).getConfigurations()) {
					Configuration config = (Configuration)ManagedBuildManager.getConfigurationForDescription(desc);
					enabled &= !config.isManagedBuildOn();
				}
			}

			// Managed projects, or old style projects, can't use this functionality
			if (!enabled) {
				l.setText(l.getText() + "This setting is only applicable to Standard Make Projects (those where Makefiles are managed externally to Eclipse).\n\n" +
						"This Project seems to have at least one configuration where managed build is on.");
				return comp;
			}

			l.setText("Specify the CDT Build configuration that this Binary is connected to.\n\n" +
					"If the binary is built with '-g3' the -D, -U, -I and -include switches " +
					"will be loaded into the specified configuration, populating the indexer and allowing " +
					"source navigation and code highlighting to work correctly.\n\n" +
					"NB The default project configuration will not appear in this list. You should " +
					"use the \"Manage\" button to create a new configuration for your binary");

			// Composite for build configuration combo
			Composite subComp = new Composite(comp, SWT.FILL);
			subComp.setLayout(new GridLayout(3, true));
			subComp.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL));

			// Add the ability to tie the make to an existing build configuration
			l = ControlFactory.createLabel(subComp, "Project Configuration:");
			l.setToolTipText("Select the Project Configuration into which you would like discovered Preprocessor Symbols " +
					"and Macros to be added.\n\n" +
					"As Macros and Includes may be plentiful, you can not choose to add these to the 'Default' configuration.  " +
					"You should create a new configuration using the \"Manage\" button.");

			// Add the build configurations selection
			buildConfigs = ControlFactory.createSelectCombo(subComp, new String[]{BUILD_CONFIGURATION_DEFAULT}, BUILD_CONFIGURATION_DEFAULT);
			manageConfigsButton = ControlFactory.createPushButton(subComp, "Manage...");
			manageConfigsButton.setLayoutData(new GridData());
			manageConfigsButton.setToolTipText("Managed the set of Project Configurations.\n" +
					"Use this button to add additional configurations " +
					"in which to persist Macros and Includes");
			manageConfigsButton.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					IProject[] obs = new IProject[] { res.getProject() };
					IConfigManager cm = ManageConfigSelector.getManager(obs);
					if (cm != null && cm.manage(obs, true)) {
						updateConfigurations();
					}
				}
			});
			// Load in the configurations
			updateConfigurations();
			// Select the current configuration ID
			selectConfigurationID(targetConfigurationID);
		}
		return comp;
	}

	/**
	 * Fetch the IConfigurations from the CoreModel and update the pop-up menu
	 */
	private void updateConfigurations() {
		// Fetch the available configurations from the project and populate the lsit
		if (buildConfigs == null)
			return;

		ICConfigurationDescription lastSelected =
			((ICConfigurationDescription)buildConfigs.getData(buildConfigs.getText()));

		buildConfigs.removeAll();
		buildConfigs.add(BUILD_CONFIGURATION_DEFAULT);
		for (ICConfigurationDescription cfg :
				CoreModel.getDefault().getProjectDescription(resource.getProject()).getConfigurations()) {
			// Don't allow selections of default configurations... (or there'll be pain when they create further configs)
			// Derived configurations have an additional qualifier on the end
			// FIXME is there a better way of doing this?
			if (!Pattern.matches(".*[.][0-9]+[.][0-9]+$", cfg.getId()))
				continue;
			buildConfigs.add(cfg.getName());
			buildConfigs.setData(cfg.getName(), cfg);
			// Add mapping from conifg ID as well
			buildConfigs.setData(cfg.getId(), cfg);
		}

		// Attempt to reselect the last selected configuration
		if (lastSelected != null)
			selectConfigurationID(lastSelected.getId());
		else
			buildConfigs.select(0);
	}

	private String getConfigurationID() {
		if (buildConfigs != null)
			if (!buildConfigs.getText().equals(BUILD_CONFIGURATION_DEFAULT))
				return ((ICConfigurationDescription)buildConfigs.getData(buildConfigs.getText())).getId();
		return BUILD_CONFIGURATION_DEFAULT;
	}

	/**
	 * Attempts to select the given configurationID, or index 0 on failure
	 * @param ID
	 */
	private void selectConfigurationID(String ID) {
		if (buildConfigs != null) {
			ICConfigurationDescription cfd = (ICConfigurationDescription)buildConfigs.getData(ID);
			if (cfd != null) {
				int index = buildConfigs.indexOf(cfd.getName());
				if (index != -1) {
					buildConfigs.select(index);
					return;
				}
			}
		}
		// Failed, select 'Default...'
		buildConfigs.select(0);
	}

	@Override
	public boolean performOk() {
		if (resource != null) {
			try {
				final String configID = getConfigurationID();
				if (configID.equals(BUILD_CONFIGURATION_DEFAULT))
					resource.setPersistentProperty(RESOURCE_TIED_TO_CONFIG_ID_KEY, null);
				else {
					resource.setPersistentProperty(RESOURCE_TIED_TO_CONFIG_ID_KEY, configID);
					new ExeChangedListener().scheduleUpdate(new HashMap<IFile, String>(1){{put((IFile)resource, configID);}});
				}
			} catch (CoreException e) {
				Activator.log(e);
			}
		}
		return super.performOk();
	}
}
