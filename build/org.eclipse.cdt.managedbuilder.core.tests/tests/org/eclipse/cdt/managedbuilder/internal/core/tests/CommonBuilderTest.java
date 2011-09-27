/*******************************************************************************
 * Copyright (c) 2010 Broadcom Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Broadcom Corporation - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.managedbuilder.internal.core.tests;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.resources.ACBuilder;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.settings.model.ICSettingEntry;
import org.eclipse.cdt.core.settings.model.util.CSettingEntryFactory;
import org.eclipse.cdt.managedbuilder.testplugin.AbstractBuilderTest;
import org.eclipse.cdt.managedbuilder.testplugin.ResourceDeltaVerifier;
import org.eclipse.core.resources.IBuildConfiguration;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

/**
 * Tests that builders run at appropriate moments.
 *
 * Tests needed for:
 *   - Header change in a referenced project
 *   - Exported pre-processor #define change in a referenced project
 *   - Touching a non-exported source file in a library project causes:
 *          - Library to rebuild
 *          - Dependent exe to rebuild
 *          - Depedent library doesn't need rebuild
 *   - Test HeadlessBuild ability to not stop on build error.
 */
public class CommonBuilderTest extends AbstractBuilderTest {

	public static Test suite() {
		return new TestSuite(CommonBuilderTest.class);
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		setAutoBuilding(false);
		// These tests are testing the build delta, which we trust.
//		ACBuilder.setAlwaysBuildStaticLibraries(false);
		ACBuilder.setBuildConfigResourceChanges(true);
//		ACBuilder.setStopOnBuildError(false);
	}

	/**
	 * Run a full build of a particular configuration and check the correct files
	 * are added/modified.
	 */
	public void testFullBuild() throws CoreException {
		setWorkspace("staticlibs");
		final IProject app = loadProject("App");
		IProject lib1 = loadProject("Lib1");
		IProject lib2 = loadProject("Lib2");
		IProject lib3 = loadProject("Lib3");
		IProject lib4 = loadProject("Lib4");

		List<IResource> resources = new ArrayList<IResource>();
		resources.addAll(getProjectBuildExeResources("App", "Debug", "main"));
		resources.addAll(getProjectBuildLibResources("Lib1", "Debug", "lib1"));
		resources.addAll(getProjectBuildLibResources("Lib2", "Debug", "lib2"));
		resources.addAll(getProjectBuildLibResources("Lib3", "Debug", "lib3"));
		resources.addAll(getProjectBuildLibResources("Lib4", "Debug", "lib4"));

		ResourceDeltaVerifier verifier = new ResourceDeltaVerifier();
		verifier.addExpectedChange(resources.toArray(new IResource[resources.size()]), IResourceDelta.ADDED, IResourceDelta.NO_CHANGE);
		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, lib1, lib2, lib3, lib4,
				app.getFile(".project"), lib1.getFile(".project"), lib2.getFile(".project"), lib3.getFile(".project"), lib4.getFile(".project")});
		verifyBuild(app, IncrementalProjectBuilder.FULL_BUILD, verifier);
	}

	/**
	 * Run a full build of a particular configuration, where it references a configuration in
	 * another project that is not active, and check the correct files are added/modified.
	 */
	public void testBuildProjectReferencingNonActiveConfigs() throws CoreException {
		setWorkspace("staticlibs");
		final IProject app = loadProject("App");
		IProject lib1 = loadProject("Lib1");
		IProject lib2 = loadProject("Lib2");
		IProject lib3 = loadProject("Lib3");
		IProject lib4 = loadProject("Lib4");

		setActiveConfigurationByName(lib4, "Release");

		List<IResource> resources = new ArrayList<IResource>();
		resources.addAll(getProjectBuildExeResources("App", "Debug", "main"));
		resources.addAll(getProjectBuildLibResources("Lib1", "Debug", "lib1"));
		resources.addAll(getProjectBuildLibResources("Lib2", "Debug", "lib2"));
		resources.addAll(getProjectBuildLibResources("Lib3", "Debug", "lib3"));
		resources.addAll(getProjectBuildLibResources("Lib4", "Debug", "lib4"));

		ResourceDeltaVerifier verifier = new ResourceDeltaVerifier();
		verifier.addExpectedChange(resources.toArray(new IResource[resources.size()]), IResourceDelta.ADDED, IResourceDelta.NO_CHANGE);
		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, lib1, lib2, lib3, lib4,
				app.getFile(".project"), lib1.getFile(".project"), lib2.getFile(".project"), lib3.getFile(".project"), lib4.getFile(".project")});
		verifyBuild(app, IncrementalProjectBuilder.FULL_BUILD, verifier);
	}

	/**
	 * Runs the following builds to test that deltas are used correctly:
	 *     - A full build of App Debug
	 *     - A full build of App Release
	 *     - An incremental build of App Release (to check nothing is built)
	 *     - An incremental build of App Debug (to check nothing is built)
	 *     - Modifies a file in Lib4 (on which Lib2 and App depend)
	 *     - An incremental build of App Release (to check that dependent projects are built)
	 *     - An incremental build of App Debug (to check that dependent projects are built)
	 *     - An incremental build of App Release (to check nothing is built)
	 *     - An incremental build of App Debug (to check nothing is built)
	 */
	public void testDeltas() throws CoreException {
		setWorkspace("staticlibs");
		final IProject app = loadProject("App");
		IProject lib1 = loadProject("Lib1");
		IProject lib2 = loadProject("Lib2");
		IProject lib3 = loadProject("Lib3");
		IProject lib4 = loadProject("Lib4");

		// Full build of Debug
		setActiveConfigurationByName(app, "Debug");

		List<IResource> resources = new ArrayList<IResource>();
		resources.addAll(getProjectBuildExeResources("App", "Debug", "main"));
		resources.addAll(getProjectBuildLibResources("Lib1", "Debug", "lib1"));
		resources.addAll(getProjectBuildLibResources("Lib2", "Debug", "lib2"));
		resources.addAll(getProjectBuildLibResources("Lib3", "Debug", "lib3"));
		resources.addAll(getProjectBuildLibResources("Lib4", "Debug", "lib4"));

		ResourceDeltaVerifier verifier = new ResourceDeltaVerifier();
		verifier.addExpectedChange(resources.toArray(new IResource[resources.size()]), IResourceDelta.ADDED, IResourceDelta.NO_CHANGE);
		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, lib1, lib2, lib3, lib4,
				app.getFile(".project"), lib1.getFile(".project"), lib2.getFile(".project"), lib3.getFile(".project"), lib4.getFile(".project")});
		verifyBuild(app, IncrementalProjectBuilder.FULL_BUILD, verifier);

		// Full build of Release
		setActiveConfigurationByName(app, "Release");

		resources.clear();
		resources.addAll(getProjectBuildExeResources("App", "Release", "main"));
		resources.addAll(getProjectBuildLibResources("Lib1", "Release", "lib1"));
		resources.addAll(getProjectBuildLibResources("Lib2", "Release", "lib2"));
		resources.addAll(getProjectBuildLibResources("Lib3", "Release", "lib3"));
		resources.addAll(getProjectBuildLibResources("Lib4", "Release", "lib4"));

		verifier.reset();
		verifier.addExpectedChange(resources.toArray(new IResource[resources.size()]), IResourceDelta.ADDED, IResourceDelta.NO_CHANGE);
		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, lib1, lib2, lib3, lib4,
				app.getFile(".project"), lib1.getFile(".project"), lib2.getFile(".project"), lib3.getFile(".project"), lib4.getFile(".project")});
		verifyBuild(app, IncrementalProjectBuilder.FULL_BUILD, verifier);

		// Incremental build of Release (causing nothing to be built)
		setActiveConfigurationByName(app, "Release");
		verifier.reset();
		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, lib1, lib2, lib3, lib4,
				app.getFile(".project"), lib1.getFile(".project"), lib2.getFile(".project"), lib3.getFile(".project"), lib4.getFile(".project")});
		verifyBuild(app, IncrementalProjectBuilder.INCREMENTAL_BUILD, verifier);

		// Incremental build of Debug (causing nothing to be built)
		setActiveConfigurationByName(app, "Debug");
		verifier.reset();
		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, lib1, lib2, lib3, lib4,
				app.getFile(".project"), lib1.getFile(".project"), lib2.getFile(".project"), lib3.getFile(".project"), lib4.getFile(".project")});
// FIXME there's no reason to regenerate the makefiles here as there isn't a source level dependency
		verifier.addIgnore(new IResource[] {
				app.getFolder("Debug"),
				app.getFolder("Debug").getFile("makefile"),
				app.getFolder("Debug").getFile("sources.mk"),
				app.getFolder("Debug").getFile("subdir.mk"),				
		});
// END-FIXME
		verifyBuild(app, IncrementalProjectBuilder.INCREMENTAL_BUILD, verifier);

		// Modify Lib4 (by appending whitespace)
		IFile file = lib4.getFile("lib4.c");
		file.appendContents(new ByteArrayInputStream("\n".getBytes()), true, false, null);

		// Incremental build of Release (causing Lib4 and App to be built)
		setActiveConfigurationByName(app, "Release");
		verifier.reset();
		verifier.addExpectedChange(new IResource[]{
				app.getFolder("Release").getFile("App"),
				app.getFolder("Release").getFile("makefile"),
				app.getFolder("Release").getFile("sources.mk"),
				app.getFolder("Release").getFile("subdir.mk"),
// FIXME there's no reason to regenerate the makefiles here as there isn't a source level dependency
				lib2.getFolder("Release").getFile("makefile"),
				lib2.getFolder("Release").getFile("sources.mk"),
				lib2.getFolder("Release").getFile("subdir.mk"),
// FIXME end
				lib4.getFolder("Release").getFile("makefile"),
				lib4.getFolder("Release").getFile("sources.mk"),
				lib4.getFolder("Release").getFile("subdir.mk")
			}, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
		verifier.addExpectedChange(new IResource[]{
				lib4.getFolder("Release").getFile("lib4.d"),
				lib4.getFolder("Release").getFile("lib4.o"),
				lib4.getFolder("Release").getFile("libLib4.a")
			}, IResourceDelta.CHANGED, IResourceDelta.CONTENT | IResourceDelta.REPLACED);
		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, lib1, lib2, lib3, lib4,
				app.getFile(".project"), lib1.getFile(".project"), lib2.getFile(".project"), lib3.getFile(".project"), lib4.getFile(".project")});
		verifyBuild(app, IncrementalProjectBuilder.INCREMENTAL_BUILD, verifier);

		// Incremental build of Debug (causing Lib4, Lib2 and App to be built)
		setActiveConfigurationByName(app, "Debug");
		verifier.reset();
		verifier.addExpectedChange(new IResource[]{
				app.getFolder("Debug").getFile("App"),
				app.getFolder("Debug").getFile("makefile"),
				app.getFolder("Debug").getFile("sources.mk"),
				app.getFolder("Debug").getFile("subdir.mk"),
// FIXME no need to regenerate these makefiles as there isn't a change here...
				lib2.getFolder("Debug").getFile("makefile"),
				lib2.getFolder("Debug").getFile("sources.mk"),
				lib2.getFolder("Debug").getFile("subdir.mk"),
// FIXME end
				lib4.getFolder("Debug").getFile("makefile"),
				lib4.getFolder("Debug").getFile("sources.mk"),
				lib4.getFolder("Debug").getFile("subdir.mk")
			}, IResourceDelta.CHANGED, IResourceDelta.CONTENT);
		verifier.addExpectedChange(new IResource[]{
				lib4.getFolder("Debug").getFile("lib4.d"),
				lib4.getFolder("Debug").getFile("lib4.o"),
				lib4.getFolder("Debug").getFile("libLib4.a")
			}, IResourceDelta.CHANGED, IResourceDelta.CONTENT | IResourceDelta.REPLACED);
		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, lib1, lib2, lib3, lib4,
				app.getFile(".project"), lib1.getFile(".project"), lib2.getFile(".project"), lib3.getFile(".project"), lib4.getFile(".project")});
		verifyBuild(app, IncrementalProjectBuilder.INCREMENTAL_BUILD, verifier);

		// Incremental build of Release (causing nothing to be built)
		setActiveConfigurationByName(app, "Release");
		verifier.reset();
		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, lib1, lib2, lib3, lib4,
				app.getFile(".project"), lib1.getFile(".project"), lib2.getFile(".project"), lib3.getFile(".project"), lib4.getFile(".project")});
		verifyBuild(app, IncrementalProjectBuilder.INCREMENTAL_BUILD, verifier);

		// Incremental build of Debug (causing nothing to be built)
		setActiveConfigurationByName(app, "Debug");
		verifier.reset();
		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, lib1, lib2, lib3, lib4,
				app.getFile(".project"), lib1.getFile(".project"), lib2.getFile(".project"), lib3.getFile(".project"), lib4.getFile(".project")});
		verifyBuild(app, IncrementalProjectBuilder.INCREMENTAL_BUILD, verifier);
	}

	/**
	 * Test that changing an export on a referenced project causes referenced projects to re-build
	 *  - Build app{Debug, Release} => causes all configuration to be built
 	 *  - Modifies an export Lib4 (on which Lib2 and App depend) => both app + Lib2 should be rebuilt
 	 *
	 */
	public void testChangeExport() throws CoreException {
		setWorkspace("staticlibs");
		final IProject app = loadProject("App");
		IProject lib1 = loadProject("Lib1");
		IProject lib2 = loadProject("Lib2");
		IProject lib3 = loadProject("Lib3");
		IProject lib4 = loadProject("Lib4");

		setActiveConfigurationByName(app, "Debug");

		// Full build of Debug / Release and references
		List<IResource> resources = new ArrayList<IResource>();
		resources.addAll(getProjectBuildExeResources("App", "Debug", "main"));
		resources.addAll(getProjectBuildLibResources("Lib1", "Debug", "lib1"));
		resources.addAll(getProjectBuildLibResources("Lib2", "Debug", "lib2"));
		resources.addAll(getProjectBuildLibResources("Lib3", "Debug", "lib3"));
		resources.addAll(getProjectBuildLibResources("Lib4", "Debug", "lib4"));
		resources.addAll(getProjectBuildExeResources("App", "Release", "main"));
		resources.addAll(getProjectBuildLibResources("Lib1", "Release", "lib1"));
		resources.addAll(getProjectBuildLibResources("Lib2", "Release", "lib2"));
		resources.addAll(getProjectBuildLibResources("Lib3", "Release", "lib3"));
		resources.addAll(getProjectBuildLibResources("Lib4", "Release", "lib4"));

		ResourceDeltaVerifier verifier = new ResourceDeltaVerifier();
		verifier.addExpectedChange(resources.toArray(new IResource[resources.size()]), IResourceDelta.ADDED, IResourceDelta.NO_CHANGE);
		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, lib1, lib2, lib3, lib4,
				app.getFile(".project"), lib1.getFile(".project"), lib2.getFile(".project"), lib3.getFile(".project"), lib4.getFile(".project")});
		verifyBuild(app.getBuildConfigs(), IncrementalProjectBuilder.FULL_BUILD, verifier);

		// Change an export on Lib2;Release.
		ICProjectDescription desc = CCorePlugin.getDefault().getProjectDescription(lib4);
		ICConfigurationDescription cfg = desc.getConfigurationByName("Release");
		cfg.getExternalSettings();
		ICSettingEntry e = new CSettingEntryFactory().getEntry(ICSettingEntry.MACRO, "foo", "bar", null, 0, true);
		cfg.createExternalSetting(null, null, null, new ICSettingEntry[] {e});
		CCorePlugin.getDefault().setProjectDescription(lib4, desc);

		// Rebuilding debug should do nothing.
		verifier.reset();
		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, lib1, lib2, lib3, lib4,
				app.getFile(".project"), lib1.getFile(".project"), lib2.getFile(".project"), lib3.getFile(".project"), lib4.getFile(".project")});
// FIXME: technically we shouldn't be re-generating the makefiles here, but our .cproject may have changed
		resources.clear();
		resources.addAll(getProjectBuildExeResources("App", "Debug", new String[0]));
		resources.addAll(getProjectBuildExeResources("Lib1", "Debug", new String[0]));
		resources.addAll(getProjectBuildExeResources("Lib2", "Debug", new String[0]));
		resources.addAll(getProjectBuildExeResources("Lib3", "Debug", new String[0]));
		resources.addAll(getProjectBuildExeResources("Lib4", "Debug", new String[0]));
		verifier.addIgnore(resources.toArray(new IResource[resources.size()]));
// end FIXME
		verifyBuild(app, IncrementalProjectBuilder.FULL_BUILD, verifier);

		// Rebuilding release should change resources in Lib2 and App
		resources.clear();
		resources.addAll(getProjectBuildExeResources("App", "Release", "main"));
		resources.addAll(getProjectBuildLibResources("Lib2", "Release", "lib2"));
		verifier.reset();
		verifier.addExpectedChange(resources.toArray(new IResource[resources.size()]), IResourceDelta.ADDED, IResourceDelta.NO_CHANGE);
		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, lib1, lib2, lib3, lib4,
				app.getFile(".project"), lib1.getFile(".project"), lib2.getFile(".project"), lib3.getFile(".project"), lib4.getFile(".project")});
		verifyBuild(app.getBuildConfigs(), IncrementalProjectBuilder.FULL_BUILD, verifier);
	}

	/**
	 * Run a full build of two configurations, then a clean and check that everything
	 * is removed.
	 */
	public void testClean() throws CoreException {
		setWorkspace("staticlibs");
		final IProject app = loadProject("App");
		IProject lib1 = loadProject("Lib1");
		IProject lib2 = loadProject("Lib2");
		IProject lib3 = loadProject("Lib3");
		IProject lib4 = loadProject("Lib4");

		List<IResource> resources = new ArrayList<IResource>();
		resources.addAll(getProjectBuildExeResources("App", "Debug", "main"));
		resources.addAll(getProjectBuildLibResources("Lib1", "Debug", "lib1"));
		resources.addAll(getProjectBuildLibResources("Lib2", "Debug", "lib2"));
		resources.addAll(getProjectBuildLibResources("Lib3", "Debug", "lib3"));
		resources.addAll(getProjectBuildLibResources("Lib4", "Debug", "lib4"));

		ResourceDeltaVerifier verifier = new ResourceDeltaVerifier();
		verifier.addExpectedChange(resources.toArray(new IResource[resources.size()]), IResourceDelta.ADDED, IResourceDelta.NO_CHANGE);
		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, lib1, lib2, lib3, lib4,
				app.getFile(".project"), lib1.getFile(".project"), lib2.getFile(".project"), lib3.getFile(".project"), lib4.getFile(".project")});
		verifyBuild(app, IncrementalProjectBuilder.FULL_BUILD, verifier);

		verifier.reset();
		verifier.addExpectedChange(resources.toArray(new IResource[resources.size()]), IResourceDelta.REMOVED, IResourceDelta.NO_CHANGE);
		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, lib1, lib2, lib3, lib4,
				app.getFile(".project"), lib1.getFile(".project"), lib2.getFile(".project"), lib3.getFile(".project"), lib4.getFile(".project")});
		verifyBuild(new IBuildConfiguration[] {
				app.getActiveBuildConfig(),
				lib1.getActiveBuildConfig(),
				lib2.getActiveBuildConfig(),
				lib3.getActiveBuildConfig(),
				lib4.getActiveBuildConfig(),
				}, IncrementalProjectBuilder.CLEAN_BUILD, verifier);
	}

	/**
	 * Tests building a project that has an application, a shared library and a static library.
	 * Specifically, this is to test that building a shared library does build its dependent libraries,
	 * as they are required by the linker.
	 * The dependencies are as follows:
	 *  - StaticLib depends on nothing
	 *  - SharedLib depends on StaticLib
	 *  - App depends on SharedLib
	 */
	public void testSharedLibrary() throws CoreException {
		setWorkspace("sharedlib");
		final IProject app = loadProject("App");
		IProject sharedLib = loadProject("SharedLib");
		IProject staticLib = loadProject("StaticLib");

		// Run build of App, which should build SharedLib and StaticLib
		List<IResource> resources = new ArrayList<IResource>();
		resources.addAll(getProjectBuildExeResources("App", "Debug", "main"));
		resources.addAll(getProjectBuildSharedLibResources("SharedLib", "Debug", "sharedlib"));
		resources.addAll(getProjectBuildLibResources("StaticLib", "Debug", "staticlib"));

		ResourceDeltaVerifier verifier = new ResourceDeltaVerifier();
		verifier.addExpectedChange(resources.toArray(new IResource[resources.size()]), IResourceDelta.ADDED, IResourceDelta.NO_CHANGE);
		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, sharedLib, staticLib,
				app.getFile(".project"), sharedLib.getFile(".project"), staticLib.getFile(".project")});
		verifyBuild(app, IncrementalProjectBuilder.FULL_BUILD, verifier);

		getWorkspace().build(new IBuildConfiguration[]{app.getActiveBuildConfig()}, IncrementalProjectBuilder.CLEAN_BUILD, true, null);

		// Run build of SharedLib, which should build SharedLib and StaticLib
		resources.clear();
		resources.addAll(getProjectBuildSharedLibResources("SharedLib", "Debug", "sharedlib"));
		resources.addAll(getProjectBuildLibResources("StaticLib", "Debug", "staticlib"));
		verifier.reset();
		verifier.addExpectedChange(resources.toArray(new IResource[resources.size()]), IResourceDelta.ADDED, IResourceDelta.NO_CHANGE);
		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, sharedLib, staticLib,
				app.getFile(".project"), sharedLib.getFile(".project"), staticLib.getFile(".project")});
		verifyBuild(sharedLib, IncrementalProjectBuilder.FULL_BUILD, verifier);
	}

	/**
	 * Tests stop on build error. This builds an application App dependent on two libraries Lib1 and Lib2.
	 * Lib1 has an error, so building App will build Lib2, then fail on Lib1.
	 */
	public void testStopOnError() throws CoreException {
		setWorkspace("error");
		final IProject app = loadProject("App");
		IProject lib1 = loadProject("Lib1");
		IProject lib2 = loadProject("Lib2");

		ResourceDeltaVerifier verifier = new ResourceDeltaVerifier();
		
		// Enable stop on build error
//		ACBuilder.setStopOnBuildError(true);

		// Lib2 should compile:
		List<IResource> resources = new ArrayList<IResource>();
		resources.addAll(getProjectBuildLibResources("Lib2", "Debug", "lib2"));
		verifier.addExpectedChange(lib2.getFile(".project"), IResourceDelta.CHANGED, IResourceDelta.CONTENT);
		verifier.addExpectedChange(resources.toArray(new IResource[resources.size()]), IResourceDelta.ADDED, IResourceDelta.NO_CHANGE);

		// Lib1 should partially compile:
		verifier.addExpectedChange(new IResource[]{lib1, lib1.getFile("lib1.c")}, IResourceDelta.CHANGED, IResourceDelta.MARKERS);
		verifier.addExpectedChange(new IResource[]{
				lib1.getFolder("Debug"),
				lib1.getFolder("Debug").getFile("lib1.d"),
				lib1.getFolder("Debug").getFile("makefile"),
				lib1.getFolder("Debug").getFile("objects.mk"),
				lib1.getFolder("Debug").getFile("sources.mk"),
				lib1.getFolder("Debug").getFile("subdir.mk")
			}, IResourceDelta.ADDED, IResourceDelta.NO_CHANGE);

		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, lib1, lib2,
				app.getFile(".project"), lib1.getFile(".project"), lib2.getFile(".project")});

		getWorkspace().addResourceChangeListener(verifier);
		boolean cancelled = false;
		try {
			IWorkspaceRunnable body = new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) throws CoreException {
					getWorkspace().build(new IBuildConfiguration[]{app.getActiveBuildConfig()}, IncrementalProjectBuilder.FULL_BUILD, true, null);
				}
			};
			IProgressMonitor monitor = new NullProgressMonitor();
			getWorkspace().run(body, monitor);
			if (monitor.isCanceled())
				throw new OperationCanceledException();
			fail();
		} catch (OperationCanceledException e) {
			cancelled = true;
			assertTrue(verifier.getMessage(), verifier.isDeltaValid());
		} finally {
			getWorkspace().removeResourceChangeListener(verifier);
			printAllMarkers();
		}
		// Stop on build error causes cancellation
		assertTrue(cancelled);
	}

	/**
	 * Tests building Lib1 that references Lib2, then App that references Lib2.
	 * This checks that rememberLastBuildState is being used correctly, so that
	 * the deltas for Lib2 are not updated, which would prevent it from being
	 * built when building App.
	 */
	public void testRememberLastBuildState() throws CoreException {
		setWorkspace("linker");
		final IProject app = loadProject("App");
		IProject lib1 = loadProject("Lib1");
		IProject lib2 = loadProject("Lib2");

		// Run build of Lib1, which should build only Lib1 and not update the delta for Lib2
		List<IResource> resources = new ArrayList<IResource>();
		resources.addAll(getProjectBuildLibResources("Lib1", "Debug", "lib1"));
		ResourceDeltaVerifier verifier = new ResourceDeltaVerifier();
		verifier.addExpectedChange(resources.toArray(new IResource[resources.size()]), IResourceDelta.ADDED, IResourceDelta.NO_CHANGE);
		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, lib1, lib2,
				app.getFile(".project"), lib1.getFile(".project"), lib2.getFile(".project")});
		verifyBuild(lib1, IncrementalProjectBuilder.INCREMENTAL_BUILD, verifier);

		// Run build of App, which should build Lib2 and App
		resources = new ArrayList<IResource>();
		resources.addAll(getProjectBuildExeResources("App", "Debug", "main"));
		resources.addAll(getProjectBuildLibResources("Lib2", "Debug", "lib2"));
		verifier.reset();
		verifier.addExpectedChange(resources.toArray(new IResource[resources.size()]), IResourceDelta.ADDED, IResourceDelta.NO_CHANGE);
		verifier.addIgnore(new IResource[]{
				getWorkspace().getRoot(), app, lib1, lib2,
				app.getFile(".project"), lib1.getFile(".project"), lib2.getFile(".project")});
		verifyBuild(app, IncrementalProjectBuilder.INCREMENTAL_BUILD, verifier);
	}
}
