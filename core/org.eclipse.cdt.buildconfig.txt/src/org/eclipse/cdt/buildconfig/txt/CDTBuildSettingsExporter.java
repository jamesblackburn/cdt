package org.eclipse.cdt.buildconfig.txt;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.cdt.buildconfig.txt.CDTBuildSettingsExporter.SettingDelta.Change;
import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.envvar.IContributedEnvironment;
import org.eclipse.cdt.core.envvar.IEnvironmentVariable;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICExternalSetting;
import org.eclipse.cdt.core.settings.model.ICFileDescription;
import org.eclipse.cdt.core.settings.model.ICFolderDescription;
import org.eclipse.cdt.core.settings.model.ICLanguageSetting;
import org.eclipse.cdt.core.settings.model.ICLanguageSettingEntry;
import org.eclipse.cdt.core.settings.model.ICPathEntry;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.settings.model.ICResourceDescription;
import org.eclipse.cdt.core.settings.model.ICSettingEntry;
import org.eclipse.cdt.core.settings.model.ICSourceEntry;
import org.eclipse.cdt.internal.core.resources.ResourceLookup;
import org.eclipse.cdt.make.ui.actions.AbstractTargetAction;
import org.eclipse.cdt.managedbuilder.core.BuildException;
import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.core.IHoldsOptions;
import org.eclipse.cdt.managedbuilder.core.IOption;
import org.eclipse.cdt.managedbuilder.core.IResourceInfo;
import org.eclipse.cdt.managedbuilder.core.ITool;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceFilterDescription;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;

/**
 * This class's job is to export the Build settings on the Project to a human
 * readable text file.
 */
public class CDTBuildSettingsExporter extends AbstractTargetAction {

	/** Magic text line which is prepended to the generated file */
	static final String BUILD_CONFIG_MAGIC_LINE = "# CDT Build-Configuration Settings for Project: ";
	public static final String DEFAULT_BUILD_CONFIG_FILE = "build_config.txt";

	/* Exclude some items that change per user */
	/** Workspace Path Variables to exclude */
	private static final String[] EXCLUDED_PATH_VARIABLES = {"PROJECT_LOC", "PARENT_LOC", "WORKSPACE_LOC", "ECLIPSE_HOME"};
	/** Environment Variables to exclude */
	private static final String[] EXCLUDED_CONFIG_ENVIRONMENT = {"PWD", "CWD"};

	public static class SettingDelta<T> {
		public enum Change { ADDED, REMOVED, MODIFIED };

		Change change;
		T item;

		public SettingDelta(Change change, T item) {
			this.change = change;
			this.item = item;
		}

		@Override
		public int hashCode() {
			return 17 * change.hashCode() + item.hashCode();
		}
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof SettingDelta))
				return false;
			@SuppressWarnings("unchecked")
			SettingDelta other = (SettingDelta) obj;
			return change.equals(other.change) &&
					item.equals(other.item);
		}
		@Override
		public String toString() {
			switch (change) {
			case ADDED:
				return "+ " + item.toString();
			case REMOVED:
				return "- " + item.toString();
			case MODIFIED:
				return "! " + item.toString();
			default:
				return "SettingDelta Type \""+change+"\" not handled";
			}
		}
	}

	private int indentation = 0;
	BufferedWriter writer;
	boolean verbose = false;
	boolean pushLangSettingsUpTree = false;

	/** Map from Source path -> List of Exclusions */
	private Map<IPath, Collection<IPath>> commonSourceAndExclusions = new TreeMap<IPath, Collection<IPath>>(new PathTreeUtils.NaturalPathSorter());
	/**
	 * A map of includes, lib paths & libs which we pick up by virtue of the project referencing mechanism.
	 * We don't display these in referenced projects by default
	 */
	private Map<Integer, Set<String>> referencedBits = new HashMap<Integer, Set<String>>();
	/**
	 * Map from entrykind to collection of things exported by the current configuration
	 */
	private Map<Integer, Set<String>> myExportedBits = new HashMap<Integer, Set<String>>();

	@Override
	public void run(IAction action) {
		final IContainer container = getSelectedContainer();
		if (container != null) {
			BuildConfigurationExportDialog dialog = new BuildConfigurationExportDialog(container.getProject(), Display.getDefault().getActiveShell());
			if (dialog.open() == Window.OK) {
				final String output = dialog.getLocation();
				verbose = dialog.boolVerbose;
				pushLangSettingsUpTree = dialog.boolPushUpTree;
				Job j = new Job("Settings Exporter Job...") {
					@Override
					protected IStatus run(IProgressMonitor monitor) {
						IFile f = ResourceLookup.selectFileForLocation(new Path(output), container.getProject());
						if (f != null) {
							printConfigurationSettings(container.getProject(), f);
						} else {
							printConfigurationSettings(container.getProject(), output);
						}
						printReferenceGraph(container.getProject());
						return Status.OK_STATUS;
					}
				};
				j.setPriority(Job.SHORT);
				j.schedule();
			}
		}
	}

	private void pushIndent() {
		indentation+=2;
	}

	private void popIndent() {
		indentation-=2;
	}

	/**
	 * Entry point for a java.io.File based writer where we can't find an IFile
	 * @param project
	 * @param strFile
	 */
	private void printConfigurationSettings(IProject project, String strFile) {
		try {
			File outputFile = new Path(strFile).toFile();
			writer = new BufferedWriter(new FileWriter(outputFile));

			// Print all the settings
			printMain(project);

			// Flush the writer
			writer.flush();
		} catch (IOException e) {
			Activator.log(e);
		} finally {
			if (writer != null)
				try { writer.close(); } catch (IOException e) {/*Ignore*/}
		}
	}


	/**
	 * Entry point for an IFile based writer (means we have local history)
	 * @param project
	 * @param file
	 */
	public void printConfigurationSettings(IProject project, IFile file) {
		ByteArrayOutputStream os = null;
		try {
			os = new ByteArrayOutputStream();
			// Create writer on the output stream
			writer = new BufferedWriter(new OutputStreamWriter(os));

			// Do the actual work of printing
			printMain(project);

			// Ensure everything has been flushed through to the output stream
			writer.flush();

			// If there have been no changes, then don't write out to the IFile...
			if (file.exists()) {
				InputStream in = file.getContents(true);
				try {
					byte[] osByteArray = os.toByteArray();
					int i=0;
					while (osByteArray.length > i) {
						int read = in.read();
						if (read != osByteArray[i++])
							break;
						if (read == -1) // no more to read and files equal
							break;
						// If the file hasn't changed, return!
						if (i == osByteArray.length && in.read() == -1)
							return;
					}
				} finally {
					if (in != null)
						in.close();
				}
			}

			// If read-only then validateEdit (ClearCase safe)
			if (file.exists() && file.getResourceAttributes().isReadOnly()) {
				ResourcesPlugin.getWorkspace().validateEdit(new IFile[] {file}, null);
				if (file.getResourceAttributes().isReadOnly())
					return;
			}
			// Set contents
			if (!file.exists())
				file.create(new ByteArrayInputStream(os.toByteArray()), true, null);
			else
				file.setContents(new ByteArrayInputStream(os.toByteArray()), true, true, null);
		} catch (Exception e) {
			Activator.log(e);
		} finally {
			if (writer != null)
				try { writer.close(); } catch (IOException e) {/*Ignore*/}
			writer = null;
			if (os != null)
				try { os.close(); } catch (IOException e) {/*Ignore*/}
		}
	}

	/**
	 * Main wrapper for the drives the printing
	 */
	private void printMain(IProject project) {
		// Print the magi-line
		print(BUILD_CONFIG_MAGIC_LINE + project.getName() + "\n");

		printLinkedResources(project);
		printResourcesWithFilters(project);

		ICProjectDescription projDesc = CoreModel.getDefault().getProjectDescription(project, false);
		if (projDesc != null) {
			printGlobalSourcePaths(projDesc);
			for (ICConfigurationDescription cfgDes : projDesc.getConfigurations()) {
				referencedBits.clear();
				myExportedBits.clear();
				print("\n");
				print("Configuration: " + cfgDes.getName());
				pushIndent();
				printEnvironment(cfgDes);
				printReferences(cfgDes);
				printMyExported(cfgDes);
				printConfiguration(cfgDes);
				popIndent();
			}
		}
	}

	/**
	 * Print the source paths and exclusions from all configurations
	 */
	private void printGlobalSourcePaths(ICProjectDescription prjDesc) {
		// Add the intial set of excluded resources
		for (ICSourceEntry sourceEntry : prjDesc.getActiveConfiguration().getSourceEntries()) {
			IPath sourcePath = sourceEntry.getFullPath();
			commonSourceAndExclusions.put(sourcePath, new LinkedHashSet<IPath>(Arrays.asList(sourceEntry.getExclusionPatterns())));
		}

		// Iterate over the other configs checking the source paths
		for (ICConfigurationDescription cfgDesc : prjDesc.getConfigurations()) {
			ICSourceEntry[] entries = cfgDesc.getSourceEntries();
			// Aggregate the files / path excluded from the build
			for (ICSourceEntry sourceEntry : entries) {
				IPath sourcePath = sourceEntry.getFullPath();
				if (commonSourceAndExclusions.containsKey(sourcePath))
					commonSourceAndExclusions.get(sourcePath).retainAll(Arrays.asList(sourceEntry.getExclusionPatterns()));
			}

			// Remove any source entries not contained by this configuration
			outer:
			for (Iterator<IPath> it = commonSourceAndExclusions.keySet().iterator(); it.hasNext();) {
				IPath global = it.next();
				for (ICSourceEntry cfgEntry : entries)
					if (cfgEntry.getFullPath().equals(global))
						continue outer;
				it.remove();
			}
		}

		if (!commonSourceAndExclusions.isEmpty()) {
			print ("Global Source Paths:");
			for (Entry<IPath, Collection<IPath>> p : commonSourceAndExclusions.entrySet()) {
				StringBuilder sb = new StringBuilder("  ");
				sb.append(p.getKey());
				if (!p.getValue().isEmpty()) {
					sb.append(" [Excluded:");
					for (IPath excluded : p.getValue())
						sb.append(" ").append(excluded.toString()).append(",");
					sb.setLength(sb.length() - 1);
					sb.append("]");
				}
				print (sb.toString());
			}
		}
	}

	/**
	 * Print linked resources
	 */
	private void printLinkedResources(IProject project) {
		try {
			// Print the Path Variables in use
			Set<String> pathVariables = new LinkedHashSet<String>();
			pathVariables.addAll(Arrays.asList(project.getPathVariableManager().getPathVariableNames()));
			pathVariables.removeAll(Arrays.asList(EXCLUDED_PATH_VARIABLES));
			if (!pathVariables.isEmpty()) {
				print("Path Variables:");
				for (String pvName : pathVariables)
					print("  " + pvName + "=" + project.getPathVariableManager().getValue(pvName));
			}

			// Display linked resources ...
			project.accept(new IResourceProxyVisitor() {
				boolean headerPrinted = false;
				public boolean visit(IResourceProxy proxy) throws CoreException {
					if (proxy.isLinked()) {
						if (!headerPrinted) {
							print("Linked resources:");
							headerPrinted = true;
						}
						IResource resource = proxy.requestResource();
						// Print Workspace path => Path Relative Linked Resource
						if (resource.getType() == IResource.FOLDER)
							print(" Group: " + resource.getFullPath().toOSString());
						else if (resource.getRawLocation() != null)
							print("  " + resource.getFullPath().toOSString() + " => " + resource.getRawLocation().toOSString());
					}
					return true;
				}
			}, IResource.NONE);
		} catch (CoreException e) {
			Activator.log(e);
		}
	}

	/**
	 * Print resources with IResource level filters
	 */
	private void printResourcesWithFilters(IProject project) {
		try {
			// Display filtered resources ...
			project.accept(new IResourceVisitor() {
				boolean headerPrinted = false;
				public boolean visit(IResource resource) throws CoreException {
					if (resource instanceof IContainer && ((IContainer)resource).getFilters().length > 0) {
						if (!headerPrinted) {
							print("Filters:");
							headerPrinted = true;
						}
						IContainer container = (IContainer)resource;
						IResourceFilterDescription[] filters = container.getFilters();
						pushIndent();

						StringBuilder sb = new StringBuilder(resource.getFullPath().toOSString()).append("  ");
						for (int i = 0; i < filters.length ; i++) {
							final IResourceFilterDescription filter = filters[i];
							final int type = filter.getType();
							if ((type & IResourceFilterDescription.EXCLUDE_ALL) != 0)
								sb.append("Exclude all");
							else if ((type & IResourceFilterDescription.INCLUDE_ONLY) != 0)
								sb.append("Include only");
							// Arguments
							String arguments = filter.getFileInfoMatcherDescription().getArguments().toString();
							if (arguments != null)
								sb.append(": \"").append(arguments).append("\"");
							// Applies to files & or folder or both?
							if ((type & IResourceFilterDescription.FILES) != 0)
								sb.append(" files");
							if ((type & IResourceFilterDescription.FILES) != 0 && (type & IResourceFilterDescription.FOLDERS) != 0)
								sb.append(" &");
							if ((type & IResourceFilterDescription.FOLDERS) != 0)
								sb.append(" folders");
							// Inheritable?
							if ((type & IResourceFilterDescription.INHERITABLE) != 0)
								sb.append("  -  ").append("Inheritable");
							print(sb);

							if (i == 0)
								indentation += resource.getFullPath().toOSString().length() + 2;
							sb = new StringBuilder();
						}
						indentation -= resource.getFullPath().toOSString().length() + 2;
						popIndent();
					}
					return true;
				}
			});
		} catch (CoreException e) {
			Activator.log(e);
		}
	}

	/**
	 * Prints this projects this project references
	 * @param cfgDes
	 */
	private void printReferences(ICConfigurationDescription cfgDes) {
//		ICReferenceEntry[] refs = cfgDes.getReferenceEntries();
//		if (refs.length == 0)
//			return;
//		print("References: ");
//		for (ICReferenceEntry ref : refs) {
//			pushIndent();
//			String prjName = ref.getProject();
//			String cfgName = "[Active]";
//			if (ref.getConfiguration().length() != 0)
//				cfgName = ref.getConfiguration() + " (not found)";
//			IProject refdp = ResourcesPlugin.getWorkspace().getRoot().getProject(prjName);
//			if (refdp.exists()) {
//				ICProjectDescription desc = CCorePlugin.getDefault().getProjectDescription(refdp, false);
//				if (desc != null) {
//					ICConfigurationDescription cfg = desc.getConfigurationById(ref.getConfiguration());
//					if (cfg != null) {
//						cfgName = cfg.getName();
//						// add the exported settings to the set of things picked up by default by this project
//						ICExternalSetting[] extSettings = cfg.getExternalSettings();
//						for (ICExternalSetting sett : extSettings) {
//							for (ICSettingEntry entry : sett.getEntries()) {
//								if (!referencedBits.containsKey(entry.getKind()))
//									referencedBits.put(entry.getKind(), new HashSet<String>());
//								String value = entry.getName();
//								// Convert workspace paths to:
//								// "${workspace_loc:...}"  *yuck*
//								// FIXME this is very tied to the managedbuil implementation
//								if (entry instanceof ICPathEntry && ((ICPathEntry)entry).isValueWorkspacePath())
//									value = ManagedBuildManager.fullPathToLocation(value);
//								referencedBits.get(entry.getKind()).add(doubleQuotePath(value, false));
//							}
//						}
//					}
//				}
//			}
//			print(prjName + ": " + cfgName);
//			popIndent();
//		}
	}

	HashSet<String> visitedReferences = new HashSet<String>();
	private void recurseReferences(ICConfigurationDescription cfgDesc, StringBuilder sb, int depth) {
		Map<String, String> refs = cfgDesc.getReferenceInfo();
		if (refs.isEmpty())
			return;

		// add some indentation
		String prefix = "";
		for (int i = 0; i < depth; i++)
			prefix += "  ";
		String this_config = prefix + "\"" + cfgDesc.getProjectDescription().getProject().getName() + ":" + cfgDesc.getName() + "\" -> \"";

		for (Map.Entry<String, String> e : refs.entrySet()) {
			String prjName = e.getKey();
			String cfgName = "[Active]";
			if (e.getValue().length() != 0)
				cfgName = e.getValue() + " (not found)";
			IProject refdp = ResourcesPlugin.getWorkspace().getRoot().getProject(prjName);
			if (refdp.exists()) {
				ICProjectDescription desc = CCorePlugin.getDefault().getProjectDescription(refdp, false);
				if (desc != null) {
					ICConfigurationDescription cfg = desc.getConfigurationById(e.getValue());
					if (cfg != null)
						cfgName = cfg.getName();
					sb.append(this_config);
					sb.append(prjName).append(":").append(cfgName).append("\"\n");
					// Recurse - not infinitely though!
					String key = prjName+ ":" + cfgName + " [" + e.getValue() + "]";
					if (!visitedReferences.contains(key)) {
						visitedReferences.add(key);
						if (cfg != null)
							recurseReferences(cfg, sb, depth + 1);
					}
					// Printing done, continue
					continue;
				}
			}
			// Add the un-resolved reference so we print it out correctly as 'reachable' in the dot file.
			visitedReferences.add(prjName + ":" + cfgName);
			sb.append(this_config);
			sb.append(prjName).append(":").append(cfgName).append("\"\n");
		}
	}

	/**
	 * print the reference graph reachable from project - one file per configuration
	 * @param project
	 */
	private void printReferenceGraph(IProject project) {
		for (ICConfigurationDescription cfgDes : CoreModel.getDefault().getProjectDescription(project, false).getConfigurations()) {
			StringBuilder sb = new StringBuilder();
			sb.append("digraph {\n");
			recurseReferences(cfgDes, sb, 0);
			sb.append("}\n");

			// Print the reachable functions
			StringBuilder sb1 = new StringBuilder();
			sb1.append("#Reachable-Configs:\n");
			for (String ref : visitedReferences)
				sb1.append("# ").append(ref).append("\n");
			sb1.append("\n\n");
			sb1.append(sb);
			sb = sb1;

			try {
				IFile f = project.getFile("build_reference_graph_" + cfgDes.getName() + ".dot");
				if (f.exists())
					f.setContents(new ByteArrayInputStream(sb.toString().getBytes()), true, false, null);
				else
					f.create(new ByteArrayInputStream(sb.toString().getBytes()), true, null);
				// Run dot
				Process p = new ProcessBuilder("dot", "-Tpng", "-o",
						f.getLocation().removeFileExtension().addFileExtension("png").toString(),
						f.getLocation().toString()).start();
				p.waitFor();
				p.destroy();
				project.getFile("build_reference_graph_" + cfgDes.getName() + ".png").refreshLocal(IResource.DEPTH_ONE, null);
			} catch (Exception e) {
				Activator.log(e);
			}
			visitedReferences.clear();
		}
	}

	/**
	 * Print & Stash the settings exported by the passed in configuration
	 * @param cfgDes
	 */
	private void printMyExported(ICConfigurationDescription cfgDes) {
		ICExternalSetting[] extSettings = cfgDes.getExternalSettings();
		if (extSettings.length == 0)
			return;
		print("Exported: ");
		pushIndent();
		StringBuilder sb = new StringBuilder();
		int prevKind = -1;
		for (ICExternalSetting sett : extSettings) {
			for (ICSettingEntry entry : sett.getEntries()) {
				int currentKind = entry.getKind();
				if (!myExportedBits.containsKey(entry.getKind()))
					myExportedBits.put(entry.getKind(), new HashSet<String>());

				if (currentKind != prevKind) {
					if (sb.length() != 0) {
						sb.setLength(sb.length() - 2);
						print(sb);
					}
					prevKind = currentKind;
					sb.setLength(0);
					switch (currentKind) {
					case ICSettingEntry.INCLUDE_PATH:
						sb.append("Include: ");
						break;
					case ICSettingEntry.INCLUDE_FILE:
						sb.append("Include: ");
						break;
					case ICSettingEntry.MACRO:
						sb.append("Include: ");
						break;
					case ICSettingEntry.MACRO_FILE  :
						sb.append("Include: ");
						break;
					case ICSettingEntry.LIBRARY_PATH:
						sb.append("Lib Path: ");
						break;
					case ICSettingEntry.LIBRARY_FILE:
						sb.append("Lib File: ");
						break;
					case ICSettingEntry.OUTPUT_PATH :
						sb.append("Output Path: ");
						break;
					case ICSettingEntry.SOURCE_PATH :
						sb.append("Source Path: ");
						break;
					default:
						print ("unhandled kind " + currentKind);
					}
				}

				String value = entry.getName();
				// Convert workspace paths to:
				// "${workspace_loc:...}"  *yuck*
				// FIXME this is very tied to the managedbuild implementation
				if (entry instanceof ICPathEntry && ((ICPathEntry)entry).isValueWorkspacePath())
					value = ManagedBuildManager.fullPathToLocation(value);
				sb.append(entry.getName()).append(", ");
				myExportedBits.get(entry.getKind()).add(entry.getName());
				myExportedBits.get(entry.getKind()).add(doubleQuotePath(value, false));
			}
		}
		if (sb.length() != 0) {
			sb.setLength(sb.length() - 2);
			print(sb);
		}
		popIndent();
	}

	/**
	 * Print the Environment set on the configuration
	 */
	private void printEnvironment(ICConfigurationDescription des) {
		try {
			// Ensure that the environment is available so we don't end up emitting a blank env section
			des.getProjectDescription().getProject().getFolder(".settings").refreshLocal(IResource.DEPTH_ONE, null);
		} catch (CoreException e) {
			Activator.log(e);
		}
		// Fetch and print the CDT build environment
		IContributedEnvironment ce = CCorePlugin.getDefault().getBuildEnvironmentManager().getContributedEnvironment();
		IEnvironmentVariable[] variables = ce.getVariables(des);
		Arrays.sort(variables, new Comparator<IEnvironmentVariable>() {
			public int compare(IEnvironmentVariable o1, IEnvironmentVariable o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});

		// Remove entries which change per user
		Map<String, String> envVariables = new LinkedHashMap<String,String>();
		for (IEnvironmentVariable v : variables)
			envVariables.put(v.getName(), v.getValue());
		for (String excluded : EXCLUDED_CONFIG_ENVIRONMENT)
			envVariables.remove(excluded);
		if (!envVariables.isEmpty()) {
			print("Environment: ");
			for (Map.Entry<String, String> e : envVariables.entrySet())
				print("  " + e.getKey() + "=" + e.getValue());
		}
	}

	/**
	 * This method takes a tree of IOptions sorted root first and creates deltas based on differences between
	 * parent and child IOptions.
	 *
	 * The original options are actually stored as a map from option ID -> IOption to allow easy lookup of
	 * the option in the parent
	 * @return Path -> Set of IOption SettingDeltas
	 */
	private Map<IPath, LinkedHashSet<SettingDelta<IOption>>> createDeltaTree(Map<IPath, LinkedHashMap<String, IOption>> originalTree) {
		TreeMap<IPath, LinkedHashSet<SettingDelta<IOption>>> deltaTree = new TreeMap<IPath, LinkedHashSet<SettingDelta<IOption>>>(new PathTreeUtils.ParentFirstPathComparator());

		for (Map.Entry<IPath, LinkedHashMap<String, IOption>> e : originalTree.entrySet()) {
			IPath path = e.getKey();

			LinkedHashMap<String, IOption> addedSet = new LinkedHashMap<String, IOption>(e.getValue()); // Clone so we don't alter the original set
			LinkedHashMap<String, IOption> parentSet = null;
			LinkedHashMap<String, IOption> modifiedSet = new LinkedHashMap<String, IOption>();
			if (!path.removeLastSegments(1).equals(path)) {
				IPath parent = path;
				do {
					parent = parent.removeLastSegments(1);
					parentSet = originalTree.get(parent);
				} while (parentSet == null && !parent.equals(new Path("")));
			}
			if (parentSet == null)
				parentSet = new LinkedHashMap<String, IOption>();
			// Initialize the parent's set
			parentSet = new LinkedHashMap<String, IOption>(parentSet); // Clone so we don't alter the original set

			// Find the items that have been modified
			LinkedHashSet<String> shared = new LinkedHashSet<String>();
			shared.addAll(addedSet.keySet());
			shared.retainAll(parentSet.keySet());
			for (String id : shared) {
				// Remove from par
				IOption parentOpt = parentSet.remove(id);
				IOption thisOpt = addedSet.remove(id);
				// If same value in parent and child, then continue
				if (parentOpt.getValue().equals(thisOpt.getValue()))
					continue;
				// Otherwise add to the modified set
				modifiedSet.put(id, thisOpt);
			}

			deltaTree.put(path, new LinkedHashSet<SettingDelta<IOption>>());

			// Add the modified deltas
			for (IOption modified : modifiedSet.values()) {
				SettingDelta<IOption> d = new SettingDelta<IOption>(Change.MODIFIED, modified);
				deltaTree.get(path).add(d);
			}

			// Put in the added deltas
			for (IOption added : addedSet.values()) {
				SettingDelta<IOption> d = new SettingDelta<IOption>(Change.ADDED, added);
				deltaTree.get(path).add(d);
			}

			// Add the deleted deltas
			if (verbose)
				for (IOption removed : parentSet.values()) {
					SettingDelta<IOption> d = new SettingDelta<IOption>(Change.REMOVED, removed);
					deltaTree.get(path).add(d);
				}
		}

		return deltaTree;
	}

	private void printConfiguration(ICConfigurationDescription cfgDesc) {
		// Map from Path => (Map from String optionID => IOption)
		TreeMap<IPath, LinkedHashMap<String, IOption>> pathToolOptions = new TreeMap<IPath, LinkedHashMap<String, IOption>>(new PathTreeUtils.ParentFirstPathComparator());
		LinkedHashMap<String, IOption> toolOptions =  new LinkedHashMap<String, IOption>();

		// Get the build configuration for this cfgDesc
		IConfiguration cfg = ManagedBuildManager.getConfigurationForDescription(cfgDesc);

		{
			// Print the toolchain options
			List<IOption> toolchainOptions = new LinkedList<IOption>();
			for (IOption option : cfg.getToolChain().getOptions())
				toolchainOptions.add(option);
			printToolChainOptions(toolchainOptions);
		}

		// Print the excluded resources
		{
			boolean sourcePathsTitlePrinted = false;
			for (ICSourceEntry sourceEntry : cfgDesc.getSourceEntries()) {
				IPath source = sourceEntry.getFullPath();
				Set<IPath> excluded = new TreeSet<IPath>(new PathTreeUtils.NaturalPathSorter());
				excluded.addAll(Arrays.asList(sourceEntry.getExclusionPatterns()));

				if (commonSourceAndExclusions.containsKey(source)) {
					// If the commons source and exclusions contains all our exclusions
					// nothing to print
					if (commonSourceAndExclusions.get(source).equals(excluded))
						continue;
				}

				// else print the
				if (!sourcePathsTitlePrinted) {
					print("Source Paths:");
					sourcePathsTitlePrinted = true;
				}
				StringBuilder sb = new StringBuilder("  " + source);
				if (!excluded.isEmpty()) {
					sb.append(" [Excluded:");
					for (IPath exc : excluded)
						sb.append(" ").append(exc.toString()).append(",");
					sb.setLength(sb.length() - 1);
					sb.append("]");
				}
				print (sb.toString());
			}
		}

		// Don't include 'filtered' tools - such as linker in .a build
		Set<String> filteredTools = new HashSet<String>();
		for (ITool tool : cfg.getFilteredTools())
			filteredTools.add(tool.getName());

		// Get configuration level options
		pathToolOptions.put(new Path(""), toolOptions);
		for (ITool tool : cfg.getTools()) {
			if (!filteredTools.contains(tool.getName()))
				continue;
			for (IOption option : tool.getOptions())
				if (isOptionApplicable(option))
					toolOptions.put(getOptionSuperID(option.getId()), option);
		}

		// Push language (include / define) setting entries up the tree if the user wants to do so...
		TreeMap<IPath, LinkedHashSet<ICLanguageSettingEntry>> languageSettings = new TreeMap<IPath, LinkedHashSet<ICLanguageSettingEntry>>(new PathTreeUtils.DeepestFirstPathComparator());
		if (pushLangSettingsUpTree) {
			for (ICResourceDescription resDesc : cfgDesc.getResourceDescriptions()) {
				if (!languageSettings.containsKey(resDesc.getPath()))
					languageSettings.put(resDesc.getPath(), new LinkedHashSet<ICLanguageSettingEntry>());
				LinkedHashSet<ICLanguageSettingEntry> langSet = languageSettings.get(resDesc.getPath());
				if (resDesc instanceof ICFolderDescription)
					for (ICLanguageSetting ls : ((ICFolderDescription)resDesc).getLanguageSettings()) {
						langSet.addAll(ls.getSettingEntriesList(ICLanguageSettingEntry.INCLUDE_PATH));
						langSet.addAll(ls.getSettingEntriesList(ICLanguageSettingEntry.MACRO));
					}
				else if (resDesc instanceof ICFileDescription) {
					langSet.addAll(((ICFileDescription)resDesc).getLanguageSetting().getSettingEntriesList(ICLanguageSettingEntry.INCLUDE_PATH));
					langSet.addAll(((ICFileDescription)resDesc).getLanguageSetting().getSettingEntriesList(ICLanguageSettingEntry.MACRO));
				}
			}
			// Push the common settings up the tree
			PathTreeUtils.propogateUpwards(languageSettings);
		}

		// Get the overridden options on all child resource configurations
		for (IResourceInfo resInfo : cfg.getResourceInfos()) {
			if (!resInfo.getResourceData().hasCustomSettings())
				continue;
			if (!pathToolOptions.containsKey(resInfo.getPath()))
				pathToolOptions.put(resInfo.getPath(), new LinkedHashMap<String, IOption>());
			toolOptions = pathToolOptions.get(resInfo.getPath());

			for (ITool tool : resInfo.getTools()) {
				// Ignore tools not enabled in this configuration
				if (!filteredTools.contains(tool.getName()))
					continue;
				for (IOption option : tool.getOptions())
					if (isOptionApplicable(option))
						toolOptions.put(getOptionSuperID(option.getId()), option);
			}
		}
		// Create the tree of deltas from the parent
		Map<IPath, LinkedHashSet<SettingDelta<IOption>>> deltaTree = createDeltaTree(pathToolOptions);
		print("");
		prettyFormatAll(cfg, deltaTree, languageSettings);
	}

	/**
	 * Returns whether the specified option is applicable for the given config / resource description
	 * @param buildObject an IConfiguration or Resource description
	 * @param option the option in question
	 * @return
	 */
	private boolean isOptionApplicable(IOption option) {
		// We ignore things that aren't specialised...
		if (superID.matcher(option.getId()).matches() &&
				// If pushing up tree, ignore -D, -U, -I settings
				(!pushLangSettingsUpTree || !option.getCommand().matches("-[DUI]")))
			return true;
		return false;
	}

	private final Pattern superID = Pattern.compile("(.*?)[.][0-9.]+$");
	/**
	 * Returns the option ID with the random termination string removed,
	 * or returns the original passed in optionID
	 * @param optionID
	 * @return
	 */
	private String getOptionSuperID(String optionID) {
		Matcher m = superID.matcher(optionID);
		if (m.matches())
			return m.group(1);
		return optionID;
	}

	/**
	 * Print the Resource -> Settings delta tree
	 * @param tree
	 */
	private void prettyFormatAll(IConfiguration cfg, Map<IPath, LinkedHashSet<SettingDelta<IOption>>> tree, TreeMap<IPath, LinkedHashSet<ICLanguageSettingEntry>> languageSettings) {
		TreeSet<IPath> paths = new TreeSet<IPath>(new PathTreeUtils.NaturalPathSorter());
		paths.addAll(tree.keySet());
		paths.addAll(languageSettings.keySet());
		for (IPath path : paths) {
			if ((!tree.containsKey(path) || tree.get(path).isEmpty()) &&
					(!languageSettings.containsKey(path) || languageSettings.get(path).isEmpty()))
					continue;

			// Print the Path
			StringBuilder sb = new StringBuilder("Path: ");
			if (path.equals(Path.EMPTY))
				sb.append("<root>");
			else
				sb.append(path);
			print(sb);

			// Print the settings:
			pushIndent();
			if (tree.containsKey(path))
				printAllOptionValuesSettings(tree, path);
			if (languageSettings.containsKey(path))
				printAllLanguageSettings(path, languageSettings.get(path));
			popIndent();
		}
	}

	/**
	 * Utility method to output the passed in String
	 * Appending a new line
	 * @param toPrint
	 */
	private void print(Object toPrint) {
		try {
			if (toPrint.toString().trim().length() != 0)
				for (int i = 0 ; i < indentation; i++)
					writer.write(" ");
			writer.write(toPrint.toString() + "\n");
		} catch (IOException e) {
			Activator.log(e);
		}
//		System.out.print(toPrint);
	}

	private void printAllLanguageSettings(IPath path, LinkedHashSet<ICLanguageSettingEntry> langSettings) {
		LinkedHashSet<ICLanguageSettingEntry> includes = new LinkedHashSet<ICLanguageSettingEntry>();
		LinkedHashSet<ICLanguageSettingEntry> defines = new LinkedHashSet<ICLanguageSettingEntry>();
		for (ICLanguageSettingEntry lse : langSettings)
			if (lse.getKind() == ICLanguageSettingEntry.INCLUDE_PATH)
				includes.add(lse);
			else if (lse.getKind() == ICLanguageSettingEntry.MACRO)
				defines.add(lse);
			else
				Activator.log("lse type: " + lse.getKind() + " unhandled");
		StringBuilder sb = new StringBuilder("  + Includes [");
		int indent = indentation;

		// Reset
		int i = 0;
		for (ICLanguageSettingEntry def : includes) {
			int length = sb.length();
			sb.append(def.getName() + " ");
			if (++i < includes.size()) {
				print(sb);
				if (i == 1)
					indentation = indent + length;
				sb.setLength(0);
			}
		}
		sb.append("]");
		print(sb);

		sb = new StringBuilder("  + Defines [");
		indentation = indent;
		i = 0;
		for (ICLanguageSettingEntry def : defines) {
			int length = sb.length();
			sb.append(def.getName() + "=" + def.getValue() + " ");
			if (++i < defines.size()) {
				print(sb);
				if (i == 1)
					indentation = indent + length;
				sb.setLength(0);
			}
		}
		sb.append("]");
		print(sb);
		indentation = indent;
	}

	/**
	 * Print toolchain settings
	 */
	private void printToolChainOptions(Collection<IOption> options) {
		if (options.isEmpty())
			return;
		print("Toolchain Options: ");
		pushIndent();
		for (IOption option : options) {
			StringBuilder sb = new StringBuilder();
			try {
				sb.append(option.getName() + ": ");
				if (option.getValueType() == IOption.ENUMERATED)
					sb.append(option.getEnumName((String)option.getValue()));
				else if (!"".equals(option.getCommand()))
					sb.append(option.getCommand());
				else
					sb.append(option.getValue());
			} catch (BuildException e) {
				Activator.log(e);
			}
			print (sb);
		}
		popIndent();
	}

	/**
	 * Why oh why doesn't the API let you fetch all language setting entries?
	 * @param clse
	 */
	private void printAllOptionValuesSettings(Map<IPath, LinkedHashSet<SettingDelta<IOption>>> tree, IPath path) {
		LinkedHashSet<SettingDelta<IOption>> cls = tree.get(path);
		SettingDelta.Change lastChange = null;
		IHoldsOptions optionHolder = null;
		for (SettingDelta<IOption> delta : cls) {
			StringBuilder sb = new StringBuilder();
			IOption ent = delta.item;
			lastChange = delta.change;

			// Print the tool name and command
			if (optionHolder == null || ent.getOptionHolder() != optionHolder) {
				optionHolder = ent.getOptionHolder();
				if (optionHolder instanceof ITool) {
					print("[ " + optionHolder.getName() + ": " + ((ITool)optionHolder).getToolCommand() + " ]");
				}
			}

			switch (lastChange) {
			case ADDED:
				sb.append("  + ");
				break;
			case REMOVED:
				sb.append("  - ");
				break;
			case MODIFIED:
				sb.append("  ! ");
				break;
			}

			// Cache the indentation so we can fix it up later
			final int indent = indentation;

			try {
				int type = ent.getValueType();

				// Print the tool name
				sb.append(ent.getName()).append(": ");
				if (ent.getValueType() == IOption.ENUMERATED) {
					// enumeration type requires looking up the value...
					sb.append(ent.getEnumName((String)ent.getValue()));
				} else {
					sb.append(ent.getCommand());
					sb.append("  (");

					final int entryKind = ManagedBuildManager.optionTypeToEntryKind(type);

					// If verbose || we're not printing a list, then just print the item.
					if (verbose || ent.getBasicValueType() != IOption.STRING_LIST) {
						Set<String> val = new LinkedHashSet<String>();
						if (ent.getBasicValueType() == IOption.STRING_LIST)
							val.addAll((Collection<String>)ent.getValue());
						else
							val.add(ent.getValue().toString());
						if (referencedBits.containsKey(entryKind))
							val.removeAll(referencedBits.get(entryKind));
						if (val.isEmpty())
							sb.append("<none>");
						else {
							int i = 0;
							for (String v : val) {
								int length = sb.length();
								sb.append(v);
								if (++i < val.size()) {
									print (sb);
									if (i == 1)
										indentation = indent + length;
									sb.setLength(0);
								}
							}
						}
						sb.append(")");
					} else {
						// Attemt to remove duplication from the string lists
						IPath parent = path;
						do {
							parent = parent.removeLastSegments(1);
						} while (!tree.containsKey(parent));

						IOption parentOption = null;
						if (!parent.equals(path)) {
							LinkedHashSet<SettingDelta<IOption>> parentSettingsDelta = tree.get(parent);
							for (SettingDelta<IOption> pdelta : parentSettingsDelta) {
								if (getOptionSuperID(ent.getId()).equals(getOptionSuperID(pdelta.item.getId()))) {
									parentOption = pdelta.item;
									break;
								}
							}
						}

						sb.append("[");
						LinkedHashSet<String> removed = new LinkedHashSet<String>();
						// Add the options from the 'parent'
						if (parentOption != null)
							removed.addAll((Collection)(parentOption.getValue()));
						// We expect the referenced bits to be in there ...
						if (referencedBits.containsKey(entryKind))
							removed.addAll(referencedBits.get(entryKind));
						LinkedHashSet<String> thisBits = new LinkedHashSet<String>((Collection)(ent.getValue()));
						LinkedHashSet<String> added = new LinkedHashSet<String>(thisBits);
						added.removeAll(removed);
						removed.removeAll(thisBits);

						int i = 0;
						for (String add : added) {
							int length = sb.length() + indent;
							sb.append("+ ").append(add).append(", ");
							if (++i < added.size() || !removed.isEmpty()) {
								print(sb);
								if (i == 1)
									indentation = length;
								sb.setLength(0);
							}
						}
						i = 0;
						for (String remove : removed) {
							int length = sb.length() + indent;
							sb.append("- ").append(remove).append(", ");
							if (++i < removed.size()) {
								print(sb);
								if (i == 1 && added.isEmpty())
									indentation = length;
								sb.setLength(0);
							}
						}
						if (!removed.isEmpty() || !added.isEmpty())
							sb.setLength(sb.length() - 2);

						sb.append("]");
						sb.append(")");
					}
				}
			} catch (BuildException e) {
				Activator.log(e);
			}
			print(sb);
			// restore indentation
			indentation = indent;
		}
	}

	/**
	 * Nicked from buildEntryStorage
	 *
	 * @param pathName
	 * @param nullIfNone
	 * @return
	 */
	private static String doubleQuotePath(String pathName, boolean nullIfNone)	{
		/* Trim */
		pathName = pathName.trim();

		/* Check if path is already double-quoted */
		boolean bStartsWithQuote = pathName.indexOf('"') == 0;
		boolean bEndsWithQuote = pathName.lastIndexOf('"') == pathName.length() - 1;

		boolean quoted = false;

		/* Check for spaces, backslashes or macros */
		int i = pathName.indexOf(' ') + pathName.indexOf('\\')
			+ pathName.indexOf("${"); //$NON-NLS-1$

		/* If indexof didn't fail all three times, double-quote path */
		if (i != -3) {
			if (!bStartsWithQuote){
				pathName = "\"" + pathName; //$NON-NLS-1$
				quoted = true;
			}
			if (!bEndsWithQuote){
				pathName = pathName + "\""; //$NON-NLS-1$
				quoted = true;
			}
		}

		if(quoted)
			return pathName;
		return nullIfNone ? null : pathName;
	}


}
