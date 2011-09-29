/*******************************************************************************
 * Copyright (c) 2010 Broadcom Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * James Blackburn (Broadcom Corp.) - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.core.settings.model;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

/**
 * A multi-item project description allows easy access to the all the configurations
 * in multiple project configs simultaneously.
 *
 * @noextend This interface is not intended to be extended by clients.
 * @noimplement This interface is not intended to be implemented by clients.
 * @since 5.3
 */
public interface ICMultiProjectDescription extends ICMultiItemsHolder<ICProjectDescription>, ICProjectDescription {

	/**
	 * Fetch the configuration with specified if from the project description for project
	 * @param project
	 * @param id {@link ICConfigurationDescription} id to search for
	 * @return {@link ICConfigurationDescription} null if configuration or project description not contained in this {@link ICMultiProjectDescription}
	 */
	ICConfigurationDescription getConfigurationById(IProject project, String id);

	/**
	 * Persist all the project descriptions contained within this ICMultiProjectDescription
	 */
	void setProjectDescriptions() throws CoreException;

}
