package org.eclipse.cdt.buildconfig.txt;

import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.CProjectDescriptionEvent;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class Activator extends AbstractUIPlugin {

	private static BundleContext context;
	private static volatile Activator instance;

	/** Automatic build settings exporter listening for configuration changes */
	private AutoExportBuildConfigListener settingsExporter;

	static BundleContext getContext() {
		return context;
	}
	
	public Activator() {
		instance = this;
	}

	public static Activator getInstance() {
		return instance;
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext bundleContext) throws Exception {
		Activator.context = bundleContext;
		// Add a listener for configuration changese to automatically update the exported build configuration
		settingsExporter = new AutoExportBuildConfigListener();
		CoreModel.getDefault().getProjectDescriptionManager().addCProjectDescriptionListener(settingsExporter,
				CProjectDescriptionEvent.APPLIED);
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		if (settingsExporter != null) {
			CoreModel.getDefault().getProjectDescriptionManager().removeCProjectDescriptionListener(settingsExporter);
			settingsExporter = null;
		}

		Activator.context = null;
	}

	public static void log(IStatus status) {
		getInstance().getLog().log(status);
	}

	public static void log(CoreException e) {
		log(e.getStatus());
	}

	public static void log(Exception e) {
		log(new Status(Status.ERROR, "buildconfig.txt", 1, e.getMessage(), e));
	}

	public static void log(String error) {
		log(new Status(Status.ERROR, "buildconfig.txt", 1, error, null));
	}

	public static void info(String message) {
		log(new Status(Status.INFO, "buildconfig.txt", 1, message, null));
	}

}
