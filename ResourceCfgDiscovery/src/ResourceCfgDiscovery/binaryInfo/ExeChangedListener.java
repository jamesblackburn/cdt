package ResourceCfgDiscovery.binaryInfo;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.core.runtime.jobs.Job;

import ResourceCfgDiscovery.Activator;

public class ExeChangedListener implements IResourceChangeListener {

	private static final ILock lock = Job.getJobManager().newLock();

	/**
	 * Create the Exe change listener
	 * @param project
	 */
	public ExeChangedListener() {
	}

	public void resourceChanged(IResourceChangeEvent event) {
		try {
			// Map of IFile to configuration ID
			final Map<IFile, String> toUpdate = new HashMap<IFile, String>();
			event.getDelta().accept(new IResourceDeltaVisitor() {
				public boolean visit(IResourceDelta delta) throws CoreException {
					if (delta.getKind() == IResourceDelta.CHANGED) {
						IResource res = delta.getResource();

						if (res.getType() != IResource.ROOT) { // Only interested in Projects...
							// If not New Style cdt project, don't descend
							if (CCorePlugin.getDefault().getProjectDescription(res.getProject(), false) == null ||
									!CCorePlugin.getDefault().isNewStyleProject(res.getProject()))
								return false;

							Map<String, String> binaresToConfigMap = DwarfSettingsProvider.getBinariesConfigMap(res.getProject());

							for (Map.Entry<String, String> e : binaresToConfigMap.entrySet()) {
								IPath binProjRelPath = new Path(e.getKey());
								if (res.getProjectRelativePath().isPrefixOf(binProjRelPath)) {
									IResourceDelta d = delta.findMember(binProjRelPath.removeFirstSegments(res.getProjectRelativePath().segmentCount()));
									if (d != null)
										toUpdate.put((IFile)d.getResource(), e.getValue());
								}
							}
							// No more descent, already searched
							return false;
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
