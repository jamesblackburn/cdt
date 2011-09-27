/*******************************************************************************
 * Copyright (c) 2002, 2011 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * QNX Software Systems - Initial API and implementation
 * Dmitry Kozlov (CodeSourcery) - Build error highlighting and navigation
 * James Blackburn (Broadcom Corp.)
 *******************************************************************************/
package org.eclipse.cdt.internal.ui.buildconsole;

import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.TypedRegion;

import org.eclipse.cdt.core.ProblemMarkerInfo;
import org.eclipse.cdt.ui.CUIPlugin;

/**
 * Represents a text region implements ITypedRegion in the BuildConsole.
 * <p>
 * This internal class is used instead of extending {@link TypedRegion} so
 * we don't churn too many objects when overflowing the console...
 * </p>
 */
public class BuildConsolePartition implements ITypedRegion, Comparable<BuildConsolePartition> {

	/** offset of the partition in the document */
	int fOffset;

	/** length of the partition */
	int fLength;

	/** The region's type */
	String fType;

	/** Associated stream */
	private BuildConsoleStreamDecorator fStream;

	/** Marker associated with this partition if any */
	private ProblemMarkerInfo fMarker;

	/** Partition type */
	public static final String CONSOLE_PARTITION_TYPE = CUIPlugin.getPluginId() + ".CONSOLE_PARTITION_TYPE"; //$NON-NLS-1$

	/** Partition types to report build problems in the console */
	public static final String ERROR_PARTITION_TYPE = CUIPlugin.getPluginId() + ".ERROR_PARTITION_TYPE"; //$NON-NLS-1$
	public static final String INFO_PARTITION_TYPE = CUIPlugin.getPluginId() + ".INFO_PARTITION_TYPE"; //$NON-NLS-1$
	public static final String WARNING_PARTITION_TYPE = CUIPlugin.getPluginId() + ".WARNING_PARTITION_TYPE"; //$NON-NLS-1$

	public BuildConsolePartition(BuildConsoleStreamDecorator stream, int offset, int length) {
		this (stream, offset, length, CONSOLE_PARTITION_TYPE, null);
	}

	public BuildConsolePartition(BuildConsoleStreamDecorator stream, int offset, int length, String type, ProblemMarkerInfo marker) {
		fOffset = offset;
		fLength = length;
		fType = type;
		fStream = stream;
		fMarker = marker;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.text.IRegion#getLength()
	 */
	public int getLength() {
		return fLength;
	}

	/**
	 * @return This partition's marker info, or null
	 */
	public ProblemMarkerInfo getMarker() {
		return fMarker;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.text.IRegion#getOffset()
	 */
	public int getOffset() {
		return fOffset;
	}

	/**
	 * @return this partition's stream
	 */
	public BuildConsoleStreamDecorator getStream() {
		return fStream;
	}

	/*
	 * @see org.eclipse.jface.text.ITypedRegion#getType()
	 */
	public String getType() {
		return fType;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof BuildConsolePartition) {
			BuildConsolePartition r= (BuildConsolePartition) o;
			return r.fOffset == fOffset && r.fLength == r.fLength && fStream.equals(r.fStream) && ((fType == null && r.getType() == null) || fType.equals(r.getType()));
		}
		return false;
	}

	@Override
	public int hashCode() {
	 	int type= fType == null ? 0 : fType.hashCode();
	 	return (fOffset << 24) | (fLength << 16) | type | fStream.hashCode();
	}

	@Override
	public String toString() {
		return fType + " - " + "offset: " + fOffset + ", length: " + fLength; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	/**
	 * Returns whether this partition is allowed to be combined with the given
	 * partition.
	 *
	 * @param partition
	 * @return boolean
	 */
	public boolean canBeCombinedWith(BuildConsolePartition partition) {
		// Error partitions never can be combined together
		String type = getType();
		if (isProblemPartitionType(type) || isProblemPartitionType(partition.getType()))
			return false;

		return fStream == partition.fStream;
	}

	public static boolean isProblemPartitionType(String type) {
		return type==BuildConsolePartition.ERROR_PARTITION_TYPE
			|| type==BuildConsolePartition.WARNING_PARTITION_TYPE
			|| type==BuildConsolePartition.INFO_PARTITION_TYPE;
	}

	public int compareTo(BuildConsolePartition o) {
		return fOffset - o.fOffset;
	}

}
