/*******************************************************************************
 * Copyright (c) 2004, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM - Initial API and implementation
 *    Markus Schorn (Wind River Systems)
 *    Bryan Wilkinson (QNX)
 *    Andrew Ferguson (Symbian)
 *******************************************************************************/
package org.eclipse.cdt.internal.core.dom.parser.cpp.semantics;

import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTLiteralExpression;
import org.eclipse.cdt.core.dom.ast.IBasicType.Kind;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPBasicType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPFunction;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPMethod;
import org.eclipse.cdt.internal.core.dom.parser.ArithmeticConversion;
import org.eclipse.cdt.internal.core.dom.parser.Value;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPBasicType;

/**
 * The cost of an implicit conversion sequence.
 * 
 * See [over.best.ics] 13.3.3.1.
 */
public class Cost {
	public enum DeferredUDC {
		NONE, COPY_INIT_OF_CLASS, INIT_BY_CONVERSION, LIST_INIT_OF_CLASS, DIRECT_LIST_INIT_OF_CLASS
	}
	public enum Rank {
		IDENTITY, PROMOTION, CONVERSION, CONVERSION_PTR_BOOL, 
		USER_DEFINED_CONVERSION, ELLIPSIS_CONVERSION, NO_MATCH
	}
	enum ReferenceBinding {
		RVALUE_REF_BINDS_RVALUE, LVALUE_REF, OTHER_REF, NO_REF
	}

	public static final Cost NO_CONVERSION = new Cost(null, null, Rank.NO_MATCH) {
		@Override
		public void setRank(Rank rank) {
			assert false;
		}
		@Override
		public void setReferenceBinding(ReferenceBinding binding) {
			assert false;
		}
		@Override
		public void setAmbiguousUDC(boolean val) {
			assert false;
		}
		@Override
		public void setDeferredUDC(DeferredUDC val) {
			assert false;
		}
		@Override
		public void setInheritanceDistance(int inheritanceDistance) {
			assert false;
		}
		@Override
		public void setQualificationAdjustment(int adjustment) {
			assert false;
		}
		@Override
		public void setUserDefinedConversion(ICPPMethod conv) {
			assert false;
		}
		@Override
		public void setCouldNarrow() {
			assert false;
		}
		@Override
		public void setSelectedFunction(ICPPFunction function) {
			assert false;
		}
	};

	IType source;
	IType target;

	private Rank fRank;
	private Rank fSecondStandardConversionRank;
	private boolean fAmbiguousUDC;
	private DeferredUDC fDeferredUDC= DeferredUDC.NONE;
	private int fQualificationAdjustments;
	private int fInheritanceDistance;
	private boolean fImpliedObject;
	private ICPPFunction fUserDefinedConversion;
	private ReferenceBinding fReferenceBinding;

	private boolean fCouldNarrow;
	private ICPPFunction fSelectedFunction; // For targeted functions
	
	public Cost(IType s, IType t, Rank rank) {
		source = s;
		target = t;
		fRank= rank;
		fReferenceBinding= ReferenceBinding.NO_REF;
	}

	public final Rank getRank() {
		return fRank;
	}

	public final boolean converts() {
		return fRank != Rank.NO_MATCH;
	}
	
	public void setRank(Rank rank) {
		fRank= rank;
	}
	

	public ReferenceBinding getReferenceBinding() {
		return fReferenceBinding;
	}

	public void setReferenceBinding(ReferenceBinding binding) {
		fReferenceBinding= binding;
	}

	
	public boolean isAmbiguousUDC() {
		return fAmbiguousUDC;
	}

	public void setAmbiguousUDC(boolean val) {
		fAmbiguousUDC= val;
	}

	public DeferredUDC isDeferredUDC() {
		return fDeferredUDC;
	}

	public void setDeferredUDC(DeferredUDC udc) {
		fDeferredUDC= udc;
	}

	public int getInheritanceDistance() {
		return fInheritanceDistance;
	}

	public void setInheritanceDistance(int inheritanceDistance) {
		fInheritanceDistance = inheritanceDistance;
	}

	public void setQualificationAdjustment(int adjustment) {
		fQualificationAdjustments= adjustment;
	}

	/**
	 * Converts the cost for the second standard conversion into the overall cost for the
	 * implicit conversion sequence.
	 */
	public void setUserDefinedConversion(ICPPMethod conv) {
		fUserDefinedConversion= conv;
		fSecondStandardConversionRank= fRank;
		fRank= Rank.USER_DEFINED_CONVERSION;
		fCouldNarrow= false;
	}

	/**
	 * Returns an integer &lt 0 if other cost is <code>null</code>, or this cost is smaller than the other cost,
	 *        0 if this cost is equal to the other cost,
	 *        an integer &gt 0 if this cost is larger than the other cost.
	 */
	public int compareTo(Cost other) {
		if (other == null)
			return -1;
		
		// cannot compare costs with deferred user defined conversions
		assert fDeferredUDC == DeferredUDC.NONE && other.fDeferredUDC == DeferredUDC.NONE;

		// 7.3.3.13 (using declarations in classes):
		// for overload resolution the implicit this pointer 
		// is treated as if it were a pointer to the derived class
		final boolean ignoreInheritanceDist= fImpliedObject && other.fImpliedObject;
		Rank rank = fRank;
		Rank otherRank = other.fRank;
		if (ignoreInheritanceDist) {
			if (rank == Rank.CONVERSION)
				rank= Rank.IDENTITY;
			if (otherRank == Rank.CONVERSION)
				otherRank= Rank.IDENTITY;
		}

		int cmp= rank.compareTo(otherRank);
		if (cmp != 0) 
			return cmp;
		
		// rank is equal
		if (rank == Rank.USER_DEFINED_CONVERSION) {
			// 13.3.3.1.10
			if (isAmbiguousUDC() || other.isAmbiguousUDC())
				return 0;
			
			if (fUserDefinedConversion != other.fUserDefinedConversion) {
				if (fUserDefinedConversion == null ||
						!fUserDefinedConversion.equals(other.fUserDefinedConversion))
					return 0;
			}			
			cmp= fSecondStandardConversionRank.compareTo(other.fSecondStandardConversionRank);
			if (cmp != 0)
				return cmp;
		}
		
		if (!ignoreInheritanceDist) {
			cmp= fInheritanceDistance - other.fInheritanceDistance;
			if (cmp != 0)
				return cmp;
		}

		if (fReferenceBinding == ReferenceBinding.LVALUE_REF) {
			if (other.fReferenceBinding == ReferenceBinding.RVALUE_REF_BINDS_RVALUE)
				return 1;
		} else if (fReferenceBinding == ReferenceBinding.RVALUE_REF_BINDS_RVALUE) {
			if (other.fReferenceBinding == ReferenceBinding.LVALUE_REF)
				return -1;
		}

		// Top level cv-qualifiers are compared only for reference bindings.
		int qdiff= fQualificationAdjustments ^ other.fQualificationAdjustments;
		if (fReferenceBinding == ReferenceBinding.NO_REF || other.fReferenceBinding == ReferenceBinding.NO_REF)
			qdiff &= ~7;
		
		if (qdiff != 0) {
			if ((fQualificationAdjustments & qdiff) == 0)
				return -1;
			if ((other.fQualificationAdjustments & qdiff) == 0)
				return 1;
		}		
		return 0;
	}

	@SuppressWarnings("nls")
	@Override
	public String toString() {
		StringBuilder buf= new StringBuilder();
		String comma= "";
		buf.append(fRank).append('[');
		if (fQualificationAdjustments != 0) {
			buf.append(comma).append("qualification=").append(fQualificationAdjustments);
			comma= ", ";
		}
		if (fInheritanceDistance != 0) {
			buf.append(comma).append("inheritance=").append(fInheritanceDistance);
			comma= ", ";
		}
		if (fDeferredUDC != DeferredUDC.NONE) {
			buf.append(comma).append(fDeferredUDC);
			comma= ", ";
		}
		if (fAmbiguousUDC) {
			buf.append(comma).append("ambiguous UDC");
			comma= ", ";
		}
		if (fSecondStandardConversionRank != null) {
			buf.append(comma).append("2ndConvRank=").append(fSecondStandardConversionRank);
		}
		buf.append(']');
		return buf.toString();
	}

	public boolean isNarrowingConversion() {
		if (fCouldNarrow) {
			if (source instanceof CPPBasicType && target instanceof ICPPBasicType) {
				ICPPBasicType basicTarget= (ICPPBasicType) target;
				final Kind targetKind = basicTarget.getKind();
				if (targetKind != Kind.eInt && targetKind != Kind.eFloat && targetKind != Kind.eDouble) {
					return true;
				}
				IASTExpression val = ((CPPBasicType) source).getCreatedFromExpression();
				if (val instanceof IASTLiteralExpression) {
					// mstodo extend to constant expressions
					Long l= Value.create(val, Value.MAX_RECURSION_DEPTH).numericalValue();
					if (l != null) {
						long n= l.longValue();
						return !ArithmeticConversion.fitsIntoType(basicTarget, n);
					}
				}
			}
			return true;
		}
		return false;
	}

	public void setCouldNarrow() {
		fCouldNarrow= true;
	}

	public ICPPFunction getUserDefinedConversion() {
		return fUserDefinedConversion;
	}

	/**
	 * Stores a selected function. Used when resolving targeted functions. 
	 */
	public void setSelectedFunction(ICPPFunction function) {
		fSelectedFunction= function;
	}
	
	public ICPPFunction getSelectedFunction() {
		return fSelectedFunction;
	}

	public void setImpliedObject() {
		fImpliedObject= true;
	}
}