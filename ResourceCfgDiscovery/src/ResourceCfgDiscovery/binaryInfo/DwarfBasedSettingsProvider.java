package ResourceCfgDiscovery.binaryInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.eclipse.cdt.core.ISymbolReader;
import org.eclipse.cdt.core.ISymbolReader.Include;
import org.eclipse.cdt.core.ISymbolReader.Macro;
import org.eclipse.cdt.core.ISymbolReader.Macro.TYPE;
import org.eclipse.cdt.core.model.CoreModel;
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
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import ResourceCfgDiscovery.Activator;

/**
 * This class is used to set Includes and Macros on the specified Project Configuration
 * by loading them in from a specified binary
 */
public class DwarfBasedSettingsProvider {
	boolean CHECKS = true;
	boolean DEBUG = true;
	boolean VERBOSE = DEBUG && true;
	boolean VVERBOSE = VERBOSE && false;

	final String cfgId;
	final IFile binary;
	
	public DwarfBasedSettingsProvider(IFile binary, String cfgId) {
		this.binary = binary;
		this.cfgId = cfgId;
	}

	/**
	 * This class acts as a placeholder in the tree to demarkate
	 * each depth as we aggregate up the tree
	 */
	private class FixedDepthPathIndex extends Path {
		public final int depth;
		public FixedDepthPathIndex(int depth) {
			super("");
			this.depth = depth;
		}
		@Override
		public int segmentCount() {
			return depth;
		}
	}
	/**
	 * Comparator which sorts paths in ascending order starting from the root...
	 */
	private class ReverseSortedByDepthComparator extends SortedByDepthComparator {
		@Override
		public int compare(IPath o1, IPath o2) {
			return -super.compare(o1, o2);
		}
	}
	/**
	 * This comparator sorts paths by depth such that all deepest paths
	 *  occur before shallow paths
	 * For paths of the same length, sorting is done
	 *     /other/path/to/...
	 *     /path/to/folder/file
	 *     /path/to/file1
	 *	   /path/to/file2
	 *	   /path/to
	 */
	private class SortedByDepthComparator implements Comparator<IPath> {
		public int compare(IPath o1, IPath o2) {
			if (o1.segmentCount() < o2.segmentCount())
				return +1;
			else if (o1.segmentCount() > o2.segmentCount())
				return -1;
			//Path separators always come first
			if (o1 instanceof FixedDepthPathIndex &&
					!(o2 instanceof FixedDepthPathIndex))
				return -1;
			else if (o2 instanceof FixedDepthPathIndex &&
					!(o1 instanceof FixedDepthPathIndex))
				return 1;

			String[] s1 = o1.segments();
			String[] s2 = o2.segments();

			for (int i = 0; i < s1.length; ++i) {
				int comparison = s1[i].compareTo(s2[i]);
				if (comparison != 0)
					return comparison;
			}
			return 0;
		}
	}

	/** Sorted Map containing the set of all macros discovered by compilation unit */
	TreeMap<IPath, LinkedHashSet<Macro>> allMacrosMap = new TreeMap<IPath, LinkedHashSet<Macro>>(new SortedByDepthComparator());
	/** Sorted Map containing the set of Includes discovered by compilation unit */
	TreeMap<IPath, LinkedHashSet<Include>> allIncludesMap = new TreeMap<IPath, LinkedHashSet<Include>>(new SortedByDepthComparator());

	public void updateSettings() {
		// Performance timing
		long startTime = System.currentTimeMillis();

		// populate allMacrosMap && allIncludesMap
		getMacrosAndIncludes(binary);

		if (!allMacrosMap.isEmpty()) {

			// Create backup of the sets for sanity checking
			TreeMap<IPath, LinkedHashSet<Macro>> sanityCheckMacroMap = new TreeMap<IPath, LinkedHashSet<Macro>>(new SortedByDepthComparator());
			TreeMap<IPath, LinkedHashSet<Include>> sanityCheckIncludeMap = new TreeMap<IPath, LinkedHashSet<Include>>(new SortedByDepthComparator());
			if (CHECKS) {
				// Clone the members individually
				for (Map.Entry<IPath, LinkedHashSet<Macro>> e : allMacrosMap.entrySet())
					sanityCheckMacroMap.put(e.getKey(), (LinkedHashSet<Macro>)e.getValue().clone());
				for (Map.Entry<IPath, LinkedHashSet<Include>> e : allIncludesMap.entrySet())
					sanityCheckIncludeMap.put(e.getKey(), (LinkedHashSet<Include>)e.getValue().clone());
			}

			// The common set of Macros, propagate similar same macros up the tree:
			condenseEntries(allMacrosMap);
			// The common set of Includes, propagate Includes up the tree:
			condenseEntries(allIncludesMap);

			// Persist Macros and Includes to the project configuration
			udpateProjectConfiguration(binary.getProject(), cfgId);

			if (DEBUG)
				System.out.println("Total Processing Time: " + (System.currentTimeMillis() - startTime) + "ms");
			if (CHECKS) {
				sanityCheck(sanityCheckMacroMap, allMacrosMap);
				sanityCheck(sanityCheckIncludeMap, allIncludesMap);
			}
		}
	}

	/**
	 * This method's job is to remove commonality between compilation units within the same subtree.
	 * i.e. it pushes common .equal() attribute objects up the tree.
	 *
	 * It operates on the passed in map.
	 *
	 * To do this:
	 *  - The Tree map is already ordered by strata (depth) (Using the SortedByDepthComparator...).
	 *  - Elements in the tree, initially, represent compilation units.
	 *  - We iterate over tree at deepest depth first (call it n), collecting all the common macros together,
	 *    - We add the common macros to a new node representing the parent directory.
	 *    - repeating for all the children of paths n-1
	 *  - Add the n-1 directories to the map
	 *  - Iterate from n-1 until we hit the root.
	 */
	private <T> void condenseEntries(TreeMap<IPath, LinkedHashSet<T>> pathSortedMap) {

		// Create an index which demarkates the strata
		IPath index = new FixedDepthPathIndex(pathSortedMap.firstKey().segmentCount());
		pathSortedMap.put(index, new LinkedHashSet<T>());

		// Attribute all common macros between files and directories at a current level to the parent container directory
		while (index.segmentCount() > 0) {
			// Create the next index
			IPath nextIndex = new FixedDepthPathIndex(index.segmentCount()-1);
			pathSortedMap.put(nextIndex, new LinkedHashSet<T>());
			// Get a map of all entries at this depth
			SortedMap<IPath, LinkedHashSet<T>> currentDepthEntries = pathSortedMap.subMap(index, nextIndex);
			// Remove the lower bound index key
			pathSortedMap.remove(index);
			// Update index
			index = nextIndex;

			/** Temporary map for the parents */
			TreeMap<IPath, LinkedHashSet<T>> parentMap = new TreeMap<IPath, LinkedHashSet<T>>(new SortedByDepthComparator());

			// Find the common elements and add them to the parent directory
			IPath currentParentDir = null;
			LinkedHashSet<T> currenParentSet = null;
			for (Map.Entry<IPath, LinkedHashSet<T>> e : currentDepthEntries.entrySet()) {
				if (!e.getKey().removeLastSegments(1).equals(currentParentDir)) {
					// Add the parent to the map
					currentParentDir = e.getKey().removeLastSegments(1);
					currenParentSet = (LinkedHashSet<T>)e.getValue().clone();
					parentMap.put(currentParentDir, currenParentSet);
				}
				currenParentSet.retainAll(e.getValue());
			}
			// Iterate again through the children removing the common elements
			for (Map.Entry<IPath, LinkedHashSet<T>> e : currentDepthEntries.entrySet())
				e.getValue().removeAll(parentMap.get(e.getKey().removeLastSegments(1)));
			// Add all the parents to the allMacrosMap
			pathSortedMap.putAll(parentMap);
		}

		// Remove all the CUs with no attributes
		Iterator<SortedMap.Entry<IPath, LinkedHashSet<T>>> it = pathSortedMap.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<IPath, LinkedHashSet<T>> e = it.next();
			if (e.getValue().isEmpty())
				it.remove();
		}

		if (DEBUG) {
			if (VERBOSE)
				for (Map.Entry<IPath, LinkedHashSet<T>> e  : pathSortedMap.entrySet()) {
					Assert.isTrue(!(e.getKey() instanceof FixedDepthPathIndex));
					System.out.println(e.getKey());
					for (Object o : e.getValue())
						System.out.println(" " + o.toString());
				}
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

				// Fetch the externally defined macors
				Map<String, LinkedHashSet<Macro>> macros = reader.getExternallyDefinedMacros();
				if (DEBUG) {
					System.out.println("Fetching Macros for " + bin.getName() + " took " + (System.currentTimeMillis() - time) + "ms");
					time = System.currentTimeMillis();
				}

				// Fetch the Includes -- should be much faster
				Map<String, LinkedHashSet<Include>> includes = reader.getIncludesPerSourceFile();
				if (DEBUG) {
					System.out.println("Fetching Includes for " + bin.getName() + " took " + (System.currentTimeMillis() - time) + "ms");
					time = System.currentTimeMillis();
				}

				// Add the macros/files to the macro map, resolving them first
				for (Map.Entry<String, LinkedHashSet<Macro>> e : macros.entrySet()) {
					IPath filePath = resolveInProject(bin.getProject(), e.getKey());
					if (filePath != null)
						allMacrosMap.put(filePath, e.getValue());
				}

				// Add the includes to the includes map, resolving paths first
				for (Map.Entry<String, LinkedHashSet<Include>> e : includes.entrySet()) {
					IPath incPath = resolveInProject(bin.getProject(), e.getKey());
					if (incPath != null)
						allIncludesMap.put(incPath, e.getValue());
				}

				if (DEBUG) {
					System.out.println("Adding Includes and Macros to map: " + (System.currentTimeMillis() - time) + "ms");
					System.out.println(allMacrosMap.size() + " compilation units with macros found");
					System.out.println(allIncludesMap.size() + " compilation units with includes found");
					if (VVERBOSE) // Print the compilation units found
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
			if (DEBUG) {
				System.out.println("Time taken to Purge Configuration: " + cfgId + " was " + (System.currentTimeMillis()- persistToProjectTime) + "ms");
				persistToProjectTime = System.currentTimeMillis();
			}

			// Due to bug 236279, apply all the settings in one go...
			ICConfigurationDescription cfgDesc = CoreModel.getDefault().getProjectDescription(project).getConfigurationById(cfgId);
			// Because of BUG 236279, child attributes are not necessarily inherited properly from the parent
			// First combine all the attributes into a single Map.
			Map combinedAttributes = combine(allMacrosMap, allIncludesMap);
			updateResourceConfigurations(project, cfgDesc, combinedAttributes);
			if (DEBUG) {
				System.out.println("Time taken to Update Resource Configs for: " + cfgId + " was " + (System.currentTimeMillis()- persistToProjectTime) + "ms");
				persistToProjectTime = System.currentTimeMillis();
			}

			// Store the settings
			CoreModel.getDefault().setProjectDescription(project, cfgDesc.getProjectDescription());
			if (DEBUG) {
				System.out.println("Time taken to setProjectDescription for: " + cfgId + " was " + (System.currentTimeMillis()- persistToProjectTime) + "ms");
			}
		} catch (CoreException e) {
			ResourceCfgDiscovery.Activator.log(e);
		}
	}

	/**
	 * Returns a sorted Map with parent paths first
	 * The Sorted set contains the unified set of attributes on the Path
	 * @param maps
	 * @return
	 */
	private Map<IPath, LinkedHashSet> combine (TreeMap... maps) {
		// Ensure that this set is sorted from the root elements going down
		Map<IPath, LinkedHashSet> result = new TreeMap<IPath, LinkedHashSet>(new ReverseSortedByDepthComparator());
		if (maps.length == 0)
			return result;
		for (Map<IPath, LinkedHashSet> m : maps) {
			for (Map.Entry<IPath, LinkedHashSet> e : m.entrySet()) {
				if (!result.containsKey(e.getKey()))
					result.put(e.getKey(), new LinkedHashSet());
				result.get(e.getKey()).addAll(e.getValue());
			}
		}
		return result;
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
//		long setProjDescriptionDelta = 0;
//		// Iterate over all the paths
//		int lastSegmentCount = 0;
//		ICConfigurationDescription cfgDesc = CoreModel.getDefault().getProjectDescription(project).getConfigurationById(cfgID);
		int resourceConfigCount = 0;
		for (Map.Entry<IPath, LinkedHashSet> e : pathToDebugObjSet.entrySet()) {

//			// Store the Project Description at each stage here, as otherwise Includes are not pushed down correctly,
//			// see ... Also get the configuration afresh from the DescriptionManager or we end up with a nasty memory leak
//			if (e.getKey().segmentCount() != lastSegmentCount) {
//			lastSegmentCount = e.getKey().segmentCount();
//			long time = System.currentTimeMillis();
//			CoreModel.getDefault().setProjectDescription(project, cfgDesc.getProjectDescription());
//			cfgDesc = CoreModel.getDefault().getProjectDescription(project).getConfigurationById(cfgID);
//			setProjDescriptionDelta += System.currentTimeMillis() - time;
//			}

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
					ICResourceDescription parentDesc = cfgDesc.getResourceDescription(parentPath, true);
					while (parentPath.segmentCount() > 0 && parentDesc == null) {
						parentPath = parentPath.removeLastSegments(1);
						parentDesc = cfgDesc.getResourceDescription(parentPath, true);
					}

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
				List<ICLanguageSettingEntry> newSettings = dwarfObjToSettingEntrys(e.getKey(), e.getValue());
				if (resDesc instanceof ICFileDescription) {
					List<ICLanguageSettingEntry> macroSettings = ((ICFileDescription)resDesc).getLanguageSetting().getSettingEntriesList(ICSettingEntry.MACRO);
					List<ICLanguageSettingEntry> includePathSettings = ((ICFileDescription)resDesc).getLanguageSetting().getSettingEntriesList(ICSettingEntry.INCLUDE_PATH);
					List<ICLanguageSettingEntry> includeFileSettings = ((ICFileDescription)resDesc).getLanguageSetting().getSettingEntriesList(ICSettingEntry.INCLUDE_FILE);
					for (ICLanguageSettingEntry lse : newSettings) {
						switch (lse.getKind()) {
						case ICSettingEntry.INCLUDE_FILE:
							includeFileSettings.add(lse);
							break;
						case ICSettingEntry.INCLUDE_PATH:
							includePathSettings.add(lse);
							break;
						case ICSettingEntry.MACRO:
							macroSettings.add(lse);
							break;
						}
					}
					((ICFileDescription)resDesc).getLanguageSetting().setSettingEntries(ICSettingEntry.MACRO, macroSettings);
					((ICFileDescription)resDesc).getLanguageSetting().setSettingEntries(ICSettingEntry.INCLUDE_PATH, includePathSettings);
					((ICFileDescription)resDesc).getLanguageSetting().setSettingEntries(ICSettingEntry.INCLUDE_FILE, includeFileSettings);
				} else if (resDesc instanceof ICFolderDescription) {
					for (ICLanguageSetting lang : ((ICFolderDescription)resDesc).getLanguageSettings()) {
						List<ICLanguageSettingEntry> macroSettings = lang.getSettingEntriesList(ICSettingEntry.MACRO);
						List<ICLanguageSettingEntry> includePathSettings = lang.getSettingEntriesList(ICSettingEntry.INCLUDE_PATH);
						List<ICLanguageSettingEntry> includeFileSettings = lang.getSettingEntriesList(ICSettingEntry.INCLUDE_FILE);
						for (ICLanguageSettingEntry lse : newSettings) {
							switch (lse.getKind()) {
							case ICSettingEntry.INCLUDE_FILE:
								includeFileSettings.add(lse);
								break;
							case ICSettingEntry.INCLUDE_PATH:
								includePathSettings.add(lse);
								break;
							case ICSettingEntry.MACRO:
								macroSettings.add(lse);
								break;
							}
						}
						lang.setSettingEntries(ICSettingEntry.MACRO, macroSettings);
						lang.setSettingEntries(ICSettingEntry.INCLUDE_PATH, includePathSettings);
						lang.setSettingEntries(ICSettingEntry.INCLUDE_FILE, includeFileSettings);
					}
				} else
					reportError(e.getValue() + " doesn't have folder or file resource desc in root, found: " + resDesc);
			}
		}
		if (DEBUG) {
			System.out.println("Number of resource configs set on: " + cfgDesc.getId() + " was " + resourceConfigCount);
		}
//		long time = System.currentTimeMillis();
//		// Persist the final config change
//		CoreModel.getDefault().setProjectDescription(project, cfgDesc.getProjectDescription());
//		setProjDescriptionDelta += System.currentTimeMillis() - time;
//		if (DEBUG) {
//		System.out.println("Time taken setProjectDescription() for: " + cfgID + " was " + setProjDescriptionDelta + "ms");
//		}
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


	private Map<ICLanguageSettingEntry,ICLanguageSettingEntry> allAttributes = new HashMap<ICLanguageSettingEntry,ICLanguageSettingEntry>();
	/**
	 * Converts a set of ISymbolReaderObjects into an ICLanguageSettingEntry List
	 * @param symObjects Set of ISymbolReader.Include && ISymbolReader.Macro
	 * @return List of ICLanguageSettingEntry List
	 */
	private List<ICLanguageSettingEntry> dwarfObjToSettingEntrys(IPath resPath, LinkedHashSet<?> symObjects) {
		List<ICLanguageSettingEntry> ls = new ArrayList<ICLanguageSettingEntry>();
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
			if (lse != null) {
				// Save memory, use the same ICLanguageSettingEntry
				if (!allAttributes.containsKey(lse))
					allAttributes.put(lse, lse);
				ls.add(allAttributes.get(lse));
			}
		}
		return ls;
	}

	/**
	 * Resolves the path in the given project. Returns null otherwise
	 * @param project
	 * @param path
	 * @return
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
				LinkedHashSet<T> orig = (LinkedHashSet<T>)e.getValue().clone();
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
			} else if (VVERBOSE)
				System.out.println("OK: " + e.getKey());
		}
	}

	protected void reportError(String error) {
		Activator.log("Dwarf Settings Provider Error: " + error);
		if (DEBUG)
			System.out.println("Dwarf Settings Provider Error: " + error);
	}
	protected void reportInfo(String error) {
		Activator.info("Dwarf Settings Provider Info: " + error);
		if (DEBUG)
			System.out.println("Dwarf Settings Provider Error: " + error);
	}

}
