/*
 *(c) Copyright Rational Software Corporation, 2002
 * All Rights Reserved.
 *
 */

package org.eclipse.cdt.debug.mi.core;

import org.eclipse.cdt.debug.mi.core.command.CommandFactory;
import org.eclipse.cdt.debug.mi.core.command.MIEnvironmentCD;
import org.eclipse.cdt.debug.mi.core.command.MIEnvironmentDirectory;

/**
 * Cygwin Command Factory overrides the regular Command Factory to allow for
 * commands to take into account the cygwin environment.
 */
public class CygwinCommandFactory extends CommandFactory {

	public CygwinCommandFactory(String miVersion) {
		super(miVersion);
	}

	public MIEnvironmentDirectory createMIEnvironmentDirectory(boolean reset, String[] pathdirs) {
		return new CygwinMIEnvironmentDirectory(getMIVersion(), reset, pathdirs);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.debug.mi.core.command.CommandFactory#createMIEnvironmentCD(java.lang.String)
	 */
	public MIEnvironmentCD createMIEnvironmentCD(String pathdir) {
		return new CygwinMIEnvironmentCD(getMIVersion(), pathdir);
	}
}
