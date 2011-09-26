/*******************************************************************************
 * Copyright (c) 2007, 2011 Intel Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Intel Corporation - Initial API and implementation
 * IBM Corporation
 * Dmitry Kozlov (CodeSourcery) - Build error highlighting and navigation
 *                                Save build output (bug 294106)
 * Andrew Gvozdev (Quoin Inc)   - Saving build output implemented in different way (bug 306222)
 * Broadcom Corporation
 *******************************************************************************/
package org.eclipse.cdt.managedbuilder.internal.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.ConsoleOutputStream;
import org.eclipse.cdt.core.ICommandLauncher;
import org.eclipse.cdt.core.IMarkerGenerator;
import org.eclipse.cdt.core.ProblemMarkerInfo;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.model.ICModelMarker;
import org.eclipse.cdt.core.resources.ACBuilder;
import org.eclipse.cdt.core.resources.IConsole;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.internal.core.resources.ResourceLookup;
import org.eclipse.cdt.managedbuilder.buildmodel.BuildDescriptionManager;
import org.eclipse.cdt.managedbuilder.buildmodel.IBuildDescription;
import org.eclipse.cdt.managedbuilder.core.IBuilder;
import org.eclipse.cdt.managedbuilder.core.IConfiguration;
import org.eclipse.cdt.managedbuilder.core.IManagedBuildInfo;
import org.eclipse.cdt.managedbuilder.core.IManagedProject;
import org.eclipse.cdt.managedbuilder.core.ManagedBuildManager;
import org.eclipse.cdt.managedbuilder.core.ManagedBuilderCorePlugin;
import org.eclipse.cdt.managedbuilder.internal.buildmodel.BuildStateManager;
import org.eclipse.cdt.managedbuilder.internal.buildmodel.IConfigurationBuildState;
import org.eclipse.cdt.managedbuilder.internal.buildmodel.IProjectBuildState;
import org.eclipse.cdt.managedbuilder.macros.BuildMacroException;
import org.eclipse.cdt.managedbuilder.macros.IBuildMacroProvider;
import org.eclipse.cdt.managedbuilder.makegen.IManagedBuilderMakefileGenerator;
import org.eclipse.cdt.managedbuilder.makegen.IManagedBuilderMakefileGenerator2;
import org.eclipse.cdt.newmake.core.IMakeBuilderInfo;
import org.eclipse.core.resources.IBuildConfiguration;
import org.eclipse.core.resources.IBuildContext;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;

public class CommonBuilder extends ACBuilder {

	public final static String BUILDER_ID = ManagedBuilderCorePlugin.getUniqueIdentifier() + ".genmakebuilder"; //$NON-NLS-1$
	private static final String BUILD_FINISHED = "ManagedMakeBuilder.message.finished";	//$NON-NLS-1$
	private static final String CONSOLE_HEADER = "ManagedMakeBuilder.message.console.header";	//$NON-NLS-1$
	private static final String ERROR_HEADER = "GeneratedmakefileBuilder error [";	//$NON-NLS-1$
	private static final String NEWLINE = System.getProperty("line.separator");	//$NON-NLS-1$
	private static final String TRACE_FOOTER = "]: ";	//$NON-NLS-1$
	private static final String TYPE_CLEAN = "ManagedMakeBuilder.type.clean";	//$NON-NLS-1$
	private static final String TYPE_INC = "ManagedMakeBuider.type.incremental";	//$NON-NLS-1$
	public static boolean VERBOSE = false;
	private boolean fBuildErrorOccured = false;

	// Wraps all the configuration information required by a build
	private static class CfgBuildInfo {
		private final IProject fProject;
		private final IManagedBuildInfo fBuildInfo;
		private final IConfiguration fCfg;
		private final IBuilder fBuilder;
		private final IConsole fConsole;

		CfgBuildInfo(IBuilder builder, IConsole console) {
			this.fBuilder = builder;
			this.fCfg = builder.getParent().getParent();
			this.fProject = this.fCfg.getOwner().getProject();
			this.fBuildInfo = ManagedBuildManager.getBuildInfo(this.fProject);
			this.fConsole = console;
		}

		public IProject getProject(){
			return fProject;
		}

		public IConsole getConsole(){
			return fConsole;
		}

		public IBuilder getBuilder(){
			return fBuilder;
		}

		public IConfiguration getConfiguration(){
			return fCfg;
		}

		public IManagedBuildInfo getBuildInfo(){
			return fBuildInfo;
		}

	}

	private static class ResourceDeltaVisitor implements IResourceDeltaVisitor {
		private String buildGoalName;
		private final IProject project;
		private final IPath buildPaths[];
		private boolean fullBuildNeeded = false;
		private final List<String> reservedNames;

		public ResourceDeltaVisitor(IConfiguration cfg, IConfiguration allConfigs[]) {
			this.project = cfg.getOwner().getProject();
			buildPaths = new IPath[allConfigs.length];
			for(int i = 0; i < buildPaths.length; i++){
				buildPaths[i] = ManagedBuildManager.getBuildFullPath(allConfigs[i], allConfigs[i].getBuilder());
			}
			String ext = cfg.getArtifactExtension();
			//try to resolve build macros in the build artifact extension
			try{
				ext = ManagedBuildManager.getBuildMacroProvider().resolveValueToMakefileFormat(
						ext,
						"", //$NON-NLS-1$
						" ", //$NON-NLS-1$
						IBuildMacroProvider.CONTEXT_CONFIGURATION,
						cfg);
			} catch (BuildMacroException e){
			}

			String name = cfg.getArtifactName();
			//try to resolve build macros in the build artifact name
			try{
				String resolved = ManagedBuildManager.getBuildMacroProvider().resolveValueToMakefileFormat(
						name,
						"", //$NON-NLS-1$
						" ", //$NON-NLS-1$
						IBuildMacroProvider.CONTEXT_CONFIGURATION,
						cfg);
				if((resolved = resolved.trim()).length() > 0)
					name = resolved;
			} catch (BuildMacroException e){
			}

			if (ext.length() > 0) {
				buildGoalName = cfg.getOutputPrefix(ext) + name + IManagedBuilderMakefileGenerator.DOT + ext;
			} else {
				buildGoalName = name;
			}
			reservedNames = Arrays.asList(new String[]{".cdtbuild", ".cdtproject", ".project"});	//$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}

		private boolean isGeneratedResource(IResource resource) {
			// Is this a generated directory ...
			IPath path = resource.getFullPath();
			for (IPath buildPath : buildPaths) {
				if(buildPath != null && buildPath.isPrefixOf(path)){
					return true;
				}
			}
			return false;
		}

		private boolean isProjectFile(IResource resource) {
			return reservedNames.contains(resource.getName());
		}

		public boolean shouldBuildFull() {
			return fullBuildNeeded;
		}

		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource resource = delta.getResource();
			// If the project has changed, then a build is needed and we can stop
			if (resource != null && resource.getProject() == project) {
				switch(resource.getType()){
				case IResource.FILE:
					String name = resource.getName();
					if ((!name.equals(buildGoalName) &&
						// TODO:  Also need to check for secondary outputs
						(resource.isDerived() ||
						(isProjectFile(resource)) ||
						(isGeneratedResource(resource))))) {
						// The resource that changed has attributes which make it uninteresting,
						 // so don't do anything
					} else {
						//  TODO:  Should we do extra checks here to determine if a build is really needed,
						//         or do you just do exclusion checks like above?
						//         We could check for:
						//         o  The build goal name
						//         o  A secondary output
						//         o  An input file to a tool:
						//            o  Has an extension of a source file used by a tool
						//            o  Has an extension of a header file used by a tool
						//            o  Has the name of an input file specified in an InputType via:
						//               o  An Option
						//               o  An AdditionalInput
						//
						//if (resourceName.equals(buildGoalName) ||
						//	(buildInfo.buildsFileType(ext) || buildInfo.isHeaderFile(ext))) {

						// We need to do an incremental build, at least
						if (delta.getKind() == IResourceDelta.REMOVED) {
							// If a meaningful resource was removed, then force a full build
							// This is required because an incremental build will trigger make to
							// do nothing for a missing source, since the state after the file
							// removal is up-to-date, as far as make is concerned
							// A full build will clean, and ultimately trigger a relink without
							// the object generated from the deleted source, which is what we want
							fullBuildNeeded = true;
							// There is no point in checking anything else since we have
							// decided to do a full build anyway
							break;
						}

						//}
					}

					return false;
				}
			}
			return true;
		}
	}

	public CommonBuilder() {
	}


	/*
	 * (non-Javadoc)
	 * @see IncrementalProjectBuilder#build(int, Map, IProgressMonitor)
	 */
	@Override
	protected IProject[] build(int kind, @SuppressWarnings("rawtypes") Map argsMap, IProgressMonitor monitor) throws CoreException {
		@SuppressWarnings("unchecked")
		Map<String, String> args = argsMap;
		if (DEBUG_EVENTS)
			printEvent(kind, args);

		final IProject project = getProject();

		// Create the console for the build
		IConsole console = CCorePlugin.getDefault().getConsole();
		console.start(getProject());

		if(!isCdtProjectCreated(project)) {
			outputTrace(project.getName(), "Not building as project: " + project.getName() + " is still being created"); //$NON-NLS-1$ //$NON-NLS-2$
			return buildComplete(monitor);
		}

		if(VERBOSE)
			outputTrace(project.getName(), ">>build requested, type = " + kind); //$NON-NLS-1$

		IManagedBuildInfo info = ManagedBuildManager.getBuildInfo(project);
		IManagedProject mProj = info.getManagedProject();

		// Fetch the configuration
		IConfiguration cfg = mProj.getConfigurationByName(getBuildConfig().getName());
		if (cfg == null) {
			// If we don't know about the configuration, nothing to do. We don't have a configuration with that ID!
			outputTrace(project.getName(), "CommonBuilder: Configuration: " +  getBuildConfig() + " not found!"); //$NON-NLS-1$ //$NON-NLS-2$
			return new IProject[0];
			//			throw new CoreException(new Status(Status.ERROR, ManagedBuilderCorePlugin.PLUGIN_ID, "Couldn't build configuration \"" 
			//					+ getBuildConfiguration() + "\" as no CDT configuration found matching that ID!"));
		} else
			outputTrace(project.getName(), "CommonBuilder: Configuration: " +  cfg.getName() + " found for: " + getBuildConfig()); //$NON-NLS-1$ //$NON-NLS-2$			

		IBuilder builders[] = ManagedBuilderCorePlugin.createBuilders(project, cfg, args);
		boolean managedBuildOn = cfg.isManagedBuildOn();
		// Check the status of this projects builders
		MultiStatus status = checkBuilders(builders, cfg);
		if(status.getSeverity() != IStatus.OK)
			throw new CoreException(status);

		monitor.beginTask("", builders.length); //$NON-NLS-1$

		// Iterate over the builders, running them if necessary
		for (IBuilder builder : builders) {
			// Do we need to run the builder for this build invocation?
			//
			// If this is a managed build controlled configuration, decide whether a build is necessary in this case
			// For non Managed build configurations we always run the builder
			if (managedBuildOn) {
				// First thing we do is work out whether anything interesting has happened since the last build by checking the delta/
				// If not then nothing to do.
				if (!needsBuild(kind, cfg, builder)) {
					outputTrace(project.getName(), "!shouldBuild() - no relevant changes found" + kind); //$NON-NLS-1$
					// echo to the console that build considered but not performed
					emitMessage(console, "Nothing to build for: " + getProject().getName() + "/" + cfg.getName());
					continue;
				}

				// Second we work out if this builder's output is required by any other projects about to be built.
				// If the answer is no, then we want to keep the delta since last build, but shortCircuit this particular invocation
				if (canPostpone(kind)) {
					// The builder was not run, even though the delta may indicate there
					// were changes, so request that the delta is not updated
					outputTrace(project.getName(), "canPostpone() - build output not needed on this build"); //$NON-NLS-1$
					rememberLastBuiltState();
					continue;
				}
			}

			// Now call the builder
			build(kind, new CfgBuildInfo(builder, console), new SubProgressMonitor(monitor, 1));
		}

		if (fBuildErrorOccured) {
			for (IBuilder builder : builders)
				removeBuildArtifact(builder, cfg);
		}

		// Check if any project buildConfigs have been built that depend on the one currently being built.
		// If so, a cycle exists in the build and we need to repeatedly build until nothing changes.
		//TODO: This requires the workspace to be refreshed so that changes made by previous builders are included in the delta
		//Set<IBuildConfiguration> dependencies = new HashSet<IBuildConfiguration>(Arrays.asList(getProject().getReferencingProjectVariants(getProjectVariant())));
		//Set<IBuildConfiguration> alreadyBuilt = new HashSet<IBuildConfiguration>(Arrays.asList(getContext().getAllReferencedProjectVariants()));
		//dependencies.retainAll(alreadyBuilt);
		//if (dependencies.size() > 0)
		//	needRebuild();

		return buildComplete(monitor);
	}

	/**
	 * Checks if this project configuration should be built based on changes the passed-in configuration.
	 *
	 * Checks the resource delta for 'interesting' changes in the past in build configuration which might affect
	 * this project's build output.
	 *
	 * @param buildConfiguration the referenced build configuration which may cause us to need to build
	 */
	private boolean needsBuild(final IBuildConfiguration buildConfiguration) throws CoreException {
		IProject project = buildConfiguration.getProject();

		IResourceDelta delta = getDelta(project);
		if (delta == null) {
			outputTrace(project.toString(), "No build delta found; needsBuild() => true"); //$NON-NLS-1$
			return true;
		}

		final boolean shouldBuild[] = new boolean[] {false};

		// Visity which organises a build if anything has changed in a non-derived, source directory.
		IResourceDeltaVisitor visitor = new IResourceDeltaVisitor() {

			// Ignore changes in project description files:
			//   - .project has no effect on our build
			//   - .cproject file changes should be contained on the given build configuration. config.needsBuild()
			private Set<String> projectFiles = new HashSet<String>(Arrays.asList(new String[]{".cproject", ".project"})); //$NON-NLS-1$ //$NON-NLS-2$

			public boolean visit(IResourceDelta delta) throws CoreException {
				// Already decided to build => no more need to recurse.
				if (shouldBuild[0])
					return false;
				// Explore project changes in more detail
				if (delta.getFullPath().equals(buildConfiguration.getProject().getFullPath()))
					return true;
				// Ignore project description and derived file changes
				if (delta.getResource().isDerived() || projectFiles.contains(delta.getResource().getName()))
					return false;
				// TODO just check changed files under source direcotries?
				// Seen an interesting change, so we need to build
				outputTrace(delta.toString(), "Resource " +  delta.getResource() + " changed; needsBuild() => true"); //$NON-NLS-1$
				shouldBuild[0] = true;
				return false;
			}
		};

		// Check whether anything has changed in a non-derived directory
		delta.accept(visitor);
		if (shouldBuild[0])
			return true;
		//		// TODO consider source directories
		//		ICConfigurationDescription cfgDesc = CCorePlugin.getDefault().getProjectDescription(buildConfiguration.getProject(), false).getConfigurationById(buildConfiguration.getConfigurationId());
		//		for (ICSourceEntry sourceEntry : cfgDesc.getSourceEntries()) {
		//			if (delta.findMember(sourceEntry.getFullPath().removeFirstSegments(1)) != null)
		//				return true;
		//		}

		// If the passed in build configuration is referenced from the build configuration being built
		// Check for changes in the output artifact.
		if (!buildConfiguration.equals(getBuildConfig())) {
			// If this isn't a static library (i.e. exe / .so or something we don't know about, then check artifact)
			//   && the referenced config is a static library ...
			if (!isStaticLibrary(getBuildConfig()) && isStaticLibrary(buildConfiguration)) {
				IConfiguration cfg = ManagedBuildManager.getBuildInfo(buildConfiguration.getProject()).getManagedProject().
						getConfigurationByName(buildConfiguration.getName());
				IBuilder b = cfg.getBuilder();
				IResource artifact = getBuildArtifact(b, cfg);
				if (artifact != null && delta.findMember(artifact.getFullPath().removeFirstSegments(1)) != null) {
					outputTrace(delta.toString(), "needsBuild(): Referenced Library changed: " + artifact.getFullPath()); //$NON-NLS-1$
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Determines whether the builder should be run based on build context and resource
	 * changes. If resource changes are ignored due to 'build only when there are resource changes'
	 * being disabled then we return true.
	 */
	private boolean needsBuild(int kind, IConfiguration cfg, IBuilder builder) throws CoreException {
		// Check if resource changes shouldn't be checked
		if (!buildConfigResourceChanges())
			return true;

		// Does the configuration think that something has changed?
		if (cfg.needsRebuild() || cfg.needsFullRebuild()) {
			outputTrace(getBuildConfig().toString(), "needsBuild(): rebuild requested"); //$NON-NLS-1$
			return true;
		}

		// Check deltas to see if a build is required
		if (kind != IncrementalProjectBuilder.FULL_BUILD) {
			// If the artifact doesn't exist, then we need to build.
			IResource artifact = getBuildArtifact(builder, cfg);
			if (artifact == null || !artifact.exists()) {
				outputTrace(getBuildConfig().toString(), "needsBuild(): artifact not present"); //$NON-NLS-1$
				return true;
			}

			// Check for changes in the variant being built
			if (needsBuild(getBuildConfig())) {
				outputTrace(getBuildConfig().toString(), "needsBuild()"); //$NON-NLS-1$
				return true;
			}

			// Check for changes in the dependent configuration
			IBuildConfiguration[] refCfgs = getProject().getReferencedBuildConfigs(getBuildConfig().getName(), false);
			for (int i = 0; i < refCfgs.length; i++) {
				if (needsBuild(refCfgs[i])) {
					outputTrace(refCfgs[i].toString(), "needsBuild(): building " + getBuildConfig() + " as reference: " + refCfgs[i] + " changed"); //$NON-NLS-1$
					return true;
				}
			}

			outputTrace(getBuildConfig().toString(), "needsBuild(): returning false"); //$NON-NLS-1$
			return false;
		}

		// Always build if a full build is requested
		outputTrace(getBuildConfig().toString(), "needsBuild(): true as build kind " + kind); //$NON-NLS-1$
		return true;
	}

	/**
	 * If this builder is for a static library, and is not being built as a reference for
	 * an executable, the library does not need to be built in this _particular_ build loop invocation.
	 * This is an optimization to prevent rebuilding all referenced libraries all the time.
	 * At some point in the future the referenced library will be rebuilt.
	 * @return boolean indicating if we can short-circuit this particular build
	 */
	private boolean canPostpone(int kind) throws CoreException {
		// The context about why this build is being called
		IBuildContext context = getContext();

		// If this build configuration is in the list of requested build configurations, then we must build
		for (IBuildConfiguration config : context.getRequestedConfigs())
			if (config.equals(getBuildConfig())) {
				outputTrace(getProject().getName(), "canPostpone() - configuration " + getBuildConfig() + " explicitly built"); //$NON-NLS-1$ //$NON-NLS-2$
				return false;
			}

		// Check if we are building a static library. If so, only build it if it is not referenced
		// by any other projects being built (i.e. it is the top level build object), or ifgetReferencedBuildConfigurations
		// it is referenced by either an executable or a shared library that includes it.
		if (isStaticLibrary(getBuildConfig()) && context.getAllReferencingBuildConfigs().length != 0) {
			IBuildConfiguration[] refs = context.getAllReferencingBuildConfigs();
			boolean isReferencedByAnExectuable = false;
			for (IBuildConfiguration variant : refs) {
				// If exe or .so then we're building
				if (!isStaticLibrary(variant)) {
					// && the exe / .so directly references this build configuration
					Set<IBuildConfiguration> set = new HashSet<IBuildConfiguration>(
							Arrays.asList(variant.getProject().getReferencedBuildConfigs(variant.getName(), false)));
					if (set.contains(getBuildConfig())) {
						isReferencedByAnExectuable = true;
						outputTrace(getBuildConfig().toString(), "CommonBuilder: Referenced by " + variant + " so building..."); //$NON-NLS-1$ //$NON-NLS-2$
						break;
					}
				}
			}
			// Not referenced, we can short-circuit!
			if (!isReferencedByAnExectuable) {
				outputTrace(getBuildConfig().toString(), "CommonBuilder: Configuration not referenced by a build configuration, so canPostpone()!"); //$NON-NLS-1$ //$NON-NLS-2$			
				return true;
			}
		}

		// This isn't a .a, or it's referenced by an exe. Just build.
		return false;
	}

	/**
	 * @return true if the given project variant (configuration) produces a static library; false otherwise.
	 */
	private boolean isStaticLibrary(IBuildConfiguration buildConfiguration) {
		IManagedBuildInfo info = ManagedBuildManager.getBuildInfo(buildConfiguration.getProject());
		if (info == null)
			return false;
		IManagedProject mProj = info.getManagedProject();
		if (mProj == null)
			return false;
		IConfiguration cfg = mProj.getConfigurationByName(buildConfiguration.getName());
		if (cfg == null || cfg.getBuildArtefactType() == null)
			return false;
		return ManagedBuildManager.BUILD_ARTEFACT_TYPE_PROPERTY_STATICLIB.equals(cfg.getBuildArtefactType().getId());
	}

	// Wraps all the information that describes the status of a build
	private static class BuildStatus {
		private final boolean fManagedBuildOn;
		private boolean fRebuild;
		private boolean fBuild = true;
		private final List<String> fConsoleMessages = new ArrayList<String>();
		private IManagedBuilderMakefileGenerator fMakeGen;

		public BuildStatus(IBuilder builder) {
			fManagedBuildOn = builder.isManagedBuildOn();
		}

		public void setRebuild() {
			fRebuild = true;
		}

		public boolean isRebuild() {
			return fRebuild;
		}

		public boolean isManagedBuildOn() {
			return fManagedBuildOn;
		}

		public boolean isBuild() {
			return fBuild;
		}

		public void cancelBuild() {
			fBuild = false;
		}

		public List<String> getConsoleMessagesList() {
			return fConsoleMessages;
		}

		public IManagedBuilderMakefileGenerator getMakeGen() {
			return fMakeGen;
		}

		public void setMakeGen(IManagedBuilderMakefileGenerator makeGen) {
			fMakeGen = makeGen;
		}
	}

	/**
	 * Mark the monitor as done and return the projects we're interested in the delta for
	 * @param monitor
	 * @return IProject[] or interesting projects
	 * @throws CoreException
	 */
	private IProject[] buildComplete(IProgressMonitor monitor) throws CoreException {
		monitor.done();

		outputTrace(getProject().getName(), "<<end of build"); //$NON-NLS-1$

		// Return projects we directly reference.
		return getProject().getReferencedProjects();
	}

	/** Invokes a builder if the builder needs to be run */
	private void build(int kind, CfgBuildInfo bInfo, IProgressMonitor monitor) throws CoreException{
		outputTrace(bInfo.getProject().getName(), "building cfg " + bInfo.getConfiguration().getName() + " with builder " + bInfo.getBuilder().getName()); //$NON-NLS-1$ //$NON-NLS-2$
		IBuilder builder = bInfo.getBuilder();
		BuildStatus status = new BuildStatus(builder);

		if (!shouldBuild(kind, builder)) {
			return;
		}

		if (status.isBuild()) {
			IConfiguration cfg = bInfo.getConfiguration();

			if(status.isManagedBuildOn()){
				status = performPrebuildGeneration(kind, bInfo, status, monitor);
			}

			if(status.isBuild()){
				try {
					// remove all markers for this project
					removeAllMarkers(bInfo.getProject(), bInfo.getConfiguration().getName());

					// Invoke the build
					boolean isClean = builder.getBuildRunner().invokeBuild(
							kind,
							bInfo.getProject(),
							bInfo.getConfiguration(),
							builder,
							bInfo.getConsole(),
							this,
							this,
							monitor);

					if (isClean) {
						forgetLastBuiltState();
						cfg.setRebuildState(true);
					} else {
						if(status.isManagedBuildOn()){
							performPostbuildGeneration(kind, bInfo, status, monitor);
						}
						cfg.setRebuildState(false);
					}
				} catch(CoreException e){
					cfg.setRebuildState(true);
					throw e;
				}

				PropertyManager.getInstance().serialize(cfg);
			} else if(status.getConsoleMessagesList().size() != 0) {
				emitMessages(bInfo.getConsole(), status.getConsoleMessagesList());
			}
		}
		checkCancel(monitor);
	}

	/**
	 * Perform pre-build generation for make file builders.
	 * Generates files, such as the Makefiles, necessary to run the builder.
	 */
	private BuildStatus performPrebuildGeneration(int kind, CfgBuildInfo bInfo, BuildStatus buildStatus, IProgressMonitor monitor) throws CoreException {
		IBuilder builder = bInfo.getBuilder();
		if (builder.isInternalBuilder())
			return buildStatus;

		buildStatus = performCleanning(kind, bInfo, buildStatus, monitor);
		IManagedBuilderMakefileGenerator generator = builder.getBuildFileGenerator();
		if (generator != null) {
			initializeGenerator(generator, kind, bInfo, monitor);
			buildStatus.setMakeGen(generator);

			MultiStatus result = performMakefileGeneration(bInfo, generator, buildStatus, monitor);
			if (result.getCode() == IStatus.WARNING || result.getCode() == IStatus.INFO) {
				IStatus[] kids = result.getChildren();
				for (int index = 0; index < kids.length; ++index) {
					// One possibility is that there is nothing to build
					IStatus status = kids[index];
					if (status.getCode() == IManagedBuilderMakefileGenerator.NO_SOURCE_FOLDERS) {
						// Emit a message to the console indicating that there were no source files to build
						StringBuffer buf = new StringBuffer();
						String[] consoleHeader = new String[3];
						String configName = bInfo.getConfiguration().getName();
						String projName = bInfo.getProject().getName();
						if (kind == FULL_BUILD || kind == INCREMENTAL_BUILD) {
							consoleHeader[0] = ManagedMakeMessages.getResourceString(TYPE_INC);
						} else {
							consoleHeader[0] = new String();
							outputError(projName, "The given build type is not supported in this context");	//$NON-NLS-1$
						}
						consoleHeader[1] = configName;
						consoleHeader[2] = projName;
						buf.append(System.getProperty("line.separator", "\n"));	//$NON-NLS-1$	//$NON-NLS-2$
						buf.append(ManagedMakeMessages.getFormattedString(CONSOLE_HEADER, consoleHeader));
						buf.append(System.getProperty("line.separator", "\n"));	//$NON-NLS-1$	//$NON-NLS-2$
						buf.append(System.getProperty("line.separator", "\n"));	//$NON-NLS-1$	//$NON-NLS-2$
						buf.append(status.getMessage());
						buf.append(System.getProperty("line.separator", "\n"));  //$NON-NLS-1$//$NON-NLS-2$

						buildStatus.getConsoleMessagesList().add(buf.toString());
						buildStatus.cancelBuild();
					}
				}
			} else if (result.getCode() == IStatus.ERROR){
				StringBuffer buf = new StringBuffer();
				buf.append(ManagedMakeMessages.getString("CommonBuilder.23")).append(NEWLINE); //$NON-NLS-1$
				String message = result.getMessage();
				if(message != null && message.length() != 0){
					buf.append(message).append(NEWLINE);
				}

				buf.append(ManagedMakeMessages.getString("CommonBuilder.24")).append(NEWLINE); //$NON-NLS-1$
				message = buf.toString();
				buildStatus.getConsoleMessagesList().add(message);
				buildStatus.cancelBuild();
			}

			checkCancel(monitor);
		} else {
			buildStatus.cancelBuild();
		}

		return buildStatus;
	}

	/**
	 * Perform post-build generation for make builders. Regenerates Makefile dependencies
	 * if required.
	 */
	private BuildStatus performPostbuildGeneration(int kind, CfgBuildInfo bInfo, BuildStatus buildStatus, IProgressMonitor monitor) throws CoreException{
		IBuilder builder = bInfo.getBuilder();
		if (builder.isInternalBuilder())
			return buildStatus;

		if (buildStatus.isRebuild()) {
			buildStatus.getMakeGen().regenerateDependencies(false);
		}	else {
			buildStatus.getMakeGen().generateDependencies();
		}

		return buildStatus;
	}

	private BuildStatus performCleanning(int kind, CfgBuildInfo bInfo, BuildStatus status, IProgressMonitor monitor) throws CoreException {
		IConfiguration cfg = bInfo.getConfiguration();
		IProject curProject = bInfo.getProject();

		boolean makefileRegenerationNeeded = false;
		//perform necessary cleaning and build type calculation
		if(cfg.needsFullRebuild()){
			//configuration rebuild state is set to true,
			//full rebuild is needed in any case
			//clean first, then make a full build
			outputTrace(curProject.getName(), "config rebuild state is set to true, making a full rebuild");	//$NON-NLS-1$
			clean(bInfo, new SubProgressMonitor(monitor, IProgressMonitor.UNKNOWN));
			makefileRegenerationNeeded = true;
		} else {
			makefileRegenerationNeeded = cfg.needsRebuild();
			IBuildDescription des = null;

			IResourceDelta delta = kind == FULL_BUILD ? null : getDelta(curProject);
			if(delta == null)
				makefileRegenerationNeeded = true;
			if(cfg.needsRebuild() || delta != null){
				//use a build desacription model to calculate the resources to be cleaned
				//only in case there are some changes to the project sources or build information
				try{
					int flags = BuildDescriptionManager.REBUILD | BuildDescriptionManager.DEPFILES | BuildDescriptionManager.DEPS;
					if(delta != null)
						flags |= BuildDescriptionManager.REMOVED;

					outputTrace(curProject.getName(), "using a build description..");	//$NON-NLS-1$

					des = BuildDescriptionManager.createBuildDescription(cfg, getDelta(curProject), flags);

					BuildDescriptionManager.cleanGeneratedRebuildResources(des);
				} catch (Throwable e){
					//TODO: log error
					outputError(curProject.getName(), "error occured while build description calculation: " + e.getLocalizedMessage());	//$NON-NLS-1$
					//in case an error occured, make it behave in the old stile:
					if(cfg.needsRebuild()){
						//make a full clean if an info needs a rebuild
						clean(new SubProgressMonitor(monitor, IProgressMonitor.UNKNOWN));
						makefileRegenerationNeeded = true;
					}
					else if(delta != null && !makefileRegenerationNeeded){
						// Create a delta visitor to detect the build type
						ResourceDeltaVisitor visitor = new ResourceDeltaVisitor(cfg, bInfo.getBuildInfo().getManagedProject().getConfigurations());
						delta.accept(visitor);
						if (visitor.shouldBuildFull()) {
							clean(new SubProgressMonitor(monitor, IProgressMonitor.UNKNOWN));
							makefileRegenerationNeeded = true;
						}
					}
				}
			}
		}

		if(makefileRegenerationNeeded){
			status.setRebuild();
		}
		return status;
	}

	private MultiStatus performMakefileGeneration(CfgBuildInfo bInfo, IManagedBuilderMakefileGenerator generator, BuildStatus buildStatus, IProgressMonitor monitor) throws CoreException {
		// Need to report status to the user
		IProject curProject = bInfo.getProject();

		// Ask the makefile generator to generate any makefiles needed to build delta
		checkCancel(monitor);
		String statusMsg = ManagedMakeMessages.getFormattedString("ManagedMakeBuilder.message.update.makefiles", curProject.getName());	//$NON-NLS-1$
		monitor.subTask(statusMsg);

		MultiStatus result;
		if(buildStatus.isRebuild()){
			result = generator.regenerateMakefiles();
		} else {
			result = generator.generateMakefiles(getDelta(curProject));
		}

		return result;
	}

	private void initializeGenerator(IManagedBuilderMakefileGenerator generator, int kind, CfgBuildInfo bInfo, IProgressMonitor monitor){
		if(generator instanceof IManagedBuilderMakefileGenerator2){
			IManagedBuilderMakefileGenerator2 gen2 = (IManagedBuilderMakefileGenerator2)generator;
			gen2.initialize(kind, bInfo.getConfiguration(), bInfo.getBuilder(), monitor);
		} else {
			generator.initialize(bInfo.getProject(), bInfo.getBuildInfo(), monitor);
		}

	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.cdt.core.resources.ACBuilder#clean(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	protected void clean(IProgressMonitor monitor) throws CoreException {
		fBuildErrorOccured = false;

		if (DEBUG_EVENTS)
			printEvent(IncrementalProjectBuilder.CLEAN_BUILD, null);

		IProject curProject = getProject();

		if(!isCdtProjectCreated(curProject))
			return;

		// Only clean configurations actually requested by the user
		// However for a workspace clean, clean all configurations
		boolean found =  getContext().getRequestedConfigs().length == 0;
		for (IBuildConfiguration config : getContext().getRequestedConfigs())
			if (config.equals(getBuildConfig())) {
				found = true;
				break;
			}
		if (!found)
			return;

		// Create the console for the build
		IConsole console = CCorePlugin.getDefault().getConsole();
		console.start(getProject());

		// Clean every configuration, not just the active one
		IManagedBuildInfo info = ManagedBuildManager.getBuildInfo(getProject());
		IManagedProject mProj = info.getManagedProject();
		for (IConfiguration cfg : mProj.getConfigurations()) {
			IBuilder[] builders = ManagedBuilderCorePlugin.createBuilders(curProject, cfg, null);
			for (IBuilder builder : builders) {
				CfgBuildInfo bInfo = new CfgBuildInfo(builder, console);
				clean(bInfo, monitor);
			}
		}
	}

	/**
	 * Clean the given build configuration.
	 */
	private void clean(CfgBuildInfo bInfo, IProgressMonitor monitor) throws CoreException{
		if (shouldBuild(CLEAN_BUILD, bInfo.getBuilder())) {
			BuildStateManager bsMngr = BuildStateManager.getInstance();
			IProject project = bInfo.getProject();
			IConfiguration cfg = bInfo.getConfiguration();
			IProjectBuildState pbs = bsMngr.getProjectBuildState(project);
			IConfigurationBuildState cbs = pbs.getConfigurationBuildState(cfg.getId(), false);
			if(cbs != null){
				pbs.removeConfigurationBuildState(cfg.getId());
				bsMngr.setProjectBuildState(project, pbs);
			}

			bInfo.fBuilder.getBuildRunner().invokeBuild(
					CLEAN_BUILD,
					bInfo.getProject(),
					bInfo.getConfiguration(),
					bInfo.getBuilder(),
					bInfo.getConsole(),
					this,
					this,
					monitor);
		}
	}

	/**
	 * Remove the build artifact for the given builder and configuration. This is
	 * called when a build fails, so that an incomplete artifact is not left in the
	 * builders output directory.
	 */
	private void removeBuildArtifact(IBuilder builder, IConfiguration cfg) throws CoreException {
		IResource res = getBuildArtifact(builder, cfg);
		if (res != null)
			res.delete(true, null);
	}

	/**
	 * Return the IResource build artifact for the builder.
	 * 
	 * @param builder Builder
	 * @param cfg Build configuration
	 * @return IResource or null
	 * @throws CoreException if discovering the build artifact fails for whatever reason
	 */
	private IResource getBuildArtifact(IBuilder builder, IConfiguration cfg) throws CoreException {
		// Return the IResource build artifact for the ManagedBuild
		IBuildMacroProvider provider = ManagedBuildManager.getBuildMacroProvider();
		String artifactName = cfg.getArtifactName();
		String ext = cfg.getArtifactExtension();
		// FIXME: This should be centralised. We really need API to fetch the final build artifact
		if(ext != null && !"".equals(ext)) {//$NON-NLS-1$
			String prefix = cfg.getOutputPrefix(ext);
			artifactName = prefix + artifactName + "." + ext; //$NON-NLS-1$
		}
		String artifact = builder.getBuildLocation().append(artifactName).toString();
		IPath path = new Path(provider.resolveValue(artifact, "", " ", IBuildMacroProvider.CONTEXT_CONFIGURATION, builder)); //$NON-NLS-1$ //$NON-NLS-2$
		IResource retVal = null;
		for (IFile res : ResourceLookup.findFilesForLocation(path)) {
			retVal = res;
			if (res.getProject().equals(getProject()))
				return res;
		}
		return retVal;
	}

	/**
	 * @return true if the specified project is a CDT project, it exists, is open and has been created
	 */
	private boolean isCdtProjectCreated(IProject project) {
		ICProjectDescription desc = CoreModel.getDefault().getProjectDescription(project, false);
		return desc != null && !desc.isCdtProjectCreating();
	}

	/**
	 * Check the status of all internal builders before they are run
	 */
	private MultiStatus checkBuilders(IBuilder builders[], IConfiguration activeCfg) {
		MultiStatus status = null;
		for (IBuilder builder : builders) {
			boolean supportsCustomization = builder.supportsCustomizedBuild();
			boolean isManagedBuildOn = builder.isManagedBuildOn();
			if (isManagedBuildOn && !supportsCustomization) {
				if (builder.isCustomBuilder()) {
					if (status == null) {
						status = new MultiStatus(
								ManagedBuilderCorePlugin.getUniqueIdentifier(),
								IStatus.ERROR,
								new String(),
								null);
					}
					status.add(new Status (
							IStatus.ERROR,
							ManagedBuilderCorePlugin.getUniqueIdentifier(),
							0,
							ManagedMakeMessages.getResourceString("CommonBuilder.1"), //$NON-NLS-1$
							null));
				} else if (builder.getParent().getParent() != activeCfg) {
					if (status == null) {
						status = new MultiStatus(
								ManagedBuilderCorePlugin.getUniqueIdentifier(),
								IStatus.ERROR,
								new String(),
								null);
					}
					status.add(new Status (
							IStatus.ERROR,
							ManagedBuilderCorePlugin.getUniqueIdentifier(),
							0,
							ManagedMakeMessages.getResourceString("CommonBuilder.2"), //$NON-NLS-1$
							null));
				}
			}
		}

		if (status == null) {
			status = new MultiStatus(
					ManagedBuilderCorePlugin.getUniqueIdentifier(),
					IStatus.OK,
					new String(),
					null);
		}

		return status;
	}

	/**
	 * Output a list of messages to the console of the specified builder
	 */
	private void emitMessages(IConsole console, List<String> msgs) throws CoreException {
		try {
			ConsoleOutputStream consoleOutStream = console.getOutputStream();
			// Report a successful clean
			for (Iterator<String> it = msgs.iterator(); it.hasNext();) {
				String msg = it.next();
				consoleOutStream.write(msg.getBytes());
				if (it.hasNext())
					consoleOutStream.write(System.getProperty("line.separator", "\n").getBytes()); //$NON-NLS-1$ //$NON-NLS-2$
			}
			consoleOutStream.flush();
			consoleOutStream.close();
		} catch (CoreException e) {
			// Throw the exception back to the builder
			throw e;
		} catch (IOException io) {	//  Ignore console failures...
			throw new CoreException(new Status(IStatus.ERROR, ManagedBuilderCorePlugin.getUniqueIdentifier(),
					io.getLocalizedMessage(), io));
		}
	}
	private void emitMessage(IConsole console, String msg) throws CoreException {
		try {
			ConsoleOutputStream consoleOutStream = console.getOutputStream();
			// Report a successful clean
			consoleOutStream.write(msg.getBytes());
			consoleOutStream.write(System.getProperty("line.separator", "\n").getBytes()); //$NON-NLS-1$ //$NON-NLS-2$
			consoleOutStream.flush();
			consoleOutStream.close();
		} catch (CoreException e) {
			// Throw the exception back to the builder
			throw e;
		} catch (IOException io) {
			throw new CoreException(new Status(IStatus.ERROR, ManagedBuilderCorePlugin.getUniqueIdentifier(),
					io.getLocalizedMessage(), io));
		}
	}


	public static void outputTrace(String resourceName, String message) {
		if (!VERBOSE)
			return;
		StringBuilder buffer = new StringBuilder();
		buffer.append(new Date(System.currentTimeMillis()));
		buffer.append(" - ["); //$NON-NLS-1$
		buffer.append(Thread.currentThread().getName());
		buffer.append("] "); //$NON-NLS-1$
		buffer.append(resourceName).append(": "); //$NON-NLS-1$
		buffer.append(message);
		System.out.println(buffer.toString());
	}

	public static void outputError(String resourceName, String message) {
		if (VERBOSE)
			System.err.println(ERROR_HEADER + resourceName + TRACE_FOOTER + message);
	}

	/**
	 * Check whether the build has been canceled.
	 */
	public void checkCancel(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled())
			throw new OperationCanceledException();
	}

	/**
	 * @return true if the given builder should be run
	 */
	protected boolean shouldBuild(int kind, IMakeBuilderInfo info) {
		switch (kind) {
			case IncrementalProjectBuilder.AUTO_BUILD :
				return info.isAutoBuildEnable();
			case IncrementalProjectBuilder.INCREMENTAL_BUILD : // now treated as the same!
			case IncrementalProjectBuilder.FULL_BUILD :
				return info.isFullBuildEnabled() | info.isIncrementalBuildEnabled() ;
			case IncrementalProjectBuilder.CLEAN_BUILD :
				return info.isCleanBuildEnabled();
		}
		return true;
	}

	@Override
	public void addMarker(IResource file, int lineNumber, String errorDesc, int severity, String errorVar) {
		super.addMarker(file, lineNumber, errorDesc, severity, errorVar);
		if (severity == IMarkerGenerator.SEVERITY_ERROR_RESOURCE)
			fBuildErrorOccured = true;
	}

	@Override
	public void addMarker(ProblemMarkerInfo problemMarkerInfo) {
		super.addMarker(problemMarkerInfo);
		if (problemMarkerInfo.severity == IMarkerGenerator.SEVERITY_ERROR_RESOURCE)
			fBuildErrorOccured = true;
	}

	/**
	 * Remove all CDT problem markers attached to the specified project configuration
	 */
	private void removeAllMarkers(IProject currProject, String configName) throws CoreException {
		IWorkspace workspace = currProject.getWorkspace();

		// remove markers on the requested configuration
		IMarker[] markers = currProject.findMarkers(ICModelMarker.C_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE);
		Collection<IMarker> toRemove = new ArrayList<IMarker>();
		for (IMarker marker : markers) {
			// If marker configuration name is null && we're clearing such markers
			//   || configuration name to clear is equal to marker config name
			//  Remove
//			String configNameAttr = marker.getAttribute(ICModelMarker.C_MODEL_MARKER_CONFIGURATION_NAME, null) ;
//			if (configNameAttr == configName
//					|| (configName != null && configName.equals(configNameAttr)))
				toRemove.add(marker);
		}
		if (!toRemove.isEmpty())
			workspace.deleteMarkers(toRemove.toArray(new IMarker[toRemove.size()]));

		// Remove other markers which match this Project / Configuration name
		if (configName != null) {
			workspace.getRoot().findMarkers(ICModelMarker.C_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE);
			toRemove.clear();
			for (IMarker marker : markers)
				toRemove.add(marker);
					workspace.deleteMarkers(toRemove.toArray(new IMarker[toRemove.size()]));
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.cdt.core.resources.ACBuilder#getRule(int, java.util.Map)
	 */
	/** Only lock the workspace if this is a ManagedBuild, or this project references others. */
	@Override
	public ISchedulingRule getRule(int trigger, Map<String, String> args) {
		IResource workspaceRule = ResourcesPlugin.getWorkspace().getRoot();
		if (!isCdtProjectCreated(getProject()))
			return workspaceRule;

		// Be pessimistic if we referenced other configurations
		try {
			if (getProject().getReferencedBuildConfigs(getBuildConfig().getName(), false).length > 0)
				return workspaceRule;
		} catch (CoreException e) {
			// Be pessimistic if we couldn't get hold of the references
			return workspaceRule;
		}
		// If any builder isManaged => pessimistic
		IBuilder builders[] = ManagedBuilderCorePlugin.createBuilders(getProject(), args);
		for (IBuilder builder : builders) {
			if (builder.isManagedBuildOn())
				return workspaceRule;
		}

		return null;
	}
}

