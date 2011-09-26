/*******************************************************************************
 * Copyright (c) 2010 Broadcom Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Alex Collins (Broadcom Corporation) - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.core.settings.model;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.cdt.core.settings.model.CReferenceEntry;
import org.eclipse.cdt.core.settings.model.ICReferenceEntry;
import org.eclipse.cdt.core.settings.model.ICStorageElement;

/**
 * Serialization utilities for ICReferenceEntry objects. Allows them to be read
 * from and written to a file.
 *
 * @noextend This class is not intended to be subclassed by clients.
 */
public class ReferenceEntriesSerializer {
	public static final String ELEMENT_REFERENCE = "reference"; //$NON-NLS-1$
	public static final String ATTRIBUTE_PROJECT = "project"; //$NON-NLS-1$
	public static final String ATTRIBUTE_CFG = "configuration"; //$NON-NLS-1$

	public static ICReferenceEntry[] loadEntries(ICStorageElement el){
		List<ICReferenceEntry> list = loadEntriesList(el);
		return list.toArray(new ICReferenceEntry[list.size()]);
	}

	public static List<ICReferenceEntry> loadEntriesList(ICStorageElement el){
		List<ICReferenceEntry> list = new ArrayList<ICReferenceEntry>();
		for (ICStorageElement child: el.getChildren()) {
			if (ELEMENT_REFERENCE.equals(child.getName())){
				ICReferenceEntry entry = loadEntry(child);
				if (entry != null)
					list.add(entry);
			}
		}
		return list;
	}

	public static ICReferenceEntry loadEntry(ICStorageElement el){
		String project = loadString(el, ATTRIBUTE_PROJECT);
		String cfg = loadString(el, ATTRIBUTE_CFG);
		return new CReferenceEntry(project, cfg);
	}

	private static String loadString(ICStorageElement el, String attr) {
		String value = el.getAttribute(attr);
		if (value != null)
			return value;
		return null;
	}

	public static void serializeEntries(ICReferenceEntry refs[], ICStorageElement element) {
		if (refs != null) {
			for (ICReferenceEntry ref : refs) {
				ICStorageElement child = element.createChild(ELEMENT_REFERENCE);
				serializeEntries(ref, child);
			}
		}
	}

	public static void serializeEntries(ICReferenceEntry reference, ICStorageElement element) {
		String project = reference.getProject();
		String cfg = reference.getConfiguration();
		element.setAttribute(ATTRIBUTE_PROJECT, project);
		element.setAttribute(ATTRIBUTE_CFG, cfg);
	}
}
