package ResourceCfgDiscovery.binaryInfo;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.ISymbolReader;
import org.eclipse.cdt.core.ISymbolReader.Include;
import org.eclipse.cdt.core.ISymbolReader.Macro;
import org.eclipse.cdt.core.ISymbolReader.Macro.TYPE;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.parser.IScannerInfo;
import org.eclipse.cdt.core.parser.IScannerInfoProvider;
import org.eclipse.cdt.core.settings.model.CIncludeFileEntry;
import org.eclipse.cdt.core.settings.model.CIncludePathEntry;
import org.eclipse.cdt.core.settings.model.CMacroEntry;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICFileDescription;
import org.eclipse.cdt.core.settings.model.ICFolderDescription;
import org.eclipse.cdt.core.settings.model.ICLanguageSetting;
import org.eclipse.cdt.core.settings.model.ICLanguageSettingEntry;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.settings.model.ICResourceDescription;
import org.eclipse.cdt.core.settings.model.ICSettingEntry;
import org.eclipse.cdt.utils.elf.parser.ElfParser;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import ResourceCfgDiscovery.Activator;

/**
 * This class is used to set Includes and Macros on the specified Project Configuration
 * by loading them in from a specified binary
 *
 * FIXME JBB currently these settings are persisted on the build model.  This is currently
 * not scalable.  What we do for the moment is not add entries that we can get from
 * scanner discovery -- however in the future we should go further than this and simply
 * not alter the build model at all, rather push this information in via scanner discovery
 */
public class DwarfBasedSettingsProvider {
	private static final boolean CHECKS = true;
	private static final boolean Debug_DWARF_PROVIDER = true;
	private static final boolean Debug_DWARF_PROVIDER_VERBOSE = true;

	/** Language setting entries cache */
	private static volatile Reference<Map<ICLanguageSettingEntry, ICLanguageSettingEntry>> langSettEntryCache = new SoftReference<Map<ICLanguageSettingEntry, ICLanguageSettingEntry>>(null);
	/** Instance handle on the cache map */
	private Map<ICLanguageSettingEntry,ICLanguageSettingEntry> allAttributes;

	final IProject project;
	final int kind;
	final String cfgId;
	final IFile binary;

	/**
	 * Load settings for the particular {@link ICSettingEntry} kinds into the given
	 * configuration id, in the project which contains the binary
	 *
	 * Currently only the following kinds are supported:
	 * {@link ICSettingEntry#MACRO}
	 * {@link ICSettingEntry#INCLUDE_PATH}
	 * @param binary
	 * @param cfgId
	 * @param kind
	 */
	public DwarfBasedSettingsProvider(IFile binary, String cfgId, int kind) {
		project = binary.getProject();
		this.binary = binary;
		this.cfgId = cfgId;
		this.kind = kind;
		// Initialise the setting entries cache
		synchronized (DwarfBasedSettingsProvider.class) {
			allAttributes = langSettEntryCache.get();
			if (allAttributes == null) {
				allAttributes = new ConcurrentHashMap<ICLanguageSettingEntry, ICLanguageSettingEntry>();
				langSettEntryCache = new SoftReference<Map<ICLanguageSettingEntry,ICLanguageSettingEntry>>(allAttributes);
			}
		}
	}

	/** Sorted Map containing the set of all macros discovered by compilation unit */
	TreeMap<IPath, LinkedHashSet<Macro>> allMacrosMap = new TreeMap<IPath, LinkedHashSet<Macro>>(new PathTreeUtils.DeepestFirstPathComparator());
	/** Sorted Map containing the set of Includes discovered by compilation unit */
	TreeMap<IPath, LinkedHashSet<Include>> allIncludesMap = new TreeMap<IPath, LinkedHashSet<Include>>(new PathTreeUtils.DeepestFirstPathComparator());

	/**
	 * The main entrance point for updating the project configuration based on the settings discovered in the provided binary
	 */
	public void updateSettings() {
		// Performance timing
		long startTime = System.currentTimeMillis();

		// populate allMacrosMap && allIncludesMap
		getMacrosAndIncludes(binary);

		// Create backup of the sets for sanity checking
		TreeMap<IPath, LinkedHashSet<Macro>> sanityCheckMacroMap = new TreeMap<IPath, LinkedHashSet<Macro>>(new PathTreeUtils.DeepestFirstPathComparator());
		TreeMap<IPath, LinkedHashSet<Include>> sanityCheckIncludeMap = new TreeMap<IPath, LinkedHashSet<Include>>(new PathTreeUtils.DeepestFirstPathComparator());
		if (CHECKS) {
			// Clone the members individually
			for (Map.Entry<IPath, LinkedHashSet<Macro>> e : allMacrosMap.entrySet())
				sanityCheckMacroMap.put(e.getKey(), (LinkedHashSet<Macro>)e.getValue().clone());
			for (Map.Entry<IPath, LinkedHashSet<Include>> e : allIncludesMap.entrySet())
				sanityCheckIncludeMap.put(e.getKey(), (LinkedHashSet<Include>)e.getValue().clone());
		}

		// FIXME JBB
		//  No point in propogating common elements upwards here, as the
		//  current storage mechanism in CDT simply undoes this
		//  as all parent ICResourceDescription language settings entries
		//  are copied to the child when the child resource description is created...
		// The common set of Macros, propagate similar same macros up the tree:
//		PathTreeUtils.propogateUpwards(allMacrosMap);
//		// The common set of Includes, propagate Includes up the tree:
//		PathTreeUtils.propogateUpwards(allIncludesMap);

		// Persist Macros and Includes to the project configuration
		udpateProjectConfiguration(binary.getProject(), cfgId);

		if (Debug_DWARF_PROVIDER)
			System.out.println("Total Processing Time: " + (System.currentTimeMillis() - startTime) + "ms");
		if (CHECKS) {
			sanityCheck(sanityCheckMacroMap, allMacrosMap);
			sanityCheck(sanityCheckIncludeMap, allIncludesMap);
		}
	}

	/**
	 * Fetches all the externally defined Macros from the IFile binary
	 * storing them in allMacrosMap set
	 * @param bin IFile binary
	 */
	private void getMacrosAndIncludes(IFile bin) {
		ElfParser parser = new ElfParser();
		try {
			ISymbolReader reader = (ISymbolReader)parser.getBinary(bin.getLocation()).getAdapter(ISymbolReader.class);
			if (reader != null) {
				long time = System.currentTimeMillis();

				if ((kind & ICSettingEntry.MACRO) != 0) {
					// Fetch the externally defined macors
					Map<String, LinkedHashSet<Macro>> macros = reader.getExternallyDefinedMacros();
					if (Debug_DWARF_PROVIDER) {
						System.out.println("Fetching Macros for " + bin.getName() + " took " + (System.currentTimeMillis() - time) + "ms");
						time = System.currentTimeMillis();
					}

					// Add the macros/files to the macro map, resolving them first
					for (Map.Entry<String, LinkedHashSet<Macro>> e : macros.entrySet()) {
						IPath filePath = resolveInProject(bin.getProject(), e.getKey());
						if (filePath != null)
							allMacrosMap.put(filePath, e.getValue());
					}
				}

				if ((kind & ICSettingEntry.INCLUDE_PATH) != 0) {
					// Fetch the Includes -- should be much faster
					Map<String, LinkedHashSet<Include>> includes = reader.getIncludesPerSourceFile();
					if (Debug_DWARF_PROVIDER) {
						System.out.println("Fetching Includes for " + bin.getName() + " took " + (System.currentTimeMillis() - time) + "ms");
						time = System.currentTimeMillis();
					}

					// Add the includes to the includes map, resolving paths first
					for (Map.Entry<String, LinkedHashSet<Include>> e : includes.entrySet()) {
						IPath incPath = resolveInProject(bin.getProject(), e.getKey());
						if (incPath != null)
							allIncludesMap.put(incPath, e.getValue());
					}
				}

				if (Debug_DWARF_PROVIDER) {
					System.out.println("Adding Includes and Macros to map: " + (System.currentTimeMillis() - time) + "ms");
					System.out.println(allMacrosMap.size() + " compilation units with macros found");
					System.out.println(allIncludesMap.size() + " compilation units with includes found");
					if (Debug_DWARF_PROVIDER_VERBOSE) // Print the compilation units found
						for (Map.Entry<IPath, LinkedHashSet<Macro>> e  : allMacrosMap.entrySet()) {
							System.out.println(e.getKey());
						}
				}
			}
		} catch (IOException e) {
			reportError(e.toString());
		}
	}


	private void udpateProjectConfiguration(IProject project, String cfgId) {
		try {

			// Nuke the configuration then recreate it.  Otherwise system will grind to a halt...
			// NB any changes the user has made _will_ be lost
			long persistToProjectTime = System.currentTimeMillis();
//			purgeDescriptions(project, cfgId);
			if (Debug_DWARF_PROVIDER) {
				System.out.println("Time taken to Purge Configuration: " + cfgId + " was " + (System.currentTimeMillis()- persistToProjectTime) + "ms");
				persistToProjectTime = System.currentTimeMillis();
			}

			// Due to bug 236279, apply all the settings in one go...
			ICConfigurationDescription cfgDesc = CoreModel.getDefault().getProjectDescription(project).getConfigurationById(cfgId);
			// Because of BUG 236279, child attributes are not necessarily inherited properly from the parent
			// First combine all the attributes into a single Map.
			Map combinedAttributes = PathTreeUtils.combine(allMacrosMap, allIncludesMap);
			updateResourceConfigurations(project, cfgDesc, combinedAttributes);
			if (Debug_DWARF_PROVIDER) {
				System.out.println("Time taken to Update Resource Configs for: " + cfgId + " was " + (System.currentTimeMillis()- persistToProjectTime) + "ms");
				persistToProjectTime = System.currentTimeMillis();
			}

			// Update the source and output paths
			updateSourceOutputPaths(cfgDesc);

			// Store the settings
			CoreModel.getDefault().setProjectDescription(project, cfgDesc.getProjectDescription());
			if (Debug_DWARF_PROVIDER) {
				System.out.println("Time taken to setProjectDescription for: " + cfgId + " was " + (System.currentTimeMillis()- persistToProjectTime) + "ms");
			}
		} catch (CoreException e) {
			Activator.log(e);
		}
	}

	/**
	 * Hook to allow update the source and output paths on the given configuration
	 */
	protected void updateSourceOutputPaths(ICConfigurationDescription cfgDesc) {

	}

	/**
	 * Nuke the existing description on this project
	 * @param root
	 */
	private void purgeDescriptions(IProject project, String cfgId) throws CoreException {
		ICProjectDescription projectDesc = CoreModel.getDefault().getProjectDescription(project);
		ICConfigurationDescription cfgDesc = projectDesc.getConfigurationById(cfgId);

		ICFolderDescription root = (ICFolderDescription)cfgDesc.getResourceDescription(new Path(""), true);
		for (ICResourceDescription resDesc : root.getNestedResourceDescriptions()) {
			try {
				root.getConfiguration().removeResourceDescription(resDesc);
			} catch (CoreException e) {
				reportError("Error purging description: " + e.toString());
			}
		}
		CoreModel.getDefault().setProjectDescription(project, projectDesc);
	}

	/**
	 *
	 * @param project
	 * @param cfgDesc
	 * @param pathToDebugObjSet
	 * @throws CoreException
	 */
	protected void updateResourceConfigurations(IProject project, ICConfigurationDescription cfgDesc/*String cfgID*/, Map<IPath, LinkedHashSet> pathToDebugObjSet)
	throws CoreException {
		int resourceConfigCount = 0;
		for (Map.Entry<IPath, LinkedHashSet> e : pathToDebugObjSet.entrySet()) {
			// All extenders to specify a set of paths they want to be annotated with these attributes
			for (IPath path : getRelevantPaths(project, e.getKey())) {
				++resourceConfigCount;
				ICResourceDescription resDesc = cfgDesc.getResourceDescription(path, true);
				if (resDesc == null) {
					// Ensure the resource exists...
					if (project.findMember(path) == null || !project.findMember(path).exists()) {
						reportInfo("Resource " + path + " not found!");
						continue;
					}

					// Find parent ICResourceDescription
					IPath parentPath = path.removeLastSegments(1);
					ICResourceDescription parentDesc = cfgDesc.getResourceDescription(parentPath, false);

					// Create the resource configuration for the file / directory based on the parent
					switch (project.findMember(path).getType()) {
					case IResource.FILE:
						resDesc = cfgDesc.createFileDescription(path, parentDesc);
						break;
					case IResource.FOLDER:
					case IResource.PROJECT:
						resDesc = cfgDesc.createFolderDescription(path, (ICFolderDescription)parentDesc);
						break;
					default:
						reportError("Resource type " + project.findMember(path).getType() + " unhandled!");
					}
				}
				// Convert Macros/Includes attributes to Settings entries
				LinkedHashSet<ICLanguageSettingEntry> newSettings = dwarfObjToSettingEntrys(e.getKey(), e.getValue());

				List<ICLanguageSetting> langs = new ArrayList<ICLanguageSetting>(3);
				if (resDesc instanceof ICFileDescription)
					langs.add(((ICFileDescription)resDesc).getLanguageSetting());
				else {
					for (ICLanguageSetting lang : ((ICFolderDescription)resDesc).getLanguageSettings()) {
						if (lang.supportsEntryKind(kind))
							langs.add(lang);
					}
				}

				for (ICLanguageSetting lang : langs) {
					if ((kind & ICSettingEntry.INCLUDE_PATH) != 0) {
						newSettings.addAll(lang.getSettingEntriesList(ICSettingEntry.INCLUDE_PATH));
						removeScannerDiscoveryProvidedInfo(newSettings);
						lang.setSettingEntries(ICSettingEntry.INCLUDE_PATH, newSettings.toArray(new ICLanguageSettingEntry[newSettings.size()]));
					}
					if ((kind & ICSettingEntry.MACRO) != 0) {
						newSettings.addAll(lang.getSettingEntriesList(ICSettingEntry.MACRO));
						removeScannerDiscoveryProvidedInfo(newSettings);
						lang.setSettingEntries(ICSettingEntry.MACRO, newSettings.toArray(new ICLanguageSettingEntry[newSettings.size()]));
					}
				}
			}
		}
		if (Debug_DWARF_PROVIDER) {
			System.out.println("Number of resource configs set on: " + cfgDesc.getId() + " was " + resourceConfigCount);
		}
	}

	/**
	 * Helper function to remove built-in entries that are available from the compiler
	 * provided scanner info.  This is to aid in reducing the amount of data we request be stored in the project
	 * model.
	 * The passed in hash set is modified in place.
	 * 
	 * Note that we're not fetching the build configuration specific scanner discovered info here
	 * as, for all we know, that may not have been intialised yet.  Fruthermore we're fetching
	 * per project info because we're really only trying to remove the common macros that exist
	 * from the compiler -- it's expected this set of macros won't change greatly from release
	 * to release.
	 */
	private void removeScannerDiscoveryProvidedInfo(LinkedHashSet<ICLanguageSettingEntry> settings) {
		IScannerInfoProvider sip = CCorePlugin.getDefault().getScannerInfoProvider(project);
		if (sip == null)
			return;
		IScannerInfo prjInfo = sip.getScannerInformation(project);
		if (prjInfo == null)
			return;
		Set<ICLanguageSettingEntry> definedSyms = convertScannerInfoDefinesToLanguageSettings(prjInfo.getDefinedSymbols());
		Set<ICLanguageSettingEntry> includePaths = convertScannerInfoIncludesToLanguageSettings(prjInfo.getIncludePaths());
		settings.removeAll(definedSyms);
		settings.removeAll(includePaths);
	}

	private Set<ICLanguageSettingEntry> convertScannerInfoDefinesToLanguageSettings(Map<String,String> defines) {
		HashSet<ICLanguageSettingEntry> set = new HashSet<ICLanguageSettingEntry>(defines.size());
		for (Map.Entry<String,String> e : defines.entrySet()) {
			ICLanguageSettingEntry lse = new CMacroEntry(e.getKey(), e.getValue(), 0);
			set.add(internEntry(lse));
		}
		return set;
	}
	private Set<ICLanguageSettingEntry> convertScannerInfoIncludesToLanguageSettings(String[] includes) {
		HashSet<ICLanguageSettingEntry> set = new HashSet<ICLanguageSettingEntry>(includes.length);
		for (String inc : includes) {
			set.add(internEntry(new CIncludePathEntry(inc, 0)));
		}
		return set;
	}

	/**
	 * In some cases we may want to add resource configuration information
	 * to more than one item in the project
	 *
	 * Contributors may return an array of project relative paths here
	 *
	 * @param originalPath
	 * @return IPath[] which should have the same resource configuration applied to them
	 */
	protected IPath[] getRelevantPaths(IProject project, IPath originalPath) {
		return new IPath[]{originalPath};
	}


	/**
	 * Converts a set of ISymbolReaderObjects into an ICLanguageSettingEntry List
	 * @param symObjects Set of ISymbolReader.Include && ISymbolReader.Macro
	 * @return List of ICLanguageSettingEntry List
	 */
	private LinkedHashSet<ICLanguageSettingEntry> dwarfObjToSettingEntrys(IPath resPath, LinkedHashSet<?> symObjects) {
		LinkedHashSet<ICLanguageSettingEntry> ls = new LinkedHashSet<ICLanguageSettingEntry>();
		if (symObjects.isEmpty())
			return ls;

		ICLanguageSettingEntry lse = null;
		for (Object o : symObjects) {
			if (o instanceof Macro) {
				Macro m = (Macro)o;
				if (m.type == TYPE.DEFINED)
					lse = new CMacroEntry(m.getName(), m.getValue(), 0);
				else
					lse = null;
				// Only add defined objects.
				// If current type is undefined, ensure that we haven't already added a defined version.
				// The interface on ISymbolReader specifies that a macro is only defined / undefined once, and it's the final
				// declaration that persists
				if (m.type == TYPE.UNDEFINED && symObjects.contains(new Macro(TYPE.DEFINED, m.macro)))
					reportError(m + " already defined now undefined!");
			} else if (o instanceof Include) {
				Include i = (Include)o;
				int flags = 0;
				// Try to resolve the path in the workspace
				IPath path = resolveInWorkspace(i.path);
				if (path != null)
					flags = ICSettingEntry.VALUE_WORKSPACE_PATH;
				else
					path = i.path;

				if (i.type == Include.TYPE.DIRECTORY) {
					lse = new CIncludePathEntry(path, flags);
				} else { // i.type == Include.TYPE.FILE
					lse = new CIncludeFileEntry(path, flags);
				}
			}
			if (lse != null)
				ls.add(internEntry(lse));
		}
		return ls;
	}

	/**
	 * Intern a language setting entry so they are shared for multiple files
	 * @param entry
	 * @return
	 */
	private ICLanguageSettingEntry internEntry(ICLanguageSettingEntry entry) {
		if (!allAttributes.containsKey(entry))
			allAttributes.put(entry, entry);
		return allAttributes.get(entry);
	}

	/**
	 * Resolves the path in the given project. Returns null otherwise
	 * @param project
	 * @param cuPath Path of the compilation unit from which this path was taken (for relative paths)
	 * @param pathToResolve The path to locate in the project
	 * @return IPath of the resolve original source path
	 * FIXME JBB can we replace this with ResourceLookup?
	 */
	protected IPath resolveInProject(IProject project, String path) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IPath p = new Path(path);
		// Try resolving as a file
		for (IFile file : root.findFilesForLocation(p))
			if (file.exists() && file.getProject().equals(project))
				return file.getProjectRelativePath();
			else if (file.exists()) {
				reportInfo(path + " not in Project " + project);
				return null;
			}
		// try resolving as a container
		for (IContainer container : root.findContainersForLocation(p))
			if (container.exists() && container.getProject().equals(project))
				return container.getProjectRelativePath();
			else if (container.exists()) {
				reportInfo(path + " not in Project " + project);
				return null;
			}

		// Try absolute path
		if (p.isAbsolute() && p.toFile().exists())
			// Return null here as user can not set attributes on non-workspace controlled paths
			return null;
		// Give up
		reportInfo("Source Path: " + path + " not resolved in " + project);
		return null;
	}

	/**
	 * Attempts to resolve the path in the workspace, otherwise returns null
	 * @param path
	 * @return workspace relative IPath
 	 * FIXME JBB can we replace this with ResourceLookup?
	 */
	private IPath resolveInWorkspace(IPath path) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		// Try resolving as a file
		for (IFile file : root.findFilesForLocation(path))
			if (file.exists())
				return file.getFullPath();
		// try resolving as a container
		for (IContainer container : root.findContainersForLocation(path))
			if (container.exists())
				return container.getFullPath();
		// Try absolute path
		if (path.isAbsolute() && path.toFile().exists())
			// Return null here as user can not set attributes on non-workspace controlled paths
			return null;
		// Give up
		reportInfo("Include Path: " + path + " not resolved in workspace or filesystem");
		return null;
	}

	/**
	 * This sanity check verifies that the condensed version matches
	 * the original, by iterating up the tree aggregating all the condensed entries
	 * @param original
	 * @param condensed
	 */
	private <T> void sanityCheck(TreeMap<IPath, LinkedHashSet<T>> original, TreeMap<IPath, LinkedHashSet<T>> condensed) {
		// Iterate through the original set
		for (Map.Entry<IPath, LinkedHashSet<T>> e : original.entrySet()) {
			IPath path = e.getKey();
			LinkedHashSet<T> newMacSet = new LinkedHashSet<T>();
			while (path.segmentCount() > 0) {
				if (condensed.containsKey(path))
					newMacSet.addAll(condensed.get(path));
				path = path.removeLastSegments(1);
			}
			if (condensed.containsKey(new Path("")))
				newMacSet.addAll(condensed.get(new Path("")));
			// Print any discrepancy
			if (!newMacSet.equals(original.get(e.getKey()))) {
				StringBuilder sb = new StringBuilder();
				sb.append("Discrepancy: ").append(e.getKey()).append("\n");
				LinkedHashSet<T> orig = new LinkedHashSet<T>(e.getValue());
				orig.removeAll(newMacSet);
				sb.append("Missing: ");
				for (T m : orig)
					sb.append(m+", ");
				sb.append("\nAdditional: ");
				newMacSet.removeAll(e.getValue());
				for (T m : newMacSet)
					sb.append(m).append(", ");
				sb.append("\n");

				reportError(sb.toString());
			} else if (Debug_DWARF_PROVIDER_VERBOSE)
				System.out.println("OK: " + e.getKey());
		}
	}

	protected void reportError(String error) {
		Activator.log("Dwarf Settings Provider Error: " + error);
		if (Debug_DWARF_PROVIDER)
			System.out.println("Dwarf Settings Provider Error: " + error);
	}
	protected void reportInfo(String error) {
		Activator.info("Dwarf Settings Provider Info: " + error);
		if (Debug_DWARF_PROVIDER)
			System.out.println("Dwarf Settings Provider Error: " + error);
	}

}
