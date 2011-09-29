package ResourceCfgDiscovery.binaryInfo;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.core.runtime.jobs.Job;

import ResourceCfgDiscovery.Activator;
import ResourceCfgDiscovery.ui.BinaryFilePropertyPage;

public class ExeChangedListener implements IResourceChangeListener {

	private static final ILock lock = Job.getJobManager().newLock();

	public void resourceChanged(IResourceChangeEvent event) {
		try {
			final Map<IFile, String> toUpdate = new HashMap<IFile, String>();
			event.getDelta().accept(new IResourceDeltaVisitor() {
				public boolean visit(IResourceDelta delta) throws CoreException {
					if (delta.getKind() == IResourceDelta.CHANGED) {
						IResource res = delta.getResource();
						if (res.getType() != IResource.ROOT) {
							// If not New Style cdt project, don't descend
							if (CCorePlugin.getDefault().getProjectDescription(res.getProject()) == null ||
									!CCorePlugin.getDefault().isNewStyleProject(res.getProject()))
								return false;
							// If any of the configurations have managed build on, don't descend
							for (IConfiguration config : ManagedBuildManager.getBuildInfo(res.getProject()).getManagedProject().getConfigurations())
								if (config.isManagedBuildOn())
									return false;

							// Look for the Configuration type flag
							if (res.getType() == IResource.FILE) {
								String cfgID = res.getPersistentProperty(BinaryFilePropertyPage.RESOURCE_TIED_TO_CONFIG_ID_KEY);
								if (cfgID != null) {
									ICConfigurationDescription cfg = CCorePlugin.getDefault().getProjectDescription(res.getProject()).getConfigurationById(cfgID);
									toUpdate.put((IFile)res, cfg.getId());
								}
							}
						}
					}
					return true;
				}
			});
			scheduleUpdate(toUpdate);
		} catch (CoreException e) {
			Activator.log(e);
		}
	}

	/**
	 * Update the given configuration with the specified binary
	 * @param filesToUpdate
	 */
	public void scheduleUpdate(final Map<IFile, String> filesToUpdate) {
		if (filesToUpdate.isEmpty())
			return;
		String name = "";
		for (IFile f : filesToUpdate.keySet())
			name += f.getName() + ", ";

		Job j = new Job("Updating Includes/Macros from: " + name.substring(0, name.length()-2)) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					// FIXME: Currently only one of these going at once... Just use scheduling rules on the given binary?
					if (!lock.acquire(0))
						return Status.OK_STATUS;
				} catch (InterruptedException e) {
					return Status.OK_STATUS;
				}

				try {
					for (Map.Entry<IFile, String> e : filesToUpdate.entrySet())
						if (!monitor.isCanceled())
							new DwarfSettingsProvider(e.getKey(), e.getValue()).updateSettings();
				} finally {
					lock.release();
				}
				return Status.OK_STATUS;
			}
		};
		j.setPriority(Job.LONG);
		j.schedule();
	}

}
