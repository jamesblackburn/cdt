/*******************************************************************************
 * Copyright (c) 2007, 2010 Wind River Systems, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Anton Leherbauer (Wind River Systems) - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.core.model;

import java.util.Set;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.dom.ILinkage;
import org.eclipse.cdt.core.dom.ast.IASTCompletionNode;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.index.IIndex;
import org.eclipse.cdt.core.parser.IParserLogService;
import org.eclipse.cdt.core.parser.IScannerInfo;
import org.eclipse.cdt.core.parser.KeywordSetKey;
import org.eclipse.cdt.core.parser.ParserFactory;
import org.eclipse.cdt.core.parser.ParserLanguage;
import org.eclipse.cdt.internal.core.model.AsmModelBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;

/**
 * Built-in language for assembly files.
 *
 * @since 4.0
 */
public class AssemblyLanguage extends AbstractLanguage implements IAsmLanguage, IExecutableExtension {

	/** An array of Assembler directive keywords for this language
	 * @since 5.3*/
	protected static String[] DIRECTIVE_KEYWORDS= {
		".set", ".section", //$NON-NLS-1$ //$NON-NLS-2$
		".global", ".globl", ".extern", ".type", ".file", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		".if", ".ifdef", ".ifndef", ".else", ".endif", ".include", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		".macro", ".endm", //$NON-NLS-1$ //$NON-NLS-2$
		".func", ".endfunc",  //$NON-NLS-1$//$NON-NLS-2$
		".text", ".data", ".rodata", ".common", ".debug", ".ctor", ".dtor", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
		".ascii", ".asciz", ".byte", ".long", ".size", ".align", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		".short", ".word", ".float", ".single", ".double" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
	};

	/** Assembler Mnemonics of this language
	 * @since 5.3*/
	protected static String[] ASM_MNEMONICS = new String[] {};
	/** The Mnemonics for the languages branhces 
	 * @since 5.3*/
	protected static String[] BRANCH_MNEMONICS = new String[] {};

	private char[] fLineCommentCharacters= {};


	private static final String DEFAULT_ID= "org.eclipse.cdt.core.assembly"; //$NON-NLS-1$
	private String fId= DEFAULT_ID;

	public static final AssemblyLanguage DEFAULT_INSTANCE= new AssemblyLanguage();

	/**
	 * @return the default language instance
	 */
	public static AssemblyLanguage getDefault() {
		return DEFAULT_INSTANCE;
	}

	/*
	 * @see org.eclipse.cdt.core.model.ILanguage#createModelBuilder(org.eclipse.cdt.core.model.ITranslationUnit)
	 */
	public IContributedModelBuilder createModelBuilder(ITranslationUnit tu) {
		IContributedModelBuilder modelBuilder= null;
		IContributedModelBuilder.Factory modelBuilderFactory= (IContributedModelBuilder.Factory)getAdapter(IContributedModelBuilder.Factory.class);
		if (modelBuilderFactory != null) {
			modelBuilder= modelBuilderFactory.create(tu);
		}
		if (modelBuilder == null) {
			// use default
			AsmModelBuilder defaultModelBuilder= new AsmModelBuilder(tu);
			defaultModelBuilder.setLineSeparatorCharacter(getLineSeparatorCharacter());
			modelBuilder= defaultModelBuilder;
		}
		return modelBuilder;
	}

	/*
	 * @see org.eclipse.cdt.core.model.ILanguage#getASTTranslationUnit(org.eclipse.cdt.core.parser.CodeReader, org.eclipse.cdt.core.parser.IScannerInfo, org.eclipse.cdt.core.dom.ICodeReaderFactory, org.eclipse.cdt.core.index.IIndex, org.eclipse.cdt.core.parser.IParserLogService)
	 */
	@Deprecated
	public IASTTranslationUnit getASTTranslationUnit(org.eclipse.cdt.core.parser.CodeReader reader,
			IScannerInfo scanInfo, org.eclipse.cdt.core.dom.ICodeReaderFactory fileCreator, IIndex index,
			IParserLogService log) throws CoreException {
		return null;
	}

	/*
	 * @see org.eclipse.cdt.core.model.ILanguage#getCompletionNode(org.eclipse.cdt.core.parser.CodeReader, org.eclipse.cdt.core.parser.IScannerInfo, org.eclipse.cdt.core.dom.ICodeReaderFactory, org.eclipse.cdt.core.index.IIndex, org.eclipse.cdt.core.parser.IParserLogService, int)
	 */
	@Deprecated
	public IASTCompletionNode getCompletionNode(org.eclipse.cdt.core.parser.CodeReader reader,
			IScannerInfo scanInfo, org.eclipse.cdt.core.dom.ICodeReaderFactory fileCreator, IIndex index,
			IParserLogService log, int offset) throws CoreException {
		return null;
	}

	/*
	 * @see org.eclipse.cdt.core.model.ILanguage#getId()
	 */
	public String getId() {
		return fId;
	}

	/*
	 * @see org.eclipse.cdt.core.model.ILanguage#getSelectedNames(org.eclipse.cdt.core.dom.ast.IASTTranslationUnit, int, int)
	 */
	public IASTName[] getSelectedNames(IASTTranslationUnit ast, int start, int length) {
		return null;
	}

	// IAsmLanguage
	
	/*
	 * @see org.eclipse.cdt.core.model.IAsmLanguage#getLineCommentCharacters()
	 */
	public char[] getLineCommentCharacters() {
		return fLineCommentCharacters;
	}

	/*
	 * @see org.eclipse.cdt.core.model.IAsmLanguage#getLineSeparatorCharacter()
	 */
	public char getLineSeparatorCharacter() {
		return '\0';
	}

	/*
	 * @see org.eclipse.cdt.core.model.IAsmLanguage#getDirectiveKeywords()
	 */
	public String[] getDirectiveKeywords() {
		return DIRECTIVE_KEYWORDS;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.cdt.core.model.IAsmLanguage#getMnemonics()
	 */
	/**
	 * @since 5.3
	 */
	public String[] getMnemonics() {
		return ASM_MNEMONICS; 
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.cdt.core.model.IAsmLanguage#getBranchMnemonics()
	 */
	/**
	 * @since 5.3
	 */
	public String[] getBranchMnemonics() {
		return BRANCH_MNEMONICS;
	}

	/*
	 * @see org.eclipse.cdt.core.model.IAsmLanguage#getPreprocessorKeywords()
	 */
	public String[] getPreprocessorKeywords() {
		Set<String> ppDirectives= ParserFactory.getKeywordSet(KeywordSetKey.PP_DIRECTIVE, ParserLanguage.C);
		String[] result= ppDirectives.toArray(new String[ppDirectives.size()]);
		return result;
	}

	/*
	 * @see org.eclipse.core.runtime.IExecutableExtension#setInitializationData(org.eclipse.core.runtime.IConfigurationElement, java.lang.String, java.lang.Object)
	 */
	public void setInitializationData(IConfigurationElement config, String propertyName, Object data) throws CoreException {
		if (data instanceof String) {
			fLineCommentCharacters= ((String)data).toCharArray();
		}
		fId= CCorePlugin.PLUGIN_ID + '.' + config.getAttribute("id"); //$NON-NLS-1$
	}

	public int getLinkageID() {
		return ILinkage.NO_LINKAGE_ID;
	}
}
