package ResourceCfgDiscovery;

import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.settings.model.ICSettingsStorage;
import org.eclipse.cdt.core.settings.model.ICStorageElement;
import org.eclipse.core.resources.IProject;
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
	 * Returns project persisted data
	 * @param project The Project to fetch the keys from
	 * @param cfgID the configuration ID, or null if a global setting
	 * @param key
	 * @return String[] if key found, or null if not found
	 */
	public static String[] getProjectData(IProject project, String cfgID, String key) throws CoreException {
		ICProjectDescription prjDesc = CoreModel.getDefault().getProjectDescription(project, false);
		ICSettingsStorage storage = prjDesc;
		if (cfgID != null) {
			ICConfigurationDescription desc = prjDesc.getConfigurationById(cfgID);
			if (desc != null)
				storage = desc;
			else
				throwCoreException("ConfigID " + cfgID + " not found!");
		}
		ICStorageElement store = storage.getStorage(PLUGIN_ID + ".store", false);
		if (store == null)
			return null;

		// theElement is the Element corresponding to 'key' that we're interested in
		ICStorageElement theElement = null;
		for (ICStorageElement child : store.getChildren())
			if (key.equals(child.getName())) {
				theElement = child;
				break;
			}

		if (theElement == null)
			return null;

		ICStorageElement[] children = theElement.getChildren();
		String[] retVal = new String[children.length];
		for (int i = 0; i < children.length; ++i)
			retVal[i] = children[i].getAttribute("value");
		return retVal;
	}
	
	/**
	 * Persists some data with the project under a particular key.
	 * @param cfgID - String ICConfigurationDesc ID or null for project wide
	 * @param key - key for the data
	 * @param values - one or more String values
	 * @throws CoreException if cfgID is not null and not found in the project
	 */
	public static void setProjectData(IProject project, String cfgID, String key, String[] values) throws CoreException {
		ICProjectDescription prjDesc = CoreModel.getDefault().getProjectDescription(project);
		ICSettingsStorage storage = prjDesc;
		if (cfgID != null) {
			ICConfigurationDescription desc = prjDesc.getConfigurationById(cfgID);
			if (desc != null)
				storage = desc;
			else
				throwCoreException("ConfigID " + cfgID + " not found!");
		}
		ICStorageElement store = storage.getStorage(PLUGIN_ID + ".store", true);

		// About to re-add 'key' to the store. Remove the original if it already exists
		for (ICStorageElement child : store.getChildren())
			if (key.equals(child.getName())) {
				store.removeChild(child);
				break;
			}

		// theElement is the Element corresponding to 'key' that we're interested in
		ICStorageElement theElement = store.createChild(key);

		for (String val : values)
			theElement.createChild(key).setAttribute("value", val);

		CoreModel.getDefault().setProjectDescription(project, prjDesc);
	}

	public static void throwCoreException(String message, Exception e) throws CoreException {
		throw new CoreException(new Status(Status.ERROR, PLUGIN_ID, 1, message, e));
	}

	public static void throwCoreException(Exception e) throws CoreException {
		throw new CoreException(new Status(Status.ERROR, PLUGIN_ID, 1, e.getMessage(), e));
	}

	public static void throwCoreException(String string) throws CoreException {
		throw new CoreException(new Status(Status.ERROR, PLUGIN_ID, 1, string, new Exception()));
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
