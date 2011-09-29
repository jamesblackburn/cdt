package ResourceCfgDiscovery.binaryInfo;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.cdt.build.core.scannerconfig.CfgInfoContext;
import org.eclipse.cdt.build.core.scannerconfig.ICfgScannerConfigBuilderInfo2Set;
import org.eclipse.cdt.build.internal.core.scannerconfig2.CfgScannerConfigProfileManager;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.make.core.scannerconfig.IScannerConfigBuilderInfo2;
import org.eclipse.cdt.make.core.scannerconfig.IScannerConfigBuilderInfo2Set;
import org.eclipse.cdt.make.core.scannerconfig.InfoContext;
import org.eclipse.cdt.make.internal.core.scannerconfig2.ScannerConfigProfileManager;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.cdt.managedbuilder.internal.core.Configuration;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

/**
 * Override DwarfBasedSettingsProvider to provide paths of directories in the source tree
 * when we're contributing to directories in the build tree
 */
public class DwarfSettingsProvider extends DwarfBasedSettingsProvider {

	/**
	 * Ensure that scanner discovery is set to per configuration
	 * (to prevent an explosive growth in the scanner discovery
	 * storageModule) and disabled -- as we'll be providing correct
	 * paths via this process, and scanner discovery only slows things
	 * down
	 * @param project
	 */
	public DwarfSettingsProvider(IFile binary, String cfgId) {
		super (binary, cfgId);
		IProject project = binary.getProject();
		try {
			ICConfigurationDescription desc = CoreModel.getDefault().getProjectDescription(project).getConfigurationById(cfgId);
			Configuration config = (Configuration)ManagedBuildManager.getConfigurationForDescription(desc);
			// Ensure Configuration wide scanner rather than per File
			config.setPerRcTypeDiscovery(false);

			// Disable scanner discovery for the per language
			IScannerConfigBuilderInfo2Set scannerSet = ScannerConfigProfileManager.createScannerConfigBuildInfo2Set(project);
			for (InfoContext context : scannerSet.getContexts())
				scannerSet.getInfo(context).setAutoDiscoveryEnabled(false);
			scannerSet.save();

			// And for all the configurations
			ICfgScannerConfigBuilderInfo2Set cfgScannerSet = CfgScannerConfigProfileManager.getCfgScannerConfigBuildInfo(config);
			Set es = cfgScannerSet.getInfoMap().entrySet();
			Map<CfgInfoContext, IScannerConfigBuilderInfo2> oldcfgScannerSet = (HashMap)((HashMap)cfgScannerSet.getInfoMap()).clone();
			for (Map.Entry<CfgInfoContext, IScannerConfigBuilderInfo2> e : oldcfgScannerSet.entrySet()) {
				e.getValue().setAutoDiscoveryEnabled(false);
				cfgScannerSet.applyInfo(e.getKey(), e.getValue());
			}

			CoreModel.getDefault().setProjectDescription(project, desc.getProjectDescription());
		} catch (CoreException e) {
			reportError(e.toString());
		}
	}

	@Override
	protected IPath[] getRelevantPaths(IProject project, IPath originalPath) {
		return super.getRelevantPaths(project, originalPath);
	}
}
