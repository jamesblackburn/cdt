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
package org.eclipse.cdt.internal.ui.dialogs;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "org.eclipse.cdt.internal.ui.dialogs.messages"; //$NON-NLS-1$
	public static String WrappedCommandLauncherDialog_AdvancedTitle;
	public static String WrappedCommandLauncherDialog_AppendedArgs;
	public static String WrappedCommandLauncherDialog_egAll;
	public static String WrappedCommandLauncherDialog_egMake;
	public static String WrappedCommandLauncherDialog_NewCommand;
	public static String WrappedCommandLauncherDialog_OriginalArgs;
	public static String WrappedCommandLauncherDialog_OriginalCommand;
	public static String WrappedCommandLauncherDialog_PrependCommand;
	public static String WrappedCommandLauncherDialog_WrapDescription;
	public static String WrappedCommandLauncherDialog_WrapExample;
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
