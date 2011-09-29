/*******************************************************************************
 * Copyright (c) 2006, 2010 Nokia and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nokia - initial API and implementation
 *     Broadcom - add macro fetching
 *******************************************************************************/
package org.eclipse.cdt.core;

import java.util.LinkedHashSet;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;

/**
 * A reader that's able to decipher debug symbol formats.
 * 
 * This initial version only returns a list of source files.
 * 
 * @noextend This interface is not intended to be extended by clients.
 * @noimplement This interface is not intended to be implemented by clients.
 */
public interface ISymbolReader {

	/**
	 * Class representing a Dwarf Preprocessor Macro.
	 *
	 * Macros may be defined, undefined or vendor specific
	 *
	 * Defined Macros have a name and value
	 * Undefined Macros have a name
	 * The format of vendor specifc macros is implementation defined.
	 * @since 5.3
	 */
	public class Macro {
		public enum TYPE {
			/** Macro Defined */
			DEFINED,
			/** Undefined Macro, macro = the macro's name */
			UNDEFINED,
			/** Vendor specific String */
			VENDOR
		};
		public final TYPE type;
		public final String macro;
		public Macro(TYPE type, String macro) {
			this.type = type;
			if (macro != null)
				this.macro = macro.intern();
			else
				this.macro = "";
		}
		/**
		 * @return a String representing the defined macro
		 */
		public String getName() {
			if (macro.indexOf(' ') != -1)
				return macro.substring(0, macro.indexOf(' ')).intern();
			return macro;
		}
		/**
		 * @return a String representing the Macro value
		 */
		public String getValue() {
			if (macro.indexOf(' ') != -1)
				return macro.substring(macro.indexOf(' ') + 1).intern();
			return "";
		}
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Macro))
				return false;
			Macro other = ((Macro)obj);
			return type == other.type && macro.equals(other.macro);
		}
		@Override
		public int hashCode() {
			return type.hashCode() + macro.hashCode();
		}
		@Override
		public String toString() {
			return this.type + " " + macro;
		}
	};
	
	/**
	 * Include type may be -I {include_dir}
	 * or -include {file}
	 * @since 5.3
	 */
	public class Include {
		public enum TYPE {
			/** Directory style include */
			DIRECTORY,
			/** File style include */
			FILE,
		};
		public final TYPE type;
		public final IPath path;
		public Include(TYPE type, String path) {
			Assert.isNotNull(path);
			this.type = type;
			this.path = new Path(path);
		}
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Include))
				return false;
			Include other = (Include)obj;
			return other.type.equals(this.type) && other.path.equals(this.path);
		}
		@Override
		public int hashCode() {
			return type.hashCode() + path.hashCode();
		}
		@Override
		public String toString() {
			return path.toOSString();
		}
	}

	/**
	 * An array of Source File paths built into this object
	 * @return String[] source file paths; may return null
	 */
	String[] getSourceFiles();

	/**
	 * @return a map of Source path to ordered set of includes
	 * @since 5.3
	 */
	Map<String, LinkedHashSet<Include>> getIncludesPerSourceFile();

	/**
	 * Returns a Map of sourceFiles paths to Ordered Macro Collection.
	 *
	 * These Macros are those externally (not defined in the source
	 * text) defined / undefined on the specified compilation unit
	 *
	 * If an external macro is both defined & undefined, we return the
	 * last declaration of the macro
	 *
	 * @return Map<String, LinkedHashSet<Macro>> - Map compilation unit path to ordered set of macros
	 * @since 5.3
	 */
	Map<String, LinkedHashSet<Macro>> getExternallyDefinedMacros();
        
        /**
	 * Gets the source files from this symbol reader.
	 *
	 * @param monitor a progress monitor since this may be a lengthly operation
	 * @return an array of path names to the source files
	 * @since 5.2
	 */
	String[] getSourceFiles(IProgressMonitor monitor);
}
