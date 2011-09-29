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
 * TestCommandLauncher
 *
 * This CommandLauncher is only applicable to cdt.launch* processTypes
 */
public class TestCommandLauncher extends CommandLauncher implements ICommandLauncher {
	@SuppressWarnings("hiding")
	public static final String ID = "org.eclipse.cdt.core.tests.TestCommandLauncher"; //$NON-NLS-1$

	public TestCommandLauncher() {
		super(ID);
	}

	public TestCommandLauncher(String id) {
		super(id);
	}

	@Override
	public String getPreference(String key) {
		return super.getPreference(key);
	}

	@Override
	public Object getContext() {
		return super.getContext();
	}

}
