/*******************************************************************************
 * Copyright (c) 2010 Broadcom Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Alex Collins (Broadcom Corporation) - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.core.settings.model;

/**
 * Represents a reference from a configuration to another configuration.
 * The reference is uniquely determined by the target projects name and the id
 * of the configuration.
 *
 * @since 5.3
 * @noextend This interface is not intended to be extended by clients.
 * @noimplement This interface is not intended to be implemented by clients.
 */
public interface ICReferenceEntry {
	/**
	 * @return the name of the project being referenced.
	 */
	String getProject();
	/**
	 * @return the id of the configuration being referenced.
	 */
	String getConfiguration();
}
