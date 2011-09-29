/*******************************************************************************
 * Copyright (c) 2011 Broadcom Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.ui.build;

import java.util.ArrayList;
import java.util.Arrays;

import org.eclipse.core.resources.IBuildConfiguration;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

/**
 * An entry in the build history
 */
public class BuildHistoryEntry {

	public final IBuildConfiguration[] configs;

	public BuildHistoryEntry(IBuildConfiguration[] configs) {
		assert configs != null;
		this.configs = configs;
	}

	public BuildHistoryEntry(IProject[] projects) {
		ArrayList<IBuildConfiguration> config = new ArrayList<IBuildConfiguration>();
		for (IProject p : projects) {
			if (p.isAccessible()) {
				try {
					config.add(p.getActiveBuildConfig());
				} catch (CoreException e) {
					// Project not accessible, don't care as can't build it anyway...
				}
			}
		}
		configs = config.toArray(new IBuildConfiguration[config.size()]);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(configs);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BuildHistoryEntry other = (BuildHistoryEntry) obj;
		if (!Arrays.equals(configs, other.configs))
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (IBuildConfiguration config : configs)
			sb.append(config.getProject().getName()).append("[").append(config.getName()).append("]")  //$NON-NLS-1$//$NON-NLS-2$
							.append("; "); //$NON-NLS-1$
		sb.setLength(sb.length() - 2);
		return sb.toString();
	}
}
