/*******************************************************************************
 * Copyright (c) 2009 Broadcom Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     James Blackburn (Broadcom Corp.) - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.ui.editor.asm;

import org.eclipse.cdt.ui.text.ICColorConstants;


/**
 * Color constants and assembler mnemonic storage for assembly 
 * highlighting
 *
 * Asm color constants are API and so this interface inherits from from 
 * ICColorConstants
 *
 * @since 6.1
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface IASMColorConstants extends ICColorConstants {
	
	/** Separator used for the assembler storage arrays */
	String ASM_SEPARATOR=";";//$NON-NLS-1$

	/* PreferenceStore keys for the Mnemonics themselves
	 *   This allows users to load in a text file of mnemonics so they
	 *   don't necessarily need an ISV to provide an IAsmLanguage implementation
	 *   They are stored as part of the preferences. */

	/** Current name of assembler mnemonics */
	String ASM_MNEMONIC_CURRENT= "asm_mnemonic_current";//$NON-NLS-1$
	/** Set of asm mnemonics supported */
	String ASM_MNEMONIC_NAMES= "asm_mnemonic_names";//$NON-NLS-1$
	/** Key prefix for the name of an assembler type */
	String ASM_MNEMONIC_= "asm_mnemonic_name_";//$NON-NLS-1$
	/** Key prefix for a list of branch mnemonics */
	String ASM_BRANCHES_ = "asm_mnemonic_branches_"; //$NON-NLS-1$
	/** Key prefix for a list of general mnemonics */
	String ASM_DIRECTIVE_ = "asm_mnemonic_directive_"; //$NON-NLS-1$
}
