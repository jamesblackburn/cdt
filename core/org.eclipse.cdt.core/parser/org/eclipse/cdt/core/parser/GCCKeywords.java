/*******************************************************************************
 * Copyright (c) 2002, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Rational Software - Initial API and implementation
 *    Ed Swartz (Nokia)
 *******************************************************************************/
package org.eclipse.cdt.core.parser;

/**
 * @author jcamelon
 *
 */
public class GCCKeywords {
	
	public static final String TYPEOF = "typeof"; //$NON-NLS-1$
	public static final String __ALIGNOF__ = "__alignof__"; //$NON-NLS-1$
	public static final String __ATTRIBUTE__ = "__attribute__"; //$NON-NLS-1$
	public static final String __DECLSPEC = "__declspec"; //$NON-NLS-1$

	public static final char [] cpTYPEOF = TYPEOF.toCharArray();
	public static final char [] cp__ALIGNOF__ = __ALIGNOF__.toCharArray();
	public static final char [] cp__ATTRIBUTE__ = __ATTRIBUTE__.toCharArray();
	public static final char [] cp__DECLSPEC = __DECLSPEC.toCharArray();

	public static final char [] cp__ASM__= "__asm__".toCharArray(); //$NON-NLS-1$
	public static final char [] cp__CONST__= "__const__".toCharArray(); //$NON-NLS-1$
	public static final char [] cp__CONST= "__const".toCharArray(); //$NON-NLS-1$
	public static final char [] cp__INLINE__= "__inline__".toCharArray(); //$NON-NLS-1$
	public static final char [] cp__RESTRICT__= "__restrict__".toCharArray(); //$NON-NLS-1$
	public static final char [] cp__RESTRICT= "__restrict".toCharArray(); //$NON-NLS-1$
	public static final char [] cp__VOLATILE__= "__volatile__".toCharArray(); //$NON-NLS-1$
	public static final char [] cp__SIGNED__= "__signed__".toCharArray(); //$NON-NLS-1$
	public static final char [] cp__TYPEOF__= "__typeof__".toCharArray(); //$NON-NLS-1$
}
