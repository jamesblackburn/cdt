package ResourceCfgDiscovery.binaryInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.settings.model.ICSettingEntry;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.cdt.managedbuilder.internal.core.Configuration;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

import ResourceCfgDiscovery.Activator;

/**
 * Override DwarfBasedSettingsProvider to:
 *
 *  - Map paths in the build tree to paths in the source tree
 *  - Override which resources settings are persisted to
 *  - Add static methods for creating and persisting the mapping
 *    between a Binary and a resource configuration.
 */
public class DwarfSettingsProvider extends DwarfBasedSettingsProvider {

	private static final String DwarfBinaryKey = "DwarfBinaryProjectSettingProvider";

	/**
	 * Returns a set of Binaries -> CfgID that are mapped for the given
	 * Project. Binary paths are project relative
	 * @param project
	 * @return Map of String Binary to String Config ID
	 */
	public static Map<String,String> getBinariesConfigMap(IProject project) {
		Map<String, String> binsToConfigs = new HashMap<String,String>();
		try {
			ICProjectDescription desc = CoreModel.getDefault().getProjectDescription(project, false);
			ICConfigurationDescription[] configs = desc.getConfigurations();

			for (ICConfigurationDescription config : configs) {
				String[] binaries = Activator.getProjectData(project, config.getId(), DwarfBinaryKey);
				if (binaries != null)
					for (String binary : binaries)
						binsToConfigs.put(binary, config.getId());
			}
		} catch (CoreException e) {
			Activator.log(e);
		}
		return binsToConfigs;
	}

	/**
	 * Maps a binary to a particular configuration description.
	 * @param project - Project
	 * @param binary - Binary IFile to be mapped
	 * @param cfgID - String cfgID or null to unset
	 */
	public static void setBinaryToConfigMapping (IProject project, IFile binary, String cfgID) {
		// We need to ensure that, if the binary is already mapped to a configuration it gets unmapped from that old configuration...
		// This method also does conversion of old style IResource mappings...
		Map<String, String> binsToConfig = getBinariesConfigMap(project);

		try {
			String path = binary.getProjectRelativePath().toOSString();
			if (binsToConfig.containsKey(path)) {
				String oldConfigID = binsToConfig.get(binary.getProjectRelativePath().toOSString());
				if (oldConfigID.equals(cfgID))
					return;
				List<String> oldBinaries = new ArrayList<String>(Arrays.asList(Activator.getProjectData(project, oldConfigID, DwarfBinaryKey)));
				oldBinaries.remove(path);
				Activator.setProjectData(project, oldConfigID, DwarfBinaryKey, oldBinaries.toArray(new String[0]));
			}

			if (cfgID == null)
				return;

			// We already know that this configuration does not contain this binary
			String[] oldbins = Activator.getProjectData(project, cfgID, DwarfBinaryKey);
			List<String> newBinaries;
			if (oldbins != null)
				newBinaries = new ArrayList<String>(Arrays.asList(oldbins));
			else
				newBinaries = new ArrayList<String>();
			newBinaries.add(path);
			Activator.setProjectData(project, cfgID, DwarfBinaryKey, newBinaries.toArray(new String[0]));
		} catch (CoreException e) {
			Activator.log(e);
		}
	}

	/**
	 * Ensure that scanner discovery is set to per configuration
	 * (to prevent an explosive growth in the scanner discovery
	 * storageModule) and disabled -- as we'll be providing correct
	 * paths via this process, and scanner discovery only slows things
	 * down
	 *
	 * FIXME just fix this
	 *
	 * @param project
	 */
	public DwarfSettingsProvider(IFile binary, String cfgId) {
		super (binary, cfgId, /*ICSettingEntry.INCLUDE_PATH |*/ ICSettingEntry.MACRO);
		IProject project = binary.getProject();
		try {
			ICConfigurationDescription desc = CoreModel.getDefault().getProjectDescription(project).getConfigurationById(cfgId);
			Configuration config = (Configuration)ManagedBuildManager.getConfigurationForDescription(desc);
			// Ensure Configuration wide scanner rather than per File
			if (config.isPerRcTypeDiscovery()) {
				
				config.setPerRcTypeDiscovery(false);

			// Disable scanner discovery for the per language
//			IScannerConfigBuilderInfo2Set scannerSet = ScannerConfigProfileManager.createScannerConfigBuildInfo2Set(project);
//			for (InfoContext context : scannerSet.getContexts())
//				scannerSet.getInfo(context).setAutoDiscoveryEnabled(false);
//			scannerSet.save();
//
//			// And for all the configurations
//			ICfgScannerConfigBuilderInfo2Set cfgScannerSet = CfgScannerConfigProfileManager.getCfgScannerConfigBuildInfo(config);
//			Set es = cfgScannerSet.getInfoMap().entrySet();
//			Map<CfgInfoContext, IScannerConfigBuilderInfo2> oldcfgScannerSet = (HashMap)((HashMap)cfgScannerSet.getInfoMap()).clone();
//			for (Map.Entry<CfgInfoContext, IScannerConfigBuilderInfo2> e : oldcfgScannerSet.entrySet()) {
//				e.getValue().setAutoDiscoveryEnabled(false);
//				cfgScannerSet.applyInfo(e.getKey(), e.getValue());
//			}

				CoreModel.getDefault().setProjectDescription(project, desc.getProjectDescription());
			}
		} catch (CoreException e) {
			reportError(e.toString());
		}
	}

	@Override
	protected IPath[] getRelevantPaths(IProject project, IPath originalPath) {
		return super.getRelevantPaths(project, originalPath);
	}
}
