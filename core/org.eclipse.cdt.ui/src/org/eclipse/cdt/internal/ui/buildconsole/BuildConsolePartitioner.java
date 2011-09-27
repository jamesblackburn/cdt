/*******************************************************************************
 * Copyright (c) 2002, 2011 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     QNX Software Systems - initial API and implementation
 *     Dmitry Kozlov (CodeSourcery) - Build error highlighting and navigation
 *     Andrew Gvozdev (Quoin Inc.)  - Copy build log (bug 306222)
 *     Alex Collins (Broadcom Corp.) - Global console
 *     James Blackburn (Broadcom Corp.)
 *******************************************************************************/
package org.eclipse.cdt.internal.ui.buildconsole;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.IDocumentPartitionerExtension;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.console.ConsolePlugin;

import org.eclipse.cdt.core.ConsoleOutputStream;
import org.eclipse.cdt.core.ProblemMarkerInfo;
import org.eclipse.cdt.core.resources.IConsole;
import org.eclipse.cdt.ui.CUIPlugin;

import org.eclipse.cdt.internal.ui.preferences.BuildConsolePreferencePage;

public class BuildConsolePartitioner
		implements
			IDocumentPartitioner,
			IDocumentPartitionerExtension,
			IConsole,
			IPropertyChangeListener {

	private final IProject fProject;

	/**
	 * List of partitions
	 */
	List<BuildConsolePartition> fPartitions = new ArrayList<BuildConsolePartition>(5);

	private int fMaxLines;

	/**
	 * The stream that was last appended to
	 */
	BuildConsoleStreamDecorator fLastStream = null;

	BuildConsoleDocument fDocument;
	DocumentMarkerManager fDocumentMarkerManager;
	boolean killed;
	BuildConsoleManager fManager;

	/*
	 * Keep tabs on the last entry so we can defer UI updates until needed
	 */
	StringBuilder lastLogEntry = new StringBuilder();
	ProblemMarkerInfo lastMarker;
	BuildConsoleStreamDecorator lastStream;

	/**
	 * A queue of stream entries written to standard out and standard err.
	 * Entries appended to the end of the queue and removed from the front.
	 */
	ConcurrentLinkedQueue<StreamEntry> fQueue = new ConcurrentLinkedQueue<StreamEntry>();

	private URI fLogURI;
	private OutputStream fLogStream;

	private static class StreamEntry {
		/** Identifier of the stream written to. */
		private final BuildConsoleStreamDecorator fStream;
		/** The text written */
		private final String fText;
		/** Problem marker corresponding to the line of text */
		private final ProblemMarkerInfo fMarker;

		public StreamEntry(String text, BuildConsoleStreamDecorator stream, ProblemMarkerInfo marker) {
			fText = text;
			fStream = stream;
			fMarker = marker;
		}

		/**
		 * Returns the stream identifier
		 */
		public BuildConsoleStreamDecorator getStream() {
			return fStream;
		}

		/**
		 * Returns the text written
		 */
		public String getText() {
			return fText;
		}

		/**
		 * Returns error marker
		 */
		public ProblemMarkerInfo getMarker() {
			return fMarker;
		}
	}

	/**
	 * Construct a partitioner that is not associated with a specific project
	 */
	public BuildConsolePartitioner(BuildConsoleManager manager) {
		this(null, manager);
	}

	public BuildConsolePartitioner(IProject project, BuildConsoleManager manager) {
		fProject = project;
		fManager = manager;
		fMaxLines = BuildConsolePreferencePage.buildConsoleLines();
		fDocument = new BuildConsoleDocument();
		fDocument.setDocumentPartitioner(this);
		fDocumentMarkerManager = new DocumentMarkerManager(fDocument, this);
		connect(fDocument);

		fLogURI = null;
		fLogStream = null;
	}

	/**
	 * Sets the indicator that stream was opened so logging can be started. Should be called
	 * when opening the output stream.
	 */
	public void setStreamOpened() {
		logOpen(false);
	}

	/**
	 * Open the stream for appending. Must be called after a call to setStreamOpened().
	 * Can be used to reopen a stream for writing after it has been closed, without
	 * emptying the log file.
	 */
	public void setStreamAppend() {
		logOpen(true);
	}

	/**
	 * Sets the indicator that stream was closed so logging should be stopped. Should be called when
	 * build process has finished. Note that there could still be unprocessed console
	 * stream entries in the queue being worked on in the background.
	 */
	public void setStreamClosed() {
		if (lastLogEntry.length() > 0) {
			fQueue.add(new StreamEntry(lastLogEntry.toString(), lastStream, lastMarker));
			lastLogEntry.setLength(0);
			lastMarker = null;
			lastStream = null;
			queueProcessor.schedule();
		}

		logClose();
	}

	/**
	 * Open the log
	 * @param append Set to true if the log should be opened for appending, false for overwriting.
	 */
	private void logOpen(boolean append) {
		fLogURI = fManager.getLogURI(fProject);
		if (fLogURI != null) {
			try {
				IFileStore logStore = EFS.getStore(fLogURI);
				// Ensure the directory exists before opening the file
				IFileStore dir = logStore.getParent();
				if (dir != null)
					dir.mkdir(EFS.NONE, null);
				int opts = append ? EFS.APPEND : EFS.NONE;
				fLogStream = new BufferedOutputStream(logStore.openOutputStream(opts, null));
			} catch (CoreException e) {
				CUIPlugin.log(e);
			}
		}
	}

	private void log(String text) {
		if (fLogStream != null) {
			try {
				fLogStream.write(text.getBytes());
			} catch (IOException e) {
				CUIPlugin.log(e);
			}
		}
	}

	private void logClose() {
		if (fLogStream != null) {
			try {
				fLogStream.close();
			} catch (IOException e) {
				CUIPlugin.log(e);
			}
			fLogStream = null;
		}
//		if (fLogURI != null)
//			ResourcesUtil.refreshWorkspaceFiles(fLogURI);
	}

	/**
	 * Method which actually posts the event to the queue for UI processing.
	 * All queue updates should be funnelled through here.
	 * @param text to append
	 * @param stream 
	 * @param marker
	 */
	public void appendToDocument(String text, BuildConsoleStreamDecorator stream, ProblemMarkerInfo marker) {
		// Buffer up to 1k of content
		final int BUFFER_SIZE = 1 * 1024;

		boolean addToQueue = true;

		// Try to append to the previous entry
		if (lastStream == stream && lastMarker == marker && lastLogEntry.length() < BUFFER_SIZE) {
			lastLogEntry.append(text);
			addToQueue = false;
		}

		// Push items to the queue for update.
		if (addToQueue) {
			// Add the currently cached item
			if (lastLogEntry.length() > 0) {
				fQueue.add(new StreamEntry(lastLogEntry.toString(), lastStream, lastMarker));
				lastLogEntry.setLength(0);
			}

			// While the queue is larger than the number of lines that will fit in the console, remove
			// from the front.  This bounds the UI console queue on the size of the console.  Any more than
			// this will cause unnecessary work.  (NB: This is an underestimate, as the some queue entries 
			// will span multiple lines).
			while (fQueue.size() > fMaxLines)
				fQueue.poll();

			// Cache the current item
			lastLogEntry.append(text);
			lastMarker = marker;
			lastStream = stream;
		}

		if (!fQueue.isEmpty())
			queueProcessor.schedule();

		// Write the text out to the log file
		log(text);
	}

	static volatile int count;
	/** The current async runnable updating the console */
	private class QueueProcessor implements Runnable {

		/** flag which indicates whether the QueueProcessor is scheduled to run */
		volatile boolean scheduled;

		/**
		 * Schedule the Queue processing job
		 */
		public void schedule() {
			if (scheduled)
				return;
			scheduled = true;
			Display display = CUIPlugin.getStandardDisplay();
			if (display != null) {
				display.asyncExec(this);
			}
		}

		/**
		 * Process entries from fQueue until either the queue has been emptied
		 * or 50ms has elapsed.  Re-schedules itself if the queue isn't empty.
		 */
		public void run() {
			/** Run for at most 50ms, or until the queue is empty */
			final long start = System.currentTimeMillis();
			StringBuilder sb = new StringBuilder();
			int i = 0;
			try {
				while (true) {
					i++;
					// Keep the UI live: Don't do more than 50ms of work at a time here.
					// Updating the document may take significantly more time.
					if (i % 10 == 0 && System.currentTimeMillis() - start > 50) {
//						System.out.println("Processed: " + i + " items in: " + (System.currentTimeMillis() - start) + " " + fQueue.size() + " remaining.");
						break;
					}

					StreamEntry entry;
					try {
						entry = fQueue.remove();
					} catch (NoSuchElementException e) {
//						System.out.println("Processed: " + i + " items in: " + (System.currentTimeMillis() - start) + " " + fQueue.size() + " remaining.");
						break;
					}

					if (fLastStream != entry.getStream()) {
						fLastStream = entry.getStream();
						warnOfContentChange(fLastStream);
					}

					if (fLastStream == null) {
						// special case to empty document
						fPartitions.clear();
						fDocumentMarkerManager.clear();
						fDocument.set(""); //$NON-NLS-1$
						sb.setLength(0);
					}
					String text = entry.getText();
					if (text.length() > 0)
						addStreamEntryToDocument(sb, entry);
				}

				// fDocument.replace is expensive, so append once per UIJob run
				// This improves throughput by many, many X
				fDocument.replace(fDocument.getLength(), 0, sb.toString());
			} catch (BadLocationException e) {
			} finally {
				// Perform cleanup
				scheduled = false;
				warnOfContentChange(fLastStream);
				checkOverflow();
				if (!fQueue.isEmpty())
					schedule();
//				System.out.println("Leaving QueueProcessor: " + (System.currentTimeMillis() - start) + "ms");
			}
		}
	}
	QueueProcessor queueProcessor = new QueueProcessor();

	private void addStreamEntryToDocument(StringBuilder sb, StreamEntry entry) throws BadLocationException {
		ProblemMarkerInfo marker = entry.getMarker();
		if (marker==null) {
			// It is plain unmarkered console output
			addPartition(new BuildConsolePartition(fLastStream,
					fDocument.getLength() + sb.length(),
					entry.getText().length()));
		} else {
			// this text line in entry is markered with ProblemMarkerInfo,
			// create special partition for it.
			String errorPartitionType;
			if (marker.severity==IMarker.SEVERITY_INFO) {
				errorPartitionType = BuildConsolePartition.INFO_PARTITION_TYPE;
			} else if (marker.severity==IMarker.SEVERITY_WARNING) {
				errorPartitionType = BuildConsolePartition.WARNING_PARTITION_TYPE;
			} else {
				errorPartitionType = BuildConsolePartition.ERROR_PARTITION_TYPE;
			}
			addPartition(new BuildConsolePartition(fLastStream,
					fDocument.getLength() + sb.length(),
					entry.getText().length(),
					errorPartitionType, marker));
		}
		sb.append(entry.getText());
	}

	void warnOfContentChange(BuildConsoleStreamDecorator stream) {
		if (stream != null) {
			ConsolePlugin.getDefault().getConsoleManager().warnOfContentChange(stream.getConsole());
		}
		fManager.showConsole();
	}

	public IDocument getDocument() {
		return fDocument;
	}

	public void setDocumentSize(int nLines) {
		fMaxLines = nLines;
		nLines = fDocument.getNumberOfLines();
		checkOverflow();
	}

	public void connect(IDocument document) {
		CUIPlugin.getDefault().getPreferenceStore().addPropertyChangeListener(this);
	}

	public void disconnect() {
		fDocument.setDocumentPartitioner(null);
		CUIPlugin.getDefault().getPreferenceStore().removePropertyChangeListener(this);
		killed = true;
	}

	public void documentAboutToBeChanged(DocumentEvent event) {
	}

	public boolean documentChanged(DocumentEvent event) {
		return documentChanged2(event) != null;
	}

	/**
	 * @see org.eclipse.jface.text.IDocumentPartitioner#getLegalContentTypes()
	 */
	public String[] getLegalContentTypes() {
		return new String[]{BuildConsolePartition.CONSOLE_PARTITION_TYPE};
	}

	/**
	 * @see org.eclipse.jface.text.IDocumentPartitioner#getContentType(int)
	 */
	public String getContentType(int offset) {
		ITypedRegion partition = getPartition(offset);
		if (partition != null) {
			return partition.getType();
		}
		return null;
	}

	/**
	 * @see org.eclipse.jface.text.IDocumentPartitioner#computePartitioning(int,
	 *      int)
	 */
	public ITypedRegion[] computePartitioning(int offset, int length) {
		if (offset == 0 && length == fDocument.getLength()) {
			return fPartitions.toArray(new ITypedRegion[fPartitions.size()]);
		}
		int end = offset + length;
		List<ITypedRegion> list = new ArrayList<ITypedRegion>(2);
		// As partitions are added to the end of the document, iterate backwards through the 
		// partition collection otherwise this becomes O(n^2) rather than linear.
		int i = fPartitions.size();
		while (i-- > 0) {
			ITypedRegion partition = fPartitions.get(i);
			int partitionStart = partition.getOffset();
			int partitionEnd = partitionStart + partition.getLength();
			if ( (offset >= partitionStart && offset < partitionEnd) ||
					(offset < partitionStart && end >= partitionStart)) {
				list.add(partition);
			}
			// Partitions are in order, so if the end doesn't overlap, bail-out
			if (offset + length > partitionEnd)
				break;
		}
		return list.toArray(new ITypedRegion[list.size()]);
	}

	/**
	 * @see org.eclipse.jface.text.IDocumentPartitioner#getPartition(int)
	 */
	public ITypedRegion getPartition(int offset) {
		int i = fPartitions.size();
		while (i-- > 0) {
			ITypedRegion partition = fPartitions.get(i);
			int start = partition.getOffset();
			int end = start + partition.getLength();
			if (offset >= start && offset < end) {
				return partition;
			}
		}
		return null;
	}

	public IRegion documentChanged2(DocumentEvent event) {
		String text = event.getText();
		if (getDocument().getLength() == 0) {
			// cleared
			fPartitions.clear();
			fDocumentMarkerManager.clear();
			return new Region(0, 0);
		}
		ITypedRegion[] affectedRegions = computePartitioning(event.getOffset(), text.length());
		if (affectedRegions.length == 0) {
			return null;
		}
		if (affectedRegions.length == 1) {
			return affectedRegions[0];
		}
		int affectedLength = affectedRegions[0].getLength();
		for (int i = 1; i < affectedRegions.length; i++) {
			ITypedRegion region = affectedRegions[i];
			affectedLength += region.getLength();
		}

		return new Region(affectedRegions[0].getOffset(), affectedLength);
	}

	/**
	 * Checks to see if the console buffer has overflowed, and empties the
	 * overflow if needed, updating partitions and hyperlink positions.
	 */
	protected void checkOverflow() {
		if (fMaxLines >= 0) {
			int nLines = fDocument.getNumberOfLines();
			if (nLines > fMaxLines + 1) {
				try {
					// We've overflowed, as partitions are being removed, clear the manager
					// which keeps track of which line is currently selected...
					fDocumentMarkerManager.clear();
					int overflow = fDocument.getLineOffset(nLines - fMaxLines);
					// update partitions
					List<BuildConsolePartition> newParitions = new ArrayList<BuildConsolePartition>(fPartitions.size());
					for (BuildConsolePartition messageConsolePartition : fPartitions) {
						int offset = messageConsolePartition.getOffset();
						String type = messageConsolePartition.getType();
						if (offset < overflow) {
							int endOffset = offset + messageConsolePartition.getLength();
							if (endOffset < overflow || BuildConsolePartition.isProblemPartitionType(type)) {
								// remove partition - partitions with problem markers can't be split - remove them too
								continue;
							} else {
								// split partition
								messageConsolePartition.fOffset = 0;
								messageConsolePartition.fLength = endOffset - overflow;
							}
						} else {
							// modify partition offset
							messageConsolePartition.fOffset -= overflow;
						}
						newParitions.add(messageConsolePartition);
					}
					fPartitions = newParitions;
					fDocument.replace(0, overflow, ""); //$NON-NLS-1$
				} catch (BadLocationException e) {
				}
			}
		}
	}

	/**
	 * Adds a new partition, combining with the previous partition if possible.
	 */
	private void addPartition(BuildConsolePartition partition) {
		if (fPartitions.isEmpty()) {
			fPartitions.add(partition);
		} else {
			BuildConsolePartition last = fPartitions.get(fPartitions.size() - 1);
			if (last.canBeCombinedWith(partition)) {
				// replace with a single partition
				assert last.fOffset < partition.fOffset;
				assert last.fOffset + last.fLength == partition.fOffset;
				last.fLength += partition.fLength;
			} else {
				// different kinds - add a new parition
				fPartitions.add(partition);
			}
		}
	}

	public IConsole getConsole() {
		return this;
	}

	public void propertyChange(PropertyChangeEvent event) {
		if (event.getProperty() == BuildConsolePreferencePage.PREF_BUILDCONSOLE_LINES) {
			setDocumentSize(BuildConsolePreferencePage.buildConsoleLines());
		}
	}

	public void start(final IProject project) {
		Display display = CUIPlugin.getStandardDisplay();
		if (display != null) {
			display.asyncExec(new Runnable() {
				public void run() {
					fLogStream = null;
					fLogURI = null;
					fManager.startConsoleActivity(project);
				}
			});
		}


		if (BuildConsolePreferencePage.isClearBuildConsole()) {
			appendToDocument("", null, null); //$NON-NLS-1$
		}
	}

	public ConsoleOutputStream getOutputStream() throws CoreException {
		return new BuildOutputStream(this, fManager.getStreamDecorator(BuildConsoleManager.BUILD_STREAM_TYPE_OUTPUT));
	}

	public ConsoleOutputStream getInfoStream() throws CoreException {
		return new BuildOutputStream(this, fManager.getStreamDecorator(BuildConsoleManager.BUILD_STREAM_TYPE_INFO));
	}

	public ConsoleOutputStream getErrorStream() throws CoreException {
		return new BuildOutputStream(this, fManager.getStreamDecorator(BuildConsoleManager.BUILD_STREAM_TYPE_ERROR));
	}

	/** This method is useful for future debugging and bug-fixing */
	@SuppressWarnings({ "unused", "nls" })
	private void printDocumentPartitioning() {
		System.out.println("Document partitioning: ");
		for (ITypedRegion tr : fPartitions) {
			BuildConsolePartition p = (BuildConsolePartition) tr;
			int start = p.getOffset();
			int end = p.getOffset() + p.getLength();
			String text;
			String isError = "U";
			String type = p.getType();
			if (type == BuildConsolePartition.ERROR_PARTITION_TYPE) {
				isError = "E";
			} else if (type == BuildConsolePartition.WARNING_PARTITION_TYPE) {
				isError = "W";
			} else if (type == BuildConsolePartition.INFO_PARTITION_TYPE) {
				isError = "I";
			} else if (type == BuildConsolePartition.CONSOLE_PARTITION_TYPE) {
				isError = "C";
			}
			try {
				text = fDocument.get(p.getOffset(), p.getLength());
			} catch (BadLocationException e) {
				text = "N/A";
			}
			if (text.endsWith("\n")) {
				text = text.substring(0, text.length() - 1);
			}
			System.out.println("    " + isError + " " + start + "-" + end + ":[" + text + "]");
		}
	}

	/**
	 * @return {@link URI} location of log file.
	 */
	public URI getLogURI() {
		return fLogURI;
	}

	IProject getProject() {
		return fProject;
	}
}
