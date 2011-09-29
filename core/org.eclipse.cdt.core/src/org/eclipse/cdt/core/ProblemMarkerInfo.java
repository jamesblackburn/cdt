/*******************************************************************************
 * Copyright (c) 2006, 2009 Siemens AG.
 * All rights reserved. This content and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Norbert Ploett - Initial implementation
 *   James Blackburn
 *******************************************************************************/

package org.eclipse.cdt.core;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;

/**
 * @noextend This class is not intended to be subclassed by clients.
 */
public class ProblemMarkerInfo {

		/**
		 * unset result code
		 * @since 5.2
		 */
		public static final int RESULT_CODE_UNSET = -1;

		/**
		 * IResource on which the new marker will be set
		 */
		public IResource file;

		/** 
		 * Project being built when marker generated
		 * @since 5.2 
		 */
		public final IProject buildProject;
		/**
		 * The file being built when the Marker was generated.
		 * For example {@link #file} may be foo.h, but {@link #buildFile} may be foo.c
		 * @since 5.2
		 */
		public final IResource buildFile;

		/** 
		 * Configuration name associated with this Problem Marker, or null
		 * @since 5.2 
		 */
		public final String configName;

		public int lineNumber;
		public String description;
		/** Problem marker severity code */
		public int severity;
		/** 
		 * Exit code returned by the process involved in the build.
		 * Equal to RESULT_CODE_UNSET if unknown.
		 * @since 5.2
		 */
		public final int resultCode;

		public String variableName;
		/** Location of the marker, if the resource can't be accessed under the workspace */
		public IPath externalPath ;

		/**
		 * Clear Problem markers associated with this resource
		 * @since 5.2
		 */
		public final boolean clearProblemMarkers;

		public ProblemMarkerInfo(IResource file, int lineNumber, String description, int severity, String variableName) {
			this (file, lineNumber, description, severity, variableName, null);
		}

		public ProblemMarkerInfo(IResource file, int lineNumber, String description, int severity, String variableName, IPath externalPath) {
			this (file, null, null, null, lineNumber, description, severity, variableName, externalPath, RESULT_CODE_UNSET);
		}
		
		/**
		 * Error marker to be added to a resource when the underlying process exits with a non-zero error status. 
		 * @param resource
		 * @param project
		 * @param configName
		 * @param description
		 * @param severity
		 * @param resultCode
		 * @since 5.2
		 */
		public ProblemMarkerInfo(IResource resource, IProject project, String configName, String description, int severity, int resultCode) {
			this(resource, project, null, configName, 0, description, severity, null, null, resultCode);
		}

		/**
		 * Constructor for ProblemMarkerInfo
		 * @param file IResource file
		 * @param buildProject under build (may be different from file#getProject()) when marker was generated. May be null.
		 * @param buildFile the file that was being built when the marker was generated. May be different from file.
		 *                  e.g. header file with build error while building the C file which #included it
		 * @param configName Configuration ID in IResource's project
		 * @param lineNumber
		 * @param description
		 * @param severity
		 * @param variableName
		 * @param externalPath
		 * @since 5.2
		 */
		public ProblemMarkerInfo(IResource file, IProject buildProject, IResource buildFile, String configName, 
				int lineNumber, String description, int severity, String variableName, IPath externalPath,
				int resultCode) {
			this.file = file;
			this.buildProject = buildProject;
			this.buildFile = buildFile;
			this.configName = configName;
			this.lineNumber = lineNumber;
			this.description = description;
			this.severity = severity;
			this.variableName = variableName;
			this.externalPath = externalPath;
			this.clearProblemMarkers = false;
			this.resultCode = resultCode;
		}

		/**
		 * Clear markers associated with building the file
		 * @param file resource being built
		 * @param buildProject project (very likely file.getProject())
		 * @param externalPath externalPath of the file if not under the project
		 * @since 5.2
		 */
		public ProblemMarkerInfo(IResource file, IProject buildProject, String configName, IPath externalPath) {
			this.file = file;
			this.buildProject = buildProject;
			this.buildFile = file;
			this.configName = configName;
			this.externalPath = externalPath;
			this.clearProblemMarkers = true;
			this.resultCode = RESULT_CODE_UNSET;
		}

}
