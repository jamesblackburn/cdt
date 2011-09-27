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
package org.eclipse.cdt.managedbuilder.internal.core.tests;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Testsuite for builder correctness
 */
public class AllBuilderTests extends TestCase {

	public static Test suite() {
		TestSuite suite = new TestSuite(AllBuilderTests.class.getName());

		// Test that common builder does the correct amount of work.
		suite.addTestSuite(CommonBuilderTest.class);

		return suite;
	}

	public AllBuilderTests() {
		super(null);
	}

	public AllBuilderTests(String name) {
		super(name);
	}
}
