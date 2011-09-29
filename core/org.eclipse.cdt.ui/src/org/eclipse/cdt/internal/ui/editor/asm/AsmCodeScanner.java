/*******************************************************************************
 *  Copyright (c) 2005, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *     QNX Software System
 *     Anton Leherbauer (Wind River Systems)
 *     Andrew Ferguson (Symbian)
 *     James Blackburn (Broadcom Corp.)
 *******************************************************************************/
package org.eclipse.cdt.internal.ui.editor.asm;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.WhitespaceRule;
import org.eclipse.jface.text.rules.WordPatternRule;
import org.eclipse.jface.text.rules.WordRule;

import org.eclipse.cdt.core.model.IAsmLanguage;
import org.eclipse.cdt.ui.text.AbstractCScanner;
import org.eclipse.cdt.ui.text.ICColorConstants;
import org.eclipse.cdt.ui.text.ITokenStoreFactory;

import org.eclipse.cdt.internal.ui.text.util.CWhitespaceDetector;


/*
 * An assembly code scanner.
 */
public final class AsmCodeScanner extends AbstractCScanner {
	
	private static String[] fgTokenProperties= {
		IASMColorConstants.ASM_DIRECTIVE,
		IASMColorConstants.ASM_LABEL,
		IASMColorConstants.ASM_MNEMONIC,
		IASMColorConstants.ASM_BRANCH,
		IASMColorConstants.C_KEYWORD,
		IASMColorConstants.C_SINGLE_LINE_COMMENT,
		IASMColorConstants.C_DEFAULT
	};

	private IAsmLanguage fAsmLanguage;
	
	/**
	 * Creates an assembly code scanner.
	 */
	public AsmCodeScanner(ITokenStoreFactory factory, IAsmLanguage asmLanguage) {
		super(factory.createTokenStore(fgTokenProperties));
		fAsmLanguage= asmLanguage;
		setRules(createRules());
	}

	protected List<IRule> createRules() {
		IToken token;
		List<IRule> rules= new ArrayList<IRule>();

		// Add rule(s) for single line comments
		token= getToken(ICColorConstants.C_SINGLE_LINE_COMMENT);
		char[] lineCommentChars= fAsmLanguage.getLineCommentCharacters();
		for (int i= 0; i < lineCommentChars.length; i++) {
			rules.add(new EndOfLineRule(new String(new char [] {lineCommentChars[i]}), token));
		}

		// Add generic whitespace rule.
		rules.add(new WhitespaceRule(new CWhitespaceDetector()));

		String current = getPreferenceStore().getString(IASMColorConstants.ASM_MNEMONIC_CURRENT);
		String[] mnemonics;
		String[] branchMnemonics;

		// If the user has supplied custom mnemonics, use those
		if (current.length() > 0) {
			//Fetch mnemonics
			String mns = getPreferenceStore().getString(IASMColorConstants.ASM_MNEMONIC_ +	current);
			mnemonics = mns.split(IASMColorConstants.ASM_SEPARATOR);
			//Add branch mnemonics
			String branches = getPreferenceStore().getString(IASMColorConstants.ASM_BRANCHES_ + current);
			branchMnemonics = branches.split(IASMColorConstants.ASM_SEPARATOR);
		} else {
			mnemonics = fAsmLanguage.getMnemonics();
			branchMnemonics = fAsmLanguage.getBranchMnemonics();
		}

		// C Default colors used for non matching tokens
		final IToken other= getToken(ICColorConstants.C_DEFAULT);		

		// Mnemonics rule
		AsmMnemonicRule mnemonicsRule = new AsmMnemonicRule(new AsmWordDetector('.') {
			@Override
			public boolean isWordPart(char c) {
				// Allow '.' for predicated instructions
				return c == '.' || super.isWordPart(c);
			}
		}, other);
		// Add the mnemonics
		if (mnemonics != null && mnemonics.length > 0) {
			IToken asmToken= getToken(IASMColorConstants.ASM_MNEMONIC);
			for (String mn : mnemonics)
				mnemonicsRule.addWord(mn, asmToken);
		}
		// Add branch mnemonics rule
		if (branchMnemonics != null && branchMnemonics.length > 0) {
			IToken branchToken = getToken(IASMColorConstants.ASM_BRANCH);
			for (String branch : branchMnemonics)
				mnemonicsRule.addBranchWord(branch, branchToken);
		}
		rules.add(mnemonicsRule);

		// Add rule for labels
		token= getToken(IASMColorConstants.ASM_LABEL);
		IRule labelRule= new AsmLabelRule(new AsmWordDetector(false), token, other);
		rules.add(labelRule);
		
		// Add word rule for keywords
		token= getToken(IASMColorConstants.ASM_DIRECTIVE);
		String[] keywords= fAsmLanguage.getDirectiveKeywords();
		WordRule wordRule= new WordRule(new AsmWordDetector('.'), other);
		for (int i=0; i<keywords.length; i++)
			wordRule.addWord(keywords[i], token);
		rules.add(wordRule);

		// TODO use extra color?
		token= getToken(ICColorConstants.C_KEYWORD);
		WordPatternRule regPattern= new WordPatternRule(new AsmWordDetector('%', (char)0), "%", null, token); //$NON-NLS-1$
		rules.add(regPattern);

		setDefaultReturnToken(other);
		return rules;
	}
}