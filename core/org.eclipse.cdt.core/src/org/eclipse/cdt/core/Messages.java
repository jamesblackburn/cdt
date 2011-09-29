/*******************************************************************************
 * Copyright (c) 2009 Broadcom Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     James Blackburn (Broadcom Corp.)
 *******************************************************************************/
package org.eclipse.cdt.core;

import org.eclipse.osgi.util.NLS;

/**
 * @noextend This class is not intended to be subclassed by clients.
 * @noinstantiate This class is not intended to be instantiated by clients.
 * @since 5.3
 */
public class Messages extends NLS {
	private static final String BUNDLE_NAME = "org.eclipse.cdt.core.messages"; //$NON-NLS-1$
	public static String CommandLauncherFactory_CustomCommandLauncher;
	public static String CommandLauncherFactory_DebugLaunch;
	public static String CommandLauncherFactory_MakefileBuild;
	public static String CommandLauncherFactory_ManagedBuild;
	public static String CommandLauncherFactory_ProfileLaunch;
	public static String CommandLauncherFactory_RunLaunch;
	public static String CommandLauncherFactory_SharedCommandLauncher;
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
