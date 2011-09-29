/*******************************************************************************
 * Copyright (c) 2009 Broadcom Coporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    James Blackburn (Broadcom Corp.) - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.core;

import java.util.Map;

import org.eclipse.cdt.core.testplugin.ResourceHelper;
import org.eclipse.cdt.core.testplugin.util.BaseTestCase;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.Preferences;

/**
 * Testsuite for the CommandLauncher contributions functionality
 */
public class CommandLauncherFactoryTests extends BaseTestCase {

	/** Project to use for testing */
	IProject fProject;

	@Override
	protected void setUp() throws Exception {
		fProject = ResourceHelper.createCDTProject("CommandLauncherProject").getProject();
		new InstanceScope().getNode("").removeNode();
		super.setUp();
	}

	@Override
	protected void tearDown() throws Exception {
		ResourceHelper.cleanUp();
	}

	/**
	 * Test default advance command launcher prefs
	 *
	 * At Instance(Workspace) scope, this is null / Default
	 * At the project scope, defaults are inherited from the Workspace scope
	 */
	public void testGetDefaultAdvancedCommandLauncherPreferences() {
		String key = "foo";
		String value1 = "bar1";
		String value2 = "bar2";
		Preferences prefs;

		prefs = CommandLauncherFactory.getDefaultAdvancedCommandLauncherPreferences(fProject, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG);
		assertNull(prefs.get(key, null));
		prefs = CommandLauncherFactory.getDefaultAdvancedCommandLauncherPreferences(fProject, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG);
		assertNull(prefs.get(key, null));

		CommandLauncherFactory.setAdvancedCommandLauncherPreference(null, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, key, value2);
		prefs = CommandLauncherFactory.getDefaultAdvancedCommandLauncherPreferences(null, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG);
		assertNull(prefs.get(key, null));
		prefs = CommandLauncherFactory.getDefaultAdvancedCommandLauncherPreferences(fProject, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG);
		assertTrue(prefs.get(key, null).equals(value2));

		CommandLauncherFactory.setAdvancedCommandLauncherPreference(fProject, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, key, value1);
		prefs = CommandLauncherFactory.getDefaultAdvancedCommandLauncherPreferences(null, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG);
		assertNull(prefs.get(key, null));
		prefs = CommandLauncherFactory.getDefaultAdvancedCommandLauncherPreferences(fProject, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG);
		assertTrue(prefs.get(key, null).equals(value2));
	}

	public void testGetAdvancedCommandLauncherPreferences() {
		String key = "foo";
		String value1 = "bar1";
		String value2 = "bar2";
		Preferences prefs;
		// Set some advanced preferences
		prefs = CommandLauncherFactory.getAdvancedCommandLauncherPreferences(null, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG);
		assertNull(prefs.get(key, null));
		prefs = CommandLauncherFactory.getAdvancedCommandLauncherPreferences(fProject, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG);
		assertNull(prefs.get(key, null));
		CommandLauncherFactory.setAdvancedCommandLauncherPreference(null, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, key, value2);
		prefs = CommandLauncherFactory.getAdvancedCommandLauncherPreferences(null, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG);
		assertTrue(prefs.get(key, null).equals(value2));
		prefs = CommandLauncherFactory.getAdvancedCommandLauncherPreferences(fProject, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG);
		assertNull(prefs.get(key, null));

		CommandLauncherFactory.setAdvancedCommandLauncherPreference(fProject, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, key, value1);
		prefs = CommandLauncherFactory.getAdvancedCommandLauncherPreferences(null, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG);
		assertTrue(prefs.get(key, null).equals(value2));
		prefs = CommandLauncherFactory.getAdvancedCommandLauncherPreferences(fProject, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG);
		assertTrue(prefs.get(key, null).equals(value1));
	}

	/**
	 * Test the raw get advanced command launcher prefs method
	 */
	public void testGetAdvancedCommandLauncherPreference() {
		String key = "foo";
		String value1 = "bar1";
		String value2 = "bar2";
		// Set some advanced preferences
		assertNull(CommandLauncherFactory.getAdvancedCommandLauncherPreference(null, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, key));
		assertNull(CommandLauncherFactory.getAdvancedCommandLauncherPreference(fProject, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, key));
		CommandLauncherFactory.setAdvancedCommandLauncherPreference(null, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, key, value2);
		assertTrue(CommandLauncherFactory.getAdvancedCommandLauncherPreference(null, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, key).equals(value2));
		assertTrue(CommandLauncherFactory.getAdvancedCommandLauncherPreference(fProject, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, key).equals(value2));

		CommandLauncherFactory.setAdvancedCommandLauncherPreference(fProject, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, key, value1);
		assertTrue(CommandLauncherFactory.getAdvancedCommandLauncherPreference(null, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, key).equals(value2));
		assertTrue(CommandLauncherFactory.getAdvancedCommandLauncherPreference(fProject, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, key).equals(value1));
	}

	/**
	 * Test setting some advanced preferences and ensure they're only visible to the CommandLauncher launched at the correct scope
	 */
	public void testSetAdvancedCommandLauncherPreference() {
		String key = "foo";
		String value1 = "bar1";
		String value2 = "bar2";
		// Set some advanced preferences
		CommandLauncherFactory.setAdvancedCommandLauncherPreference(fProject, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, key, value1);
		CommandLauncherFactory.setAdvancedCommandLauncherPreference(null, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, key, value2);
		// Set command launcher for the debug process type at the instance scope (no use custom launcher)
		new InstanceScope().getNode(CommandLauncherFactory.PREFS_BASE).put(CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, TestCommandLauncher.ID);
		ICommandLauncher cl = CommandLauncherFactory.createCommandLauncher(fProject, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, null);
		assertFalse(cl instanceof TestCommandLauncher);
		cl = CommandLauncherFactory.createCommandLauncher(null, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, null);
		assertFalse(cl instanceof TestCommandLauncher);
		// Set use custom command launchers for process types
		new InstanceScope().getNode(CommandLauncherFactory.PREFS_BASE).put(CommandLauncherFactory.USE_CUSTOM_PROCESS_TYPES, "true");
		cl = CommandLauncherFactory.createCommandLauncher(fProject, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, null);
		assertTrue(cl instanceof TestCommandLauncher);
		assertTrue(((TestCommandLauncher)cl).getPreference(key).equals(value2));
		cl = CommandLauncherFactory.createCommandLauncher(null, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, null);
		assertTrue(cl instanceof TestCommandLauncher);
		assertTrue(((TestCommandLauncher)cl).getPreference(key).equals(value2));

		// Set command launcher for the debug process type at project scope (but no use Custom project type)
		new ProjectScope(fProject).getNode(CommandLauncherFactory.PREFS_BASE).put(CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, TestCommandLauncher.ID);
		cl = CommandLauncherFactory.createCommandLauncher(fProject, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, null);
		assertTrue(cl instanceof TestCommandLauncher);
		assertTrue(((TestCommandLauncher)cl).getPreference(key).equals(value2));
		cl = CommandLauncherFactory.createCommandLauncher(null, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, null);
		assertTrue(cl instanceof TestCommandLauncher);
		assertTrue(((TestCommandLauncher)cl).getPreference(key).equals(value2));
		// Set use custom project type
		new ProjectScope(fProject).getNode(CommandLauncherFactory.PREFS_BASE).put(CommandLauncherFactory.USE_CUSTOM_PROCESS_TYPES, "true");
		cl = CommandLauncherFactory.createCommandLauncher(fProject, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, null);
		assertTrue(cl instanceof TestCommandLauncher);
		assertTrue(((TestCommandLauncher)cl).getPreference(key).equals(value1));
	}

	/**
	 * Tests that _default_ project level preferences are the workspace (instance) preferences
	 * and default workspace preferences are the workspace level preferences
	 */
	public void testGetDefaultPreferenceProcessTypeMappings() {
		Map<String, String> procCLMapping = CommandLauncherFactory.getDefaultPreferenceProcessTypeMappings(fProject);
		assertFalse(procCLMapping.values().contains(TestCommandLauncher.ID));
		assertFalse(procCLMapping.values().contains(TestGlobalCommandLauncher.ID));

		new InstanceScope().getNode(CommandLauncherFactory.PREFS_BASE).put(CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, TestCommandLauncher.ID);
		procCLMapping = CommandLauncherFactory.getDefaultPreferenceProcessTypeMappings(fProject);
		assertTrue(procCLMapping.get(CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG).equals(TestCommandLauncher.ID));
		assertFalse(procCLMapping.values().contains(TestGlobalCommandLauncher.ID));
		procCLMapping = CommandLauncherFactory.getDefaultPreferenceProcessTypeMappings(null);
		assertFalse(procCLMapping.values().contains(TestCommandLauncher.ID));
		assertFalse(procCLMapping.values().contains(TestGlobalCommandLauncher.ID));

		new ProjectScope(fProject).getNode(CommandLauncherFactory.PREFS_BASE).put(CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, TestCommandLauncher.ID);
		procCLMapping = CommandLauncherFactory.getDefaultPreferenceProcessTypeMappings(fProject);
		assertTrue(procCLMapping.get(CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG).equals(TestCommandLauncher.ID));
		assertFalse(procCLMapping.values().contains(TestGlobalCommandLauncher.ID));
	}

	/**
	 * Tests that we project vs instance scoping right
	 */
	public void testGetPreferenceProcessTypeMappings() {
		Map<String, String> procCLMapping = CommandLauncherFactory.getPreferenceProcessTypeMappings(fProject);
		assertFalse(procCLMapping.values().contains(TestCommandLauncher.ID));
		assertFalse(procCLMapping.values().contains(TestGlobalCommandLauncher.ID));

		CommandLauncherFactory.setPreferencesProcessTypeMapping(null, TestCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG);
		procCLMapping = CommandLauncherFactory.getPreferenceProcessTypeMappings(fProject);
		assertTrue(procCLMapping.get(CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG).equals(TestCommandLauncher.ID));
		assertFalse(procCLMapping.values().contains(TestGlobalCommandLauncher.ID));
		procCLMapping = CommandLauncherFactory.getPreferenceProcessTypeMappings(null);
		assertTrue(procCLMapping.get(CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG).equals(TestCommandLauncher.ID));
		assertFalse(procCLMapping.values().contains(TestGlobalCommandLauncher.ID));

		CommandLauncherFactory.setPreferencesProcessTypeMapping(fProject, TestGlobalCommandLauncher.ID, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG);
		procCLMapping = CommandLauncherFactory.getPreferenceProcessTypeMappings(fProject);
		assertTrue(procCLMapping.get(CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG).equals(TestGlobalCommandLauncher.ID));
		assertFalse(procCLMapping.values().contains(TestCommandLauncher.ID));
	}

	public void testSetPreferenceProcessTypeMappings() {
		String procType = CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG;
		Map<String, String> procCLMapping = CommandLauncherFactory.getPreferenceProcessTypeMappings(fProject);
		String prev = CommandLauncherFactory.setPreferencesProcessTypeMapping(fProject, TestCommandLauncher.ID, procType);
		assertTrue(prev.equals(procCLMapping.get(procType)));
		prev = CommandLauncherFactory.setPreferencesProcessTypeMapping(null, TestGlobalCommandLauncher.ID, procType);

		procCLMapping = CommandLauncherFactory.getPreferenceProcessTypeMappings(null);
		assertTrue(procCLMapping.get(procType).equals(TestGlobalCommandLauncher.ID));
		procCLMapping = CommandLauncherFactory.getPreferenceProcessTypeMappings(fProject);
		assertTrue(procCLMapping.get(procType).equals(TestCommandLauncher.ID));
	}

	/**
	 * Test that we detect when we're using project scoped vs instance scoped preference
	 */
	public void testHasProjectScopedCommandLaunchPreferences() throws Exception {
		assertFalse(CommandLauncherFactory.hasProjectScopedCommandLaunchPreferences(fProject));
		// Setting custom project setting without setting use custom means don
		new ProjectScope(fProject).getNode(CommandLauncherFactory.PREFS_BASE).put(CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, TestCommandLauncher.ID);
		assertFalse(CommandLauncherFactory.hasProjectScopedCommandLaunchPreferences(fProject));
		new ProjectScope(fProject).getNode(CommandLauncherFactory.PREFS_BASE).put(CommandLauncherFactory.USE_CUSTOM_PROCESS_TYPES, "true");
		assertTrue(CommandLauncherFactory.hasProjectScopedCommandLaunchPreferences(fProject));

		new ProjectScope(fProject).getNode("").removeNode();
		// Setting custom project setting without setting use_custom means project specific still isn't enabled
		assertFalse(CommandLauncherFactory.hasProjectScopedCommandLaunchPreferences(fProject));
		new ProjectScope(fProject).getNode(CommandLauncherFactory.PREFS_BASE).put(CommandLauncherFactory.PROCESS_TYPE_ALL, TestCommandLauncher.ID);
		assertTrue(CommandLauncherFactory.hasProjectScopedCommandLaunchPreferences(fProject));
		new ProjectScope(fProject).getNode(CommandLauncherFactory.PREFS_BASE).put(CommandLauncherFactory.USE_CUSTOM_PROCESS_TYPES, "true");
		assertFalse(CommandLauncherFactory.hasProjectScopedCommandLaunchPreferences(fProject));
	}

	/**
	 * Test we can get a command launcher of a particular type
	 */
	public void testGetCommandLauncher() {
		assertTrue("Couldn't get CommandLauncher by ID", CommandLauncherFactory.getCommandLauncher(TestCommandLauncher.ID) instanceof TestCommandLauncher);
		assertTrue("Couldn't get GlobalCommandLauncher by ID", CommandLauncherFactory.getCommandLauncher(TestGlobalCommandLauncher.ID) instanceof TestGlobalCommandLauncher);
	}

	/**
	 * Check that we see the correct command launchers based on their
	 * applicability for particular process types
	 */
	public void testGetSupportedCommandLaunchers() {
		Map<String, String> cls;
		cls = CommandLauncherFactory.getSupportedCommandLaunchers(CommandLauncherFactory.PROCESS_TYPE_ALL);
		assertTrue("Global Command Launcher not found!", cls.values().contains(TestGlobalCommandLauncher.ID));
		assertTrue("Specific command launcher found for all applicability", !cls.values().contains(TestCommandLauncher.ID));
		// null processType is the same as PROCESS_TYPE_ALL
		cls = CommandLauncherFactory.getSupportedCommandLaunchers(null);
		assertTrue("Global Command Launcher not found!", cls.values().contains(TestGlobalCommandLauncher.ID));
		assertTrue("Specific command launcher found for all applicability", !cls.values().contains(TestCommandLauncher.ID));
		// Check that both appear when we search for launch
		cls = CommandLauncherFactory.getSupportedCommandLaunchers(CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG);
		assertTrue("Global Command Launcher not found!", cls.values().contains(TestGlobalCommandLauncher.ID));
		assertTrue("Specific command launcher not found for it's specific processType", cls.values().contains(TestCommandLauncher.ID));
	}

	/**
	 * Test creating command launchers with different scope set
	 */
	public void testCreateCommandLauncher() {
		// By default we should get a command launcher for things the factory doesn't know about
		ICommandLauncher cl = CommandLauncherFactory.createCommandLauncher(fProject, "bogus", null);
		assertNotNull(cl);

		// Configure the Global cdt.all type to point at our Global Type
		new InstanceScope().getNode(CommandLauncherFactory.PREFS_BASE).put(CommandLauncherFactory.PROCESS_TYPE_ALL, TestGlobalCommandLauncher.ID);
		Object context = new Object();
		cl = CommandLauncherFactory.createCommandLauncher(fProject, CommandLauncherFactory.PROCESS_TYPE_BUILD_MANAGED, context);
		assertTrue("Command Launcher of wrong type", cl.getClass().equals(TestGlobalCommandLauncher.class));
		assertTrue("Command Launcher has wrong project", cl.getProject() == fProject);
		assertTrue("Command Launcher has wrong context", ((TestCommandLauncher)cl).getContext() == context);

		// Configure custom per-process Type command launchers & check that the global launcher hasn't appeared
		new InstanceScope().getNode(CommandLauncherFactory.PREFS_BASE).put(CommandLauncherFactory.USE_CUSTOM_PROCESS_TYPES, "true");
		cl = CommandLauncherFactory.createCommandLauncher(fProject, CommandLauncherFactory.PROCESS_TYPE_BUILD_MANAGED, context);
		assertTrue("Command Launcher of wrong type", !(cl.getClass().equals(TestGlobalCommandLauncher.class)));
		assertTrue("Command Launcher has wrong project", cl.getProject() == fProject);

		// Revert setting and check that global has reappeared
		new InstanceScope().getNode(CommandLauncherFactory.PREFS_BASE).put(CommandLauncherFactory.USE_CUSTOM_PROCESS_TYPES, "false");
		context = new Object();
		cl = CommandLauncherFactory.createCommandLauncher(fProject, CommandLauncherFactory.PROCESS_TYPE_BUILD_MANAGED, context);
		assertTrue("Command Launcher of wrong type",cl.getClass().equals(TestGlobalCommandLauncher.class));
		assertTrue("Command Launcher has wrong project", cl.getProject() == fProject);
		assertTrue("Command Launcher has wrong context", ((TestCommandLauncher)cl).getContext() == context);

		// Configure custom per-process Type command launchers without setting custom, and check that global wins
		new ProjectScope(fProject).getNode(CommandLauncherFactory.PREFS_BASE).put(CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, TestCommandLauncher.ID);
		context = new Object();
		cl = CommandLauncherFactory.createCommandLauncher(fProject, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, context);
		assertTrue("Command Launcher of wrong type", cl.getClass().equals(TestGlobalCommandLauncher.class));
		assertTrue("Command Launcher has wrong project", ((TestCommandLauncher)cl).getProject() == fProject);
		assertTrue("Command Launcher has wrong context", ((TestCommandLauncher)cl).getContext() == context);
		// Now set context and check that the correct command Launcher has appeared
		new ProjectScope(fProject).getNode(CommandLauncherFactory.PREFS_BASE).put(CommandLauncherFactory.USE_CUSTOM_PROCESS_TYPES, "true");
		context = new Object();
		cl = CommandLauncherFactory.createCommandLauncher(fProject, CommandLauncherFactory.PROCESS_TYPE_LAUNCH_DEBUG, context);
		assertTrue("Command Launcher of wrong type", cl.getClass().equals(TestCommandLauncher.class));
		assertTrue("Command Launcher has wrong project", ((TestCommandLauncher)cl).getProject() == fProject);
		assertTrue("Command Launcher has wrong context", ((TestCommandLauncher)cl).getContext() == context);
	}

}
