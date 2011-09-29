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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WordRule;

/**
 * A word rule matching both general assembler mnemonics 
 * and branch assembler mnemonics
 *
 * Assembler mnemonics are treated case insensitive
 * and may be preceded by a predicate {string}.{mnemonic}
 *
 * @since 6.1
 */
final class AsmMnemonicRule extends WordRule {
	private StringBuilder fBuffer= new StringBuilder();

	private Map<String, IToken> fBranchWords = new HashMap<String,IToken>();

	public AsmMnemonicRule(IWordDetector detector, IToken defaultToken) {
		super(detector, defaultToken);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.text.rules.WordRule#evaluate(org.eclipse.jface.text.rules.ICharacterScanner)
	 *
	 * This rule matches predicated mnemonics as well as the word itself.
	 */
	@Override
	public IToken evaluate(ICharacterScanner scanner) {
		int c= scanner.read();
		if (fDetector.isWordStart((char) c)) {
			if (fColumn == UNDEFINED || (fColumn == scanner.getColumn() - 1)) {
				fBuffer.setLength(0);
				do {
					fBuffer.append((char) c);
					c = scanner.read();
				} while (c != ICharacterScanner.EOF && fDetector.isWordPart((char) c));
				scanner.unread();

				// If we match pX.ASM i.e a predicated mnemonic is discovered, we want to drop
				// the predicated bit (pX.).
				// Therefore return default token for discovered (pX.)
				// otherwise return IToken.UNDEFINED if no mnemonics are discovered.
				int indexOfDot = fBuffer.lastIndexOf("."); //$NON-NLS-1$
				String st = fBuffer.substring(indexOfDot + 1, fBuffer.length()).toLowerCase();

				IToken token= fBranchWords.get(st);
				if (token == null)
					token = (IToken) fWords.get(st);

				// We have a mnemonic
				if (token != null) {
					if (indexOfDot != -1) {
						// We don't want to highlight the bit up to the dot
						int len = st.length();
						while (len-- > 0)
							scanner.unread();
						return fDefaultToken;
					}
					return token;
				}

				// No mnemonic, unwind the scanner
				int len = fBuffer.length();
				while (len-- >0)
					scanner.unread();
				return Token.UNDEFINED;
			}
		}
		scanner.unread();
		return Token.UNDEFINED;
	}

	/**
	 * Add Branch mnemonics to search for
	 * @param word
	 * @param token
	 */
	public void addBranchWord(String word, IToken token) {
		Assert.isNotNull(word);
		Assert.isNotNull(token);

		fBranchWords.put(word, token);
	}
}