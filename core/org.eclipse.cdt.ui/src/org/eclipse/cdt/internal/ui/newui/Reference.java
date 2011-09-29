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
package org.eclipse.cdt.internal.ui.newui;

import org.eclipse.cdt.core.settings.model.ICReferenceEntry;

/**
 * A wrapper class for CReferenceEntry objects that includes the referenced
 * configuration's name. This is used to represent the contents of the references
 * tab and dialogues.
 */
class Reference {
	private ICReferenceEntry ref;
	private final String cfgName;

	public Reference(ICReferenceEntry ref, String cfgName) {
		this.ref = ref;
		this.cfgName = cfgName;
	}

	public ICReferenceEntry getRef() {
		return ref;
	}

	public String getCfgName() {
		return cfgName;
	}

	@Override
	public int hashCode() {
		return ref.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Reference reference = (Reference) obj;
		if (!ref.equals(reference.ref))
			return false;
		return true;
	}
}