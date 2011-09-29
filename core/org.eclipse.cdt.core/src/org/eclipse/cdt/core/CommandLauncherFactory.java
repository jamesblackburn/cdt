/*******************************************************************************
 * Copyright (c) 2009 Broadcom Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     James Blackburn (Broadcom Corp.) - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.core;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.cdt.internal.core.settings.model.ExceptionFactory;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

/**
 * Class which provides a CommandLauncher factory. It provides a central point for:
 * <ul>
 * <li> Loading / discovering contributed CommandLaunchers </li>
 * <li> Storing / Accessing ProcessType to CommandLauncher mappings </li>
 * <li> Storing / Accessing CommandLauncher specific preferences </li>
 * </ul>
 *
 * Concretely contributors can override how CDT launched external Processes (such
 * as builds, debug sessions and Launch run/debug sessions) are executed.
 * @noextend This class is not intended to be subclassed by clients.
 * @noinstantiate This class is not intended to be instantiated by clients.
 * @since 5.3
 */
public class CommandLauncherFactory {

	/** Preference node separator */
	private static final char PREF_NODE_SEP = IPath.SEPARATOR;

	/** CommandLauncher extension point & root preference key*/
	public static final String COMMAND_LAUNCHER_EXT_POINT = "CommandLauncher"; //$NON-NLS-1$
	/** The Base of the CommandLaunchers preferences store */
	public static final String PREFS_BASE = CCorePlugin.PLUGIN_ID + PREF_NODE_SEP + COMMAND_LAUNCHER_EXT_POINT;
	/** Preferences Node path for the Advanced CommandLauncher specific preferences*/
	public static final String COMMAND_LAUNCHER_ADVANCED_NODE = PREFS_BASE + PREF_NODE_SEP + "Advanced"; //$NON-NLS-1$

	/*
	 * Command Launcher preferences are stored in the following hierarchy
	 *                              /CommandLauncher/<ProcessType>
	 * /<scope>/org.eclipse.cdt.core/CommandLauncher/cdt.all 			= <CommandLauncherID>
	 * /<scope>/org.eclipse.cdt.core/CommandLauncher/customProcessTypes = <true|false>
	 * /<scope>/org.eclipse.cdt.core/CommandLauncher/cdt.launch.run 	= <CommandLauncherID>
	 * /<scope>/org.eclipse.cdt.core/CommandLauncher/cdt.launch.debug 	= <CommandLauncherID>
	 * /<scope>/org.eclipse.cdt.core/CommandLauncher/cdt.launch.profile = <CommandLauncherID>
	 * /<scope>/org.eclipse.cdt.core/CommandLauncher/cdt.build.standard = <CommandLauncherID>
	 * /<scope>/org.eclipse.cdt.core/CommandLauncher/cdt.build.managed 	= <CommandLauncherID>
	 *
	 * CommandLauncher specific 'Advanced' Preferences are stored in a different namespace:
	 *                              /CommandLauncher/Advanced/<CommadnLauncherID>/<processType>/{customPrefs}
	 * /<scope>/org.eclipse.cdt.core/CommandLauncher/Advanced/<CommandLauncherID>/<processType>/prefKey1 = <prefValue1>
	 * /<scope>/org.eclipse.cdt.core/CommandLauncher/Advanced/<CommandLauncherID>/<processType>/prefKey2 = <prefValue2>
	 * /<scope>/org.eclipse.cdt.core/CommandLauncher/Advanced/<CommandLauncherID>/<processType>/prefKey3 = <prefValue3>
	 */

	/*
	 * processTypes which may be mapped to a command launcher
	 */
	/** Boolean Preference indicating CommandLauncher specified per ProcessType rather than
	 *  single global value */
	public static final String USE_CUSTOM_PROCESS_TYPES = "customProcessTypes"; //$NON-NLS-1$
	/** Process type encompassing all other processTypes*/
	public static final String PROCESS_TYPE_ALL = "cdt.all"; //$NON-NLS-1$

	/** Default type for the CommandLauncher */
	public static final String DEFAULT_COMMAND_LAUNCHER = CommandLauncher.ID;

	/** Process Type for a Run launch */
	public static final String PROCESS_TYPE_LAUNCH_RUN = "cdt.launch.run"; //$NON-NLS-1$
	/** Process Type for a Debug launch */
	public static final String PROCESS_TYPE_LAUNCH_DEBUG = "cdt.launch.debug"; //$NON-NLS-1$
	/** Process Type for a Profile launch */
	public static final String PROCESS_TYPE_LAUNCH_PROFILE = "cdt.launch.profile"; //$NON-NLS-1$
	/** Process Type for a Standard Makefile build */
	public static final String PROCESS_TYPE_BUILD_STANDARD = "cdt.build.standard"; //$NON-NLS-1$
	/** Process Type for a Managed build */
	public static final String PROCESS_TYPE_BUILD_MANAGED = "cdt.build.managed"; //$NON-NLS-1$

	/** Custom vs. Global Command Launcher toggle and preference */
	private static final HashMap<String, String> globalCommandLaunchPrefs = new LinkedHashMap<String, String>() {{
		put(USE_CUSTOM_PROCESS_TYPES, Messages.CommandLauncherFactory_CustomCommandLauncher);
		put(PROCESS_TYPE_ALL, Messages.CommandLauncherFactory_SharedCommandLauncher);
	}};
	/** Process Types (Map ID -> Human Readable Name) : Each of these generates identical Preference UI */
	private static final HashMap<String, String> perProcessTypeCommandLaunchPrefs = new LinkedHashMap<String, String>() {{
		put(PROCESS_TYPE_LAUNCH_RUN, Messages.CommandLauncherFactory_RunLaunch);
		put(PROCESS_TYPE_LAUNCH_DEBUG, Messages.CommandLauncherFactory_DebugLaunch);
		put(PROCESS_TYPE_LAUNCH_PROFILE, Messages.CommandLauncherFactory_ProfileLaunch);
		put(PROCESS_TYPE_BUILD_STANDARD, Messages.CommandLauncherFactory_MakefileBuild);
		put(PROCESS_TYPE_BUILD_MANAGED, Messages.CommandLauncherFactory_ManagedBuild);
	}};

	/** No instantiation */
	private CommandLauncherFactory() {}

	/**
	 * @return the map of global CommandLauncher & whether this is enabled
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, String> getGlobalCommandLaunchIdNames() {
		return (Map<String, String>)globalCommandLaunchPrefs.clone();
	}

	/**
	 * @return the map of Process Launch ID -> Human Readable name types known about by cdt.core
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, String> getPerProcessTypeCommandIdNames() {
		return (Map<String, String>)perProcessTypeCommandLaunchPrefs.clone();
	}

	//
	//  CommandLauncher Preferences
	//      'Advanced' Preferences deal with Custom command launcher preferences which are contributed
	//

	/**
	 * Returns the Default 'Advanced' preferences for the Commandlauncher with given id as defined by the
	 * Product. This can be used for resetting Advanced Preferences to default values.
	 * If project is defined, then InstanceScope preferences override default scoped preferences
	 * @param project project or null
	 * @param commandLauncherID ID of the command launcher, not null
	 * @param processType processType ID, not null
	 * @return Preferences
	 */
	public static Preferences getDefaultAdvancedCommandLauncherPreferences(IProject project, String commandLauncherID, String processType) {
		IScopeContext sc;
		if (project != null)
			sc = new InstanceScope();
		else
			sc = new DefaultScope();
		return sc.getNode(COMMAND_LAUNCHER_ADVANCED_NODE).node(commandLauncherID + PREF_NODE_SEP + processType);
	}

	/**
	 * Returns the 'Advanced' preferences for the Commandlauncher with given id at the appropriate scope
	 * (workspace / project) for a specified processType. This can be used for persisting preferences to the
	 * store.
	 * NB process type cdt.all is a processType which allows a single CommandLauncher for all process types
	 * @param project IProject or null for workspace scope
	 * @param commandLauncherID ID of the command launcher, not null
	 * @param processType processType ID, not null
	 * @return Preferences
	 */
	public static Preferences getAdvancedCommandLauncherPreferences(IProject project, String commandLauncherID, String processType) {
		IScopeContext sc;
		if (project != null)
			sc = new ProjectScope(project);
		else
			sc = new InstanceScope();
		return sc.getNode(COMMAND_LAUNCHER_ADVANCED_NODE).node(commandLauncherID + PREF_NODE_SEP + processType);
	}

	/**
	 * Returns an Advanced preference value for the specified key in the specified commandLauncherID/processType
	 * scope
	 * @param project IProject or null for workspace scope
	 * @param commandLauncherID ID of the command launcher, not null
	 * @param processType processType ID, not null
	 * @param key Key to lookup
	 * @return String representing the command launcher pref, or null if not found
	 */
	public static String getAdvancedCommandLauncherPreference(IProject project, String commandLauncherID, String processType, String key) {
		IPreferencesService service = Platform.getPreferencesService();
		IScopeContext[] contexts = null;
		if (project != null) {
			service.setDefaultLookupOrder(PREFS_BASE, null, new String[] {
					ProjectScope.SCOPE,
					InstanceScope.SCOPE,
					ConfigurationScope.SCOPE,
					DefaultScope.SCOPE
			});
			contexts = new IScopeContext[] {new ProjectScope(project)};
		}
		return service.getString(COMMAND_LAUNCHER_ADVANCED_NODE + PREF_NODE_SEP + commandLauncherID + PREF_NODE_SEP + processType, key, null, contexts);
	}

	/**
	 * Set a preference value for a given CommandLauncher and ProcessType context
	 * @param project project or null for InstanceScope
	 * @param commandLauncherID
	 * @param processType
	 * @param key key, not null
	 * @param value String value or null to remove
	 */
	public static void setAdvancedCommandLauncherPreference(IProject project, String commandLauncherID, String processType, String key, String value) {
		IScopeContext sc;
		if (project == null)
			sc = new InstanceScope();
		else
			sc = new ProjectScope(project);
		if (value != null)
			sc.getNode(COMMAND_LAUNCHER_ADVANCED_NODE).node(commandLauncherID + PREF_NODE_SEP + processType).put(key, value);
		else
			sc.getNode(COMMAND_LAUNCHER_ADVANCED_NODE).node(commandLauncherID + PREF_NODE_SEP + processType).remove(key);
	}

	/**
	 * Fetch the CommandLauncher mappings from the preference store, using scoped lookup
	 * @param sc
	 * @return Map of Process Type Key -> Command Launcher Id
	 */
	private static Map<String, String> getProcessCommandMappingsForContext(IProject project) {
		IPreferencesService service = Platform.getPreferencesService();
		IScopeContext[] context = null;
		if (project != null) {
			service.setDefaultLookupOrder(PREFS_BASE, null, new String[] {
					ProjectScope.SCOPE,
					InstanceScope.SCOPE,
					ConfigurationScope.SCOPE,
					DefaultScope.SCOPE
			});
			context = new IScopeContext[] {new ProjectScope(project)};
		}

		HashMap<String, String> launchMappings = new HashMap<String, String>();
		// Find all the process types, default to "Default"
		for (String procId : perProcessTypeCommandLaunchPrefs.keySet())
			launchMappings.put(procId, service.getString(PREFS_BASE, procId, CommandLauncher.ID, context));
		// Add the ALL processType
		launchMappings.put(PROCESS_TYPE_ALL, service.getString(PREFS_BASE, PROCESS_TYPE_ALL, CommandLauncher.ID, context));
		// All or custom Command Launcher for the processTypes?
		launchMappings.put(USE_CUSTOM_PROCESS_TYPES, service.getString(PREFS_BASE, USE_CUSTOM_PROCESS_TYPES, "false", context)); //$NON-NLS-1$
		return launchMappings;
	}

	/**
	 * Return the default process Type mappings for the specified context.
	 * If project is specified, then instance scoped workspace preferences are deemed to be default,
	 * otherwise default scope is the default
	 * @return default preferences at the specified scope level
	 */
	public static Map<String, String> getDefaultPreferenceProcessTypeMappings(IProject project) {
		IPreferencesService service = Platform.getPreferencesService();
		IScopeContext[] context = null;
		if (project != null)
			context = new IScopeContext[] {new ProjectScope(project)};

		if (project == null)
			service.setDefaultLookupOrder(PREFS_BASE, null, new String[] {DefaultScope.SCOPE});
		else
			service.setDefaultLookupOrder(PREFS_BASE, null, new String[] {InstanceScope.SCOPE, DefaultScope.SCOPE});

		HashMap<String, String> launchMappings = new HashMap<String, String>();
		// Find all the process types, default to "Default"
		for (String procId : perProcessTypeCommandLaunchPrefs.keySet())
			launchMappings.put(procId, service.getString(PREFS_BASE, procId, CommandLauncher.ID, context));
		// Add the ALL processType
		launchMappings.put(PROCESS_TYPE_ALL, service.getString(PREFS_BASE, PROCESS_TYPE_ALL, CommandLauncher.ID, context));
		// All or custom Command Launcher for the processTypes?
		launchMappings.put(USE_CUSTOM_PROCESS_TYPES, service.getString(PREFS_BASE, USE_CUSTOM_PROCESS_TYPES, "false", context)); //$NON-NLS-1$
		return launchMappings;
	}

	/**
	 * Return Map of processType -> CommandLauncherId.
	 * Uses Scoped lookup from the preference service with Project scoped preferences having
	 * precedence over Instance scoped. If the preference isn't specified, it defaults to the Built-in
	 * CommandLauncher's default
	 * @param project if non-null return use project scoped mapping, otherwise instance (workspace) scoped
	 * @return Map of processType -> CommandLauncher Id
	 */
	public static Map<String, String> getPreferenceProcessTypeMappings(IProject project) {
		return getProcessCommandMappingsForContext(project);
	}

	/**
	 * Sets the command launcher id for the given processType at the specified scope.
     *
	 * This also allows setting the special {@link #PROCESS_TYPE_ALL} process type and associated
	 * {@link #USE_CUSTOM_PROCESS_TYPES}
	 * NB a custom command launcher won't be used if USE_CUSTOM_PROCESS_TYPES == false
	 *
	 * @param project project scope, or null for Instance scope
	 * @param commandLauncherID commandLauncher ID
	 * @param processType ID of the process type
	 * @return previous command launcher id
	 */
	public static String setPreferencesProcessTypeMapping(IProject project, String commandLauncherID, String processType) {
		String previous = getProcessCommandMappingsForContext(project).get(processType);
		IScopeContext sc;
		if (project == null)
			sc = new InstanceScope();
		else
			sc = new ProjectScope(project);
		sc.getNode(PREFS_BASE).put(processType, commandLauncherID);
		return previous;
	}

	/**
	 * Return boolean indicating if the Project Scoped preferences are enabled
	 * @param project
	 * @return boolean indicating if project scoped preferences exists for passed in project
	 */
	public static boolean hasProjectScopedCommandLaunchPreferences(IProject project) {
		if (project == null)
			return false;
		try {
			Preferences prefs = new ProjectScope(project).getNode(PREFS_BASE);
			if (prefs.keys().length == 0)
				return false;

			// Also have no project specific if global  / non-global don't have at least one preference key set
			boolean custom = prefs.getBoolean(USE_CUSTOM_PROCESS_TYPES, false);
			prefs.putBoolean(USE_CUSTOM_PROCESS_TYPES, custom);

			if (prefs.keys().length < 2)
				return false;
			if (prefs.keys().length == 2) {
				String allCommandLauncher = prefs.get(PROCESS_TYPE_ALL, null);
				if (!custom && allCommandLauncher == null)
					return false;
				if (custom && allCommandLauncher != null)
					return false;
			}
		} catch (BackingStoreException e) {
			CCorePlugin.log(e);
		}
		return true;
	}

	//
	//  CommandLauncher Creation
	//

	/**
	 * Create a command launcher for a specified processType
	 *
	 * @param project project in which the process is being run (may be null)
	 * @param processType Type of process to be run
	 * @param context process type specific context (may be null)
	 * @return ICommandLauncher which can be used to create and execute {@link Process}es
	 */
	public static ICommandLauncher createCommandLauncher(IProject project, String processType, Object context) {
		CommandLauncher launcher = null;

		// Are we using a Single Command Launcher for all commands, or custom one for different process types?
		String procType = processType;
		String custom = getPreferenceProcessTypeMappings(project).get(USE_CUSTOM_PROCESS_TYPES);
		if (!"true".equals(custom)) //$NON-NLS-1$
			procType = PROCESS_TYPE_ALL;
		// commandType for the procType
		String commandType = getPreferenceProcessTypeMappings(project).get(procType);

		launcher = getCommandLauncher(commandType);
		launcher.setProject(project);
		launcher.setContext(context);
		launcher.setProcessType(processType); // Note that the CommandLauncher might still be interested in the original processType we're launching...
		return launcher;
	}

	//
	// CommandLauncher contributions
	//

	/**
	 * Return a specific command launcher as specified by its ID
	 * @param launcherID
	 * @return ICommandLauncher requested or the default CommandLauncher if one couldn't be found
	 */
	public static CommandLauncher getCommandLauncher(String launcherID) {
		if (launcherID != null) {
			CommandLauncherType clt = getCommandLauncherExtensionPoints().get(launcherID);
			if (clt != null)
				return clt.getCommandLauncher();
		}
		return new CommandLauncher();
	}

	/**
	 * Get a Map of Command Launcher name -> Command launcher ID which support launching
	 * the specified process type.
	 * @param processType ID of the process type to be launched (or null / PROCESS_TYPE_ALL for any processType)
	 * @return Map CommandLauncher name -> Id
	 */
	public static Map<String, String> getSupportedCommandLaunchers(String processType) {
		Map<String, CommandLauncherType> clt = getCommandLauncherExtensionPoints();
		Map<String, String> supportedLaunchers = new LinkedHashMap<String, String>();
		// Add the default built-in command launcher
		supportedLaunchers.put(CommandLauncher.NAME, DEFAULT_COMMAND_LAUNCHER);
		for (Map.Entry<String, CommandLauncherType> e : clt.entrySet())
			if (processType == null || processType.equals(PROCESS_TYPE_ALL)) {
				// check whether the command launcher supports all the known process types
				if (e.getValue().supportedType(perProcessTypeCommandLaunchPrefs.keySet().toArray(new String[perProcessTypeCommandLaunchPrefs.size()])))
					supportedLaunchers.put(e.getValue().name, e.getKey());
			} else if (e.getValue().supportedType(processType))
				supportedLaunchers.put(e.getValue().name, e.getKey());
		return supportedLaunchers;
	}

	/**
	 * Class representing a CommandLauncher contributed via the extension point
	 * These have such fields
	 */
	private static class CommandLauncherType {
		private static final String ID = "id"; //$NON-NLS-1$
		private static final String NAME = "name"; //$NON-NLS-1$
		private static final String CLASS = "class"; //$NON-NLS-1$
		private static final String PROCESS_TYPE = "processType"; //$NON-NLS-1$

		final String id;
		final String name;
		final String[] applicableProcessTypes;
		private final CommandLauncher launcher;

		public CommandLauncherType(String id, String name, String[] processTypes, CommandLauncher launcher) {
			this.id = id;
			this.name = name;
			this.applicableProcessTypes = processTypes;
			this.launcher = launcher;
		}

		/**
		 * @param processType process type (must not be null)
		 * @return boolean indicating whether this CommandLauncher supported the passed in processType
		 */
		public boolean supportedType(String processType) {
			for (String type : applicableProcessTypes)
				if (processType.matches(type))
					return true;
			return false;
		}

		/**
		 * @param processTypes array of process types which we should match
		 * @return boolean indicating whether this CommandLauncher supported the processTypes array
		 */
		public boolean supportedType(String[] processTypes) {
			outer:
			for (String pType : processTypes) {
				for (String type : applicableProcessTypes)
					if (pType.matches(type))
						continue outer;
				// If we reach here then we didn't match all the passed in processTypes
				return false;
			}
			return true;
		}

		@Override
		public int hashCode() {
			return id.hashCode();
		}
		@Override
		public boolean equals(Object obj) {
			if (obj == null || !(obj instanceof CommandLauncherType))
				return false;
			return id.equals(((CommandLauncherType)obj).id);
		}

		/**
		 * Return a new instance of the CommandLauncher described by this CommandLauncherType
		 * @return new instance of the ICommandLauncher
		 */
		public CommandLauncher getCommandLauncher() {
			try {
				return launcher.getClass().newInstance();
			} catch (Exception e) {
				CCorePlugin.log(e);
			}
			return launcher;
		}

		/**
		 * Create the CommandLauncherType from the configuration element
		 *
		 * @param cfgEl
		 * @return CommandLauncherType or null on failure
		 */
		private static CommandLauncherType createType(IConfigurationElement cfgEl) {
			try {
				String id = cfgEl.getAttribute(ID);
				if (id == null)
					throw ExceptionFactory.createCoreException("id attribute must not be null!"); //$NON-NLS-1$
				String name = cfgEl.getAttribute(NAME);
				if (name == null)
					throw ExceptionFactory.createCoreException("name attribute must not be null!"); //$NON-NLS-1$
				if (cfgEl.getAttribute(CLASS) == null)
					throw ExceptionFactory.createCoreException("class attribute must not be null!"); //$NON-NLS-1$
				CommandLauncher launcher = (CommandLauncher)cfgEl.createExecutableExtension(CLASS);

				ArrayList<String> pTypes = new ArrayList<String>();
				for (IConfigurationElement child : cfgEl.getChildren(PROCESS_TYPE)) {
					String pType = child.getAttribute("id"); //$NON-NLS-1$
					if (pType == null)
						throw ExceptionFactory.createCoreException("supported processType id mustn't be null"); //$NON-NLS-1$
					pTypes.add(pType);
				}
				return new CommandLauncherType(id, name, pTypes.toArray(new String[pTypes.size()]), launcher);
			} catch (Exception e) {
				CCorePlugin.log(ExceptionFactory.createCoreException("Error instantiating: " + CCorePlugin.PLUGIN_ID + COMMAND_LAUNCHER_EXT_POINT + " extension point", e)); //$NON-NLS-1$ //$NON-NLS-2$
			}
			return null;
		}
	}

	/** Cache of the launcher types as discovered from the CommandLauncher extension point */
	private static volatile Reference<Map<String, CommandLauncherType>> commandLauncherTypes = new SoftReference<Map<String, CommandLauncherType>>(null);

	/**
	 * Initialize the command launcher types
	 * @return Map CommandLauncher Type ID -> CommandLauncherType
	 */
	private static Map<String, CommandLauncherType> getCommandLauncherExtensionPoints() {
		Map<String, CommandLauncherType> commandLaunchers = commandLauncherTypes.get();
		if (commandLaunchers != null)
			return commandLaunchers;
		commandLaunchers = new HashMap<String, CommandLauncherType>();
        IExtensionPoint extpoint = Platform.getExtensionRegistry().getExtensionPoint(CCorePlugin.PLUGIN_ID, COMMAND_LAUNCHER_EXT_POINT);
		for (IExtension extension : extpoint.getExtensions()) {
			for (IConfigurationElement configEl : extension.getConfigurationElements()) {
				if (configEl.getName().equalsIgnoreCase(COMMAND_LAUNCHER_EXT_POINT)) {
					CommandLauncherType type = CommandLauncherType.createType(configEl);
					if (type != null)
						commandLaunchers.put(type.id, type);
				}
			}
		}
		commandLauncherTypes = new SoftReference<Map<String,CommandLauncherType>>(commandLaunchers);
		return commandLaunchers;
	}

}