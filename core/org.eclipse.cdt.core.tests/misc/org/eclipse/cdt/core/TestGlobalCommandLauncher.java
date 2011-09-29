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

/**
 * TestGlobalCommandLauncher
 *
 * This Command Launcher is applicable to all processTypes
 */
public class TestGlobalCommandLauncher extends TestCommandLauncher implements ICommandLauncher {
	@SuppressWarnings("hiding")
	public static final String ID = "org.eclipse.cdt.core.tests.TestGlobalCommandLauncher"; //$NON-NLS-1$

	public TestGlobalCommandLauncher() {
		super(ID);
	}
}
