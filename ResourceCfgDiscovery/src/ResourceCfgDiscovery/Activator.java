package ResourceCfgDiscovery;

import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {
	private IResourceChangeListener exeFileListener;

	// The plug-in ID
	public static final String PLUGIN_ID = "ResourceConfigFetcher";

	// The shared instance
	private static Activator plugin;
	
	/**
	 * The constructor
	 */
	public Activator() {
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		
		// Register binary changed listener
		exeFileListener = new ResourceCfgDiscovery.binaryInfo.ExeChangedListener();
		ResourcesPlugin.getWorkspace().addResourceChangeListener(exeFileListener, IResourceChangeEvent.POST_CHANGE);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(exeFileListener);
		plugin = null;
		super.stop(context);
	}
	
	public static void log(IStatus status) {
		plugin.getLog().log(status);
	}

	public static void log(CoreException e) {
		e.printStackTrace();
		log(e.getStatus());
	}

	public static void log(Exception e) {
		e.printStackTrace();
		log(new Status(Status.ERROR, PLUGIN_ID, 1, e.getMessage(), e));
	}

	public static void log(String error) {
		log(new Status(Status.ERROR, PLUGIN_ID, 1, error, new Exception()));
	}

	public static void info(String message) {
		log(new Status(Status.INFO, PLUGIN_ID, 1, message, null));
	}


	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}

}
