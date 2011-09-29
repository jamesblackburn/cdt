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
package org.eclipse.cdt.internal.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.cdt.core.CommandLauncher;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * A command launcher which wraps commands with another command
 * and/or allows adding additional arguments to the command
 * @since 5.2
 */
public class WrappedCommandLauncher extends CommandLauncher {

	@SuppressWarnings("hiding")
	public static final String ID = "org.eclipse.cdt.core.commandlauncher.wrapped"; //$NON-NLS-1$

	/** Preference key of the wrapped command name */
	public static final String PREF_COMMAND_NAME = "CommandName"; //$NON-NLS-1$
	/** Preference key of prepended string arguments */
	public static final String PREF_PREPEND_ARGUMENT = "PrependArguments"; //$NON-NLS-1$
	/** Preference key of appended command arguments */
	public static final String PREF_APPEND_ARGUMENT = "AppendArguments"; //$NON-NLS-1$

	public WrappedCommandLauncher() {
		super(ID);
	}

	@Override
	public Process execute(String[] args, String[] env, IPath changeToDirectory, IProgressMonitor monitor) throws CoreException {
		args = augmentArguments(args);
		return super.execute(args, env, changeToDirectory, monitor);
	}

	public String[] augmentArguments(String[] origArgs) {
		List<String> args = new ArrayList<String>();

		// Override command name
		String name = getPreference(PREF_COMMAND_NAME);
		if (name != null && name.trim().length() != 0)
			args.addAll(Arrays.asList(name.split("\\s+"))); //$NON-NLS-1$
		// Add original command name
		args.add(origArgs[0]);
		// Prepended arguments
		String prepArguments = getPreference(PREF_PREPEND_ARGUMENT);
		if (prepArguments != null && prepArguments.trim().length() != 0)
			args.addAll(Arrays.asList(prepArguments.split("\\s+"))); //$NON-NLS-1$
		// Original Arguments
		for (int i = 1 ; i < origArgs.length ; i++)
			args.add(origArgs[i]);
		// Appended Arguments
		String appArguments = getPreference(PREF_APPEND_ARGUMENT);
		if (appArguments != null && appArguments.trim().length() != 0)
			args.addAll(Arrays.asList(appArguments.split("\\s+"))); //$NON-NLS-1$

		return args.toArray(new String[args.size()]);
	}
}
