/*******************************************************************************
 * Copyright (c) 2005, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     James Blackburn (Broadcom Corp.)
 *******************************************************************************/
package org.eclipse.cdt.core;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

import org.eclipse.cdt.internal.core.ProcessClosure;
import org.eclipse.cdt.internal.core.settings.model.ExceptionFactory;
import org.eclipse.cdt.utils.spawner.EnvironmentReader;
import org.eclipse.cdt.utils.spawner.ProcessFactory;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

/**
 * Base CommandLauncher which should be used by extenders wishing
 * to contribute specific {@link java.lang.Process} construction capabilities to
 * CDT. This CommandLauncher creates normal local processes.
 *
 * Contributed CommandLaunchers may have custom 'Advanced' user-specified
 * Preferences which are accessible via the {@link #getPreference(String)} method
 * on this class.  They may contribute UI to configure these Preferences
 * via the org.eclipse.cdt.ui.CommandLauncherDialog extension point.
 */
public class CommandLauncher implements ICommandLauncher {

	public final static int COMMAND_CANCELED = ICommandLauncher.COMMAND_CANCELED;
	public final static int ILLEGAL_COMMAND = ICommandLauncher.ILLEGAL_COMMAND;
	public final static int OK = ICommandLauncher.OK;

	/** @since 5.3*/
	public static final String ID = "Default"; //$NON-NLS-1$
	/** @since 5.3*/
	public static final String NAME = CCorePlugin.getResourceString("CommandLauncher.Default"); //$NON-NLS-1$

	/** ID of this command launcher.
	 * Should be the same as the extension point ID;
	 * used for fetching CommandLauncher Preferences
	 * @since 5.3
	 */
	public final String fID;

	protected Process fProcess;
	protected boolean fShowCommand;
	protected String[] fCommandArgs;

	protected String fErrorMessage = ""; //$NON-NLS-1$

	private final String lineSeparator;
	private IProject fProject;
	private Object fContext;
	/**
     * @since 5.3
	 */
	protected String fProcessType;

	/**
	 * The number of milliseconds to pause between polling.
	 */
	protected static final long DELAY = 50L;

	/**
	 * Creates a new default Process launcher Fills in stderr and stdout output to the given
	 * streams. Streams can be set to <code>null</code>, if output not
	 * required
	 */
	public CommandLauncher() {
		this(ID);
	}

	/**
	 * Constructor should be called by extenders with their ID
	 * @param id
	 * @since 5.3
	 */
	protected CommandLauncher(String id) {
		fID = id;
		fProcess = null;
		fShowCommand = false;
		lineSeparator = System.getProperty("line.separator", "\n"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.ICommandLauncher#showCommand(boolean)
	 */
	public void showCommand(boolean show) {
		fShowCommand = show;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.ICommandLauncher#getErrorMessage()
	 */
	public String getErrorMessage() {
		return fErrorMessage;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.ICommandLauncher#setErrorMessage(java.lang.String)
	 */
	public void setErrorMessage(String error) {
		fErrorMessage = error;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.ICommandLauncher#getCommandArgs()
	 */
	public String[] getCommandArgs() {
		return fCommandArgs;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.ICommandLauncher#getEnvironment()
	 */
	public Properties getEnvironment() {
		return EnvironmentReader.getEnvVars();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.ICommandLauncher#getCommandLine()
	 */
	public String getCommandLine() {
		return getCommandLine(getCommandArgs());
	}

	/**
	 * Constructs a command array that will be passed to the process
	 */
	protected String[] constructCommandArray(String command, String[] commandArgs) {
		String[] args = new String[1 + commandArgs.length];
		args[0] = command;
		System.arraycopy(commandArgs, 0, args, 1, commandArgs.length);
		return args;
	}

	/**
	 * @deprecated
	 * @since 5.1
	 */
	@Deprecated
	public Process execute(IPath commandPath, String[] args, String[] env, IPath changeToDirectory) {
		try {
			return execute(commandPath, args, env, changeToDirectory, new NullProgressMonitor());
		} catch (CoreException e) {
			setErrorMessage(e.getMessage());
			fProcess = null;
		}
		return fProcess;
	}

	/**
	 * @see org.eclipse.cdt.core.ICommandLauncher#execute(IPath, String[], String[], IPath, IProgressMonitor)
	 * @since 5.1
	 */
	public Process execute(IPath commandPath, String[] args, String[] env, IPath changeToDirectory, IProgressMonitor monitor) throws CoreException {
		return execute(constructCommandArray(commandPath.toOSString(), args), env, changeToDirectory, monitor);
	}

	/**
	 * @param cmdArray String[] of command and arguments
	 * @param env String[] of environment
	 * @param changeToDirectory IPath to use as CWD for running command (or null if not required)
	 * @param monitor IProgressMonitor
	 * @return Process
	 * @throws CoreException
	 * @since 5.3
	 */
	public Process execute(String[] cmdArray, String[] env, IPath changeToDirectory, IProgressMonitor monitor) throws CoreException {
		try {
			// add platform specific arguments (shell invocation)
			fCommandArgs = cmdArray;

			File file = null;

			if(changeToDirectory != null)
				file = changeToDirectory.toFile();

			fProcess = ProcessFactory.getFactory().exec(fCommandArgs, env, file);
			fErrorMessage = ""; //$NON-NLS-1$
		} catch (IOException e) {
			setErrorMessage(e.getMessage());
			throw ExceptionFactory.createCoreException(e);
		}
		return fProcess;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.ICommandLauncher#waitAndRead(java.io.OutputStream, java.io.OutputStream)
	 */
	public int waitAndRead(OutputStream out, OutputStream err) {
		if (fShowCommand) {
			printCommandLine(out);
		}

		if (fProcess == null) {
			return ILLEGAL_COMMAND;
		}

		ProcessClosure closure = new ProcessClosure(fProcess, out, err);
		closure.runBlocking(); // a blocking call
		return OK;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.core.ICommandLauncher#waitAndRead(java.io.OutputStream, java.io.OutputStream, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public int waitAndRead(OutputStream output, OutputStream err, IProgressMonitor monitor) {
		if (fShowCommand) {
			printCommandLine(output);
		}

		if (fProcess == null) {
			return ILLEGAL_COMMAND;
		}

		ProcessClosure closure = new ProcessClosure(fProcess, output, err);
		closure.runNonBlocking();
		while (!monitor.isCanceled() && closure.isAlive()) {
			try {
				Thread.sleep(DELAY);
			} catch (InterruptedException ie) {
				// ignore
			}
		}

		int state = OK;

		// Operation canceled by the user, terminate abnormally.
		if (monitor.isCanceled()) {
			closure.terminate();
			state = COMMAND_CANCELED;
			setErrorMessage(CCorePlugin.getResourceString("CommandLauncher.error.commandCanceled")); //$NON-NLS-1$
		}

		try {
			fProcess.waitFor();
		} catch (InterruptedException e) {
			// ignore
		}
		return state;
	}

	protected void printCommandLine(OutputStream os) {
		if (os != null) {
			String cmd = getCommandLine(getCommandArgs());
			try {
				os.write(cmd.getBytes());
				os.flush();
			} catch (IOException e) {
				// ignore;
			}
		}
	}

	protected String getCommandLine(String[] commandArgs) {
		StringBuffer buf = new StringBuffer();
		if (fCommandArgs != null) {
			for (String commandArg : commandArgs) {
				buf.append(commandArg);
				buf.append(' ');
			}
			buf.append(lineSeparator);
		}
		return buf.toString();
	}


	/**
	 * @since 5.1
	 * @see org.eclipse.cdt.core.ICommandLauncher#getProject()
	 */
	public IProject getProject() {
		return fProject;
	}

	/**
	 * @since 5.1
	 * @see org.eclipse.cdt.core.ICommandLauncher#setProject(org.eclipse.core.resources.IProject)
	 */
	public void setProject(IProject project) {
		fProject = project;
	}

	/**
	 * Process Launch specific context which the ICommandLauncher can use
	 * to assist in {@link Process} creation.
	 * @return context
	 * @since 5.3
	 */
	protected Object getContext() {
		return fContext;
	}

	/**
	 * Process Launch specific context which the ICommandLauncher can use
	 * to assist in {@link Process} creation.
	 * @param context
	 * @since 5.3
	 */
	public void setContext(Object context) {
		fContext = context;
	}

	/**
	 * Set the process type being launched by this CommandLauncher
	 * @param processType being launched
	 * @since 5.3
	 */
	public void setProcessType(String processType) {
		fProcessType = processType;
	}

	/**
	 * Helper method which returns the Preferences associated
	 * with the command launcher using scoped lookup
	 * @return String Preference value associated with the command launcher or null if not found
	 * @since 5.3
	 */
	protected String getPreference(String key) {
		// Are we using a Single Command Launcher for all commands, or custom one for different process types?
		String processType = fProcessType;
		// Are we at project or instance scope?
		IProject project = fProject;
		// If we don't have project scoped preferences, then we need to ensure that the Command Launcher
		// doesn't use 'Advanced' Prefs from the disabled Project Scoped prefs
		if (!CommandLauncherFactory.hasProjectScopedCommandLaunchPreferences(project))
			project = null;

		String custom = CommandLauncherFactory.getPreferenceProcessTypeMappings(project).get(CommandLauncherFactory.USE_CUSTOM_PROCESS_TYPES);
		if (!"true".equals(custom)) //$NON-NLS-1$
			processType = CommandLauncherFactory.PROCESS_TYPE_ALL;

		return CommandLauncherFactory.getAdvancedCommandLauncherPreference(project, fID, processType, key);
	}

}