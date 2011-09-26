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
 * @since 5.3
 * @noextend This class is not intended to be subclassed by clients.
 */
public class CReferenceEntry implements ICReferenceEntry {
	private final String project;
	private final String configuration;

	public CReferenceEntry(String project, String configuration) {
		this.project = project;
		this.configuration = configuration;
	}

	public String getProject() {
		return project;
	}

	public String getConfiguration() {
		return configuration;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = prime + project.hashCode();
		result = prime * result + (configuration == null ? 0 : configuration.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		CReferenceEntry ref = (CReferenceEntry) obj;
		if (!project.equals(ref.project))
			return false;
		if ((configuration == null) != (ref.configuration == null))
			return false;
		if (configuration != null && !configuration.equals(ref.configuration))
			return false;
		return true;
	}
}
