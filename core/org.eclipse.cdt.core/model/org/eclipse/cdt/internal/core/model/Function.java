package org.eclipse.cdt.internal.core.model;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.cdt.core.model.ICElement;
import org.eclipse.cdt.core.model.IFunction;

public class Function extends FunctionDeclaration implements IFunction {
	
	public Function(ICElement parent, String name) {
		super(parent, name, ICElement.C_FUNCTION);
	}
}
