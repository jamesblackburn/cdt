/*******************************************************************************
 * Copyright (c) 2007, 2010 Nokia and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nokia - initial API and implementation
 *     Ling Wang (Nokia) bug 201000
 *******************************************************************************/

package org.eclipse.cdt.utils.debug.dwarf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.ISymbolReader;
import org.eclipse.cdt.utils.coff.Coff.SectionHeader;
import org.eclipse.cdt.utils.coff.PE;
import org.eclipse.cdt.utils.debug.IDebugEntryRequestor;
import org.eclipse.cdt.utils.elf.Elf;
import org.eclipse.cdt.utils.elf.Elf.Section;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;

/**
 * Light-weight parser of Dwarf2 data which is intended for:
 *  - Getting source files that contribute to the given executable.
 *  - Source file -> set of includes mapping
 *  - Macros (un)defined on the given source files
 */
public class DwarfReader extends Dwarf implements ISymbolReader {

	// These are sections that need be parsed to get the source file list.
	final static String[] DWARF_SectionsToParse =
	{
		DWARF_DEBUG_INFO,
		DWARF_DEBUG_LINE,
		DWARF_DEBUG_ABBREV,
		DWARF_DEBUG_STR,	// this is optional. Some compilers don't generate it.
		DWARF_DEBUG_MACINFO, // For fetching Macros for the project model
	};

	/** Declared INVALID_OFFSET for macro info field */
	private final int INVALID_OFFSET = -1;

	/** A map of Source filename to Integer Offset of the MacroInfo desired */
	private Map<String,  Integer> m_fileCollection = new LinkedHashMap<String, Integer>();
	/** A map of Source filename to Set of include directories */
	private Map<String,  LinkedHashSet<Include>> m_sourceIncludeMap = new HashMap<String, LinkedHashSet<Include>>();

	private String[] 	m_fileNames = null;
	private boolean		m_parsed = false;

	private Set<Integer>	m_parsedLineTableOffsets = new HashSet<Integer>();
	private int			m_parsedLineTableSize = 0;

	public DwarfReader(String file) throws IOException {
		super(file);
	}

	public DwarfReader(Elf exe) throws IOException {
		super(exe);
	}

	/**
	 * @since 5.1
	 */
	public DwarfReader(PE exe) throws IOException  {
		super(exe);
	}

	// Override parent.
	//
	@Override
	public void init(Elf exe) throws IOException {
		Elf.ELFhdr header = exe.getELFhdr();
		isLE = header.e_ident[Elf.ELFhdr.EI_DATA] == Elf.ELFhdr.ELFDATA2LSB;

		Elf.Section[] sections = exe.getSections();

		// Read in sections (and only the sections) we care about.
		//
		for (Section section : sections) {
			String name = section.toString();
			for (String element : DWARF_SectionsToParse) {
				if (name.equals(element)) {
					// catch out of memory exceptions which might happen trying to
					// load large sections (like .debug_info).  not a fix for that
					// problem itself, but will at least continue to load the other
					// sections.
					try {
						dwarfSections.put(element, section.mapSectionData());
					} catch (Exception e) {
						CCorePlugin.log(e);
					}
				}
			}
		}
		
		// Don't print during parsing.
		printEnabled = false;
		m_parsed = false;
	}

	@Override
	public void init(PE exe) throws IOException {

		isLE = true;
		SectionHeader[] sections = exe.getSectionHeaders();

		for (int i = 0; i < sections.length; i++) {
			String name = new String(sections[i].s_name).trim();
			if (name.startsWith("/")) //$NON-NLS-1$
			{
				int stringTableOffset = Integer.parseInt(name.substring(1));
				name = exe.getStringTableEntry(stringTableOffset);
			}
			for (String element : Dwarf.DWARF_SCNNAMES) {
				if (name.equals(element)) {
					try {
						dwarfSections.put(element, sections[i].mapSectionData());
					} catch (Exception e) {
						CCorePlugin.log(e);
					}
				}
			}
		}
		// Don't print during parsing.
		printEnabled = false;
		m_parsed = false;
	}

	/*
	 * Parse line table data of a compilation unit to get names of all source files
	 * that contribute to the compilation unit.
	 */
	void parseSourceInCULineInfo(
			final String cuName,    // compilation unit name
			final String cuCompDir,	// compilation directory of the CU
			int cuStmtList) 	// offset of the CU line table in .debug_line section
	{
		ByteBuffer data = dwarfSections.get(DWARF_DEBUG_LINE);
		if (data != null) {
			try {
				data.position(cuStmtList);
				
				/* Read line table header:
				 *
				 *  total_length:				4 bytes (excluding itself)
				 *  version:					2
				 *  prologue length:			4
				 *  minimum_instruction_len:	1
				 *  default_is_stmt:			1
				 *  line_base:					1
				 *  line_range:					1
				 *  opcode_base:				1
				 *  standard_opcode_lengths:	(value of opcode_base)
				 */

				// Remember the CU line tables we've parsed.
				Integer cuOffset = new Integer(cuStmtList);
				if (! m_parsedLineTableOffsets.contains(cuOffset)) {
					m_parsedLineTableOffsets.add(cuOffset);

					int length = read_4_bytes(data) + 4;
					m_parsedLineTableSize += length + 4;
				}
				else {
					// Compiler like ARM RVCT may produce several CUs for the
					// same source files.
					return;
				}

				// Skip the following till "opcode_base"
				data.position(data.position() + 10);
				int opcode_base = data.get();
				data.position(data.position() + opcode_base - 1);

				// Read in directories.
				//
				ArrayList<String> dirList = new ArrayList<String>();

				// Put the compilation directory of the CU as the first dir
				dirList.add(cuCompDir);

				String str, fileName;

				while (true) {
					str = readString(data);
					if (str.length() == 0)
						break;
					// If the directory is relative, append it to the CU dir
					IPath dir = new Path(str);
					if(!dir.isAbsolute())
						dir = new Path(cuCompDir).append(str);
					dirList.add(dir.toString());
				}
				
				// Ensure that there is a CU entry in the m_sourceIncludeMap
				final String compUnit = new Path(cuName).isAbsolute() ? cuName : 
										new Path(cuCompDir).append(cuName).toOSString();
				if (!m_sourceIncludeMap.containsKey(compUnit))
					m_sourceIncludeMap.put(compUnit, new LinkedHashSet<Include>());

				// Read file names
				//
				long leb128;
				while (true) {
					fileName = readString(data);
					if (fileName.length() == 0)	// no more file entry
						break;
					
					// dir index
					leb128 = read_unsigned_leb128(data);
					
					// Add source file to the list of files for this CU
					String fileInclude = addSourceFile(dirList.get((int)leb128), fileName, INVALID_OFFSET);
					// Add include map, if this file was directly included
					if (fileInclude != null && !fileInclude.equals(compUnit))
						m_sourceIncludeMap.get(compUnit).add(new Include(Include.TYPE.FILE, fileInclude));
					
					// Skip the followings
					//
					// modification time
					leb128 = read_unsigned_leb128(data);

					// file size in bytes
					leb128 = read_unsigned_leb128(data);
				}

				// Add the directory includes to the include set
				//
				Set<Include> includeSet = m_sourceIncludeMap.get(compUnit);
				for (String include : dirList.subList(1, dirList.size()))
					includeSet.add(new Include(Include.TYPE.DIRECTORY, include));
			} catch (IOException e) {
				e.printStackTrace();
				CCorePlugin.log(e);
			}
		}
	}

	/*
	 * Check if there are any line tables in .debug_line section that are
	 * not referenced by any TAG_compile_units. If yes, add source files
	 * in those table entries to our "m_fileCollection".
	 * If the compiler/linker is fully dwarf standard compliant, that should
	 * not happen. But that case does exist, hence this workaround.
	 * .................. LWang. 08/24/07
	 */
	private void getSourceFilesFromDebugLineSection()
	{
		ByteBuffer data = dwarfSections.get(DWARF_DEBUG_LINE);
		if (data == null) 
			return;
		
		int sectionSize = data.capacity();
		int minHeaderSize = 16;

		// Check if there is data in .debug_line section that is not parsed
		// yet by parseSourceInCULineInfo().
		if (m_parsedLineTableSize >= sectionSize - minHeaderSize)
			return;

		// The .debug_line section contains a list of line tables
		// for compile_units. We'll iterate through all line tables
		// in the section.
		/*
		 * Line table header for one compile_unit:
		 *
		 * total_length: 			4 bytes (excluding itself)
		 * version: 				2
		 * prologue length: 		4
		 * minimum_instruction_len: 1
		 * default_is_stmt: 		1
		 * line_base: 				1
		 * line_range: 				1
		 * opcode_base: 			1
		 * standard_opcode_lengths: (value of opcode_base)
		 */

		int lineTableStart = 0;	// offset in the .debug_line section

		try {
			while (lineTableStart < sectionSize - minHeaderSize) {
				data.position(lineTableStart);

				Integer currLineTableStart = new Integer(lineTableStart);

				// Read length of the line table for one compile unit
				// Note the length does not including the "length" field itself.
				int tableLength = read_4_bytes(data);
				
				// Record start of next CU line table
				lineTableStart += tableLength + 4;

				// According to Dwarf standard, the "tableLength" should cover the
				// the whole CU line table. But some compilers (e.g. ARM RVCT 2.2)
				// produce extra padding (1 to 3 bytes) beyond that in order for
				// "lineTableStart" to be aligned at multiple of 4. The padding
				// bytes are beyond the "tableLength" and not indicated by
				// any flag, which I believe is not Dwarf2 standard compliant.
				// How to determine if that type of padding exists ?
				// I don't have a 100% safe way. But following hacking seems
				// good enough in practice.........08/26/07
				if (lineTableStart < sectionSize - minHeaderSize &&
						(lineTableStart & 0x3) != 0)
				{
					int savedPosition = data.position();
					data.position(lineTableStart);
					
					int ltLength = read_4_bytes(data);
					int dwarfVer = read_2_bytes(data);
					int minInstLengh = data.get(data.position() + 4);
					
					boolean dataValid = 
						ltLength > minHeaderSize && 
						ltLength < 16*64*1024 &&   // One source file has that much line data ? 
						dwarfVer > 0 &&	dwarfVer < 4 &&  // ver 3 is still draft at present.
						minInstLengh > 0 && minInstLengh <= 8;
						
					if (! dataValid)	// padding exists !
						lineTableStart = (lineTableStart+3) & ~0x3;
					
					data.position(savedPosition);
				}
				
				if (m_parsedLineTableOffsets.contains(currLineTableStart))
					// current line table has already been parsed, skip it.
					continue;

				// Skip following fields till "opcode_base"
				data.position(data.position() + 10);
				int opcode_base = data.get();
				data.position(data.position() + opcode_base - 1);

				// Read in directories.
				//
				ArrayList<String> dirList = new ArrayList<String>();

				String str, fileName;

				// first dir should be TAG_comp_dir from CU, which we don't have here.
				dirList.add(""); //$NON-NLS-1$
				
				while (true) {
					str = readString(data);
					if (str.length() == 0)
						break;
					dirList.add(str);
				}

				// Read file names
				//
				long leb128;
				while (true) {
					fileName = readString(data);
					if (fileName.length() == 0) // no more file entry
						break;

					// dir index. Note "0" is reserved for compilation directory. 
					leb128 = read_unsigned_leb128(data);

					addSourceFile(dirList.get((int) leb128), fileName, INVALID_OFFSET);

					// Skip the followings
					//
					// modification time
					leb128 = read_unsigned_leb128(data);

					// file size in bytes
					leb128 = read_unsigned_leb128(data);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
	}

	/**
	 * The method that actually does the work of loading the dwarf debug info
	 * into this class's collection instances
	 */
	private void parseDwarf() {
		if (!m_parsed) {
			m_fileCollection.clear();
			m_sourceIncludeMap.clear();

			getSourceFilesFromDebugInfoSection();

			getSourceFilesFromDebugLineSection();

			m_parsed = true;
		}
	}

	public String[] getSourceFiles() {
		parseDwarf();
		String[] fileNames = new String[m_fileCollection.size()];
		m_fileCollection.keySet().toArray(fileNames);
		return fileNames;
	}

	public Map<String, LinkedHashSet<Include>> getIncludesPerSourceFile() {
		parseDwarf();
		return m_sourceIncludeMap;
	}

	public Map<String, LinkedHashSet<Macro>> getExternallyDefinedMacros() {
		parseDwarf();
		Map<String, LinkedHashSet<Macro>> macMap= new HashMap<String, LinkedHashSet<Macro>>();
		// A unique set of allMacros to try to be kind on memory
		HashMap<Macro,Macro> allMacros = new HashMap<Macro,Macro>();

		for (Map.Entry<String, Integer> e : m_fileCollection.entrySet()) {
			// If Macro field exists, add the macros to the list
			if (e.getValue() != INVALID_OFFSET) {
				LinkedHashSet<Macro> macros = new LinkedHashSet<Macro>();
				for (MacroInfo mi : parseMacroInfo(e.getValue(), true)) {
					Macro macro = mi.toMacro();
					if (!allMacros.containsKey(macro))
						allMacros.put(macro, macro);

					// If Macro has already been defined / undefined on this
					// set, and we're now undefining / defining, remove the original entry
					Macro converse = null;
					if (macro.type == Macro.TYPE.DEFINED)
						converse = new Macro(Macro.TYPE.UNDEFINED, macro.macro);
					else if (macro.type == Macro.TYPE.UNDEFINED)
						converse = new Macro(Macro.TYPE.DEFINED, macro.macro);
					if (converse != null)
						macros.remove(converse);

					// Add the Macro
					macros.add(allMacros.get(macro));
				}
				macMap.put(e.getKey(), macros);
			}
		}
		// Add some debug output on how many files has macro info, compared with the number we've found...
		// TODO: Should actually check that we've found all the macrosets there are to find
		if (printEnabled) {
			List<MacroInfo> allMIs = parseMacroInfo(-1, true);
			System.out.println("Total # Macro Entries = " + allMIs.size());
			System.out.println("# macros resolved via source file pointers = " + macMap.size());
		}
		return macMap;
	}

	/*
	 * Get source file names from compile units (CU) in .debug_info section,
	 * which will also search line table for the CU in .debug_line section.
	 *
	 * The file names are stored in member "m_fileCollection".
	 */
	private void getSourceFilesFromDebugInfoSection() {
		// This will parse the data in .debug_info section which
		// will call this->processCompileUnit() to get source files.
		parse(null);
	}

	/**
	 * Resolve the file name either absolutely, or relative to the passed in
	 * directory.  Adding it to m_fileCollection.  If the CU name is an absolute
	 * FS path, then that is returned as this file was probably included with --include
	 *
	 * @param dir The directory this CU may be relative to
	 * @param name Compilation unit name, may be relative to dir, or absolute
	 * @param macroInfo integer offset of this CU's macro info in the macro info table
	 * @return String path if name is an absolute path not relative to dir; otherwise null
	 */
	private String addSourceFile(String dir, String name, int macroInfo)
	{
		if (name == null || name.length() == 0)
			return null;
		
		if (name.charAt(0) == '<')	//  don't count the entry "<internal>" from GCCE compiler
			return null;

		boolean relative = true;
		
		String fullName = name;
		
		IPath dirPa = new Path(dir);
		IPath pa = new Path(name);
		
		// Combine dir & name if needed.
		if (!pa.isAbsolute() && dir.length() > 0)
			pa = dirPa.append(pa);
		else
			relative = false;

		// This convert the path to canonical path (but not necessarily absolute, which
		// is different from java.io.File.getCanonicalPath()).
		fullName = pa.toOSString();
		
		if (!m_fileCollection.containsKey(fullName))
			m_fileCollection.put(fullName, macroInfo);

		if (!relative)
			return fullName;
		return null;
	}
	
	/**
	 * Read a null-ended string from the given "data" stream.
	 * data	:  IN, byte buffer
	 */
	String readString(ByteBuffer data)
	{
		String str;
		
		StringBuffer sb = new StringBuffer();
		while (data.hasRemaining()) {
			byte c = data.get();
			if (c == 0) {
				break;
			}
			sb.append((char) c);
		}

		str = sb.toString();
		return str;
	}

	// Override parent: only handle TAG_Compile_Unit.
	@Override
	void processDebugInfoEntry(IDebugEntryRequestor requestor, AbbreviationEntry entry, List<Dwarf.AttributeValue> list) {
		int tag = (int) entry.tag;
		switch (tag) {
			case DwarfConstants.DW_TAG_compile_unit :
				processCompileUnit(requestor, list);
				break;
			default:
				break;
		}
	}

	// Override parent.
	// Just get the file name of the CU.
	// Argument "requestor" is ignored.
	@Override
	void processCompileUnit(IDebugEntryRequestor requestor, List<AttributeValue> list) {

		String cuName, cuCompDir;
		int		stmtList = INVALID_OFFSET;
		int macroInfo = INVALID_OFFSET;
		cuName = cuCompDir = ""; //$NON-NLS-1$

		for (int i = 0; i < list.size(); i++) {
			AttributeValue av = list.get(i);
			try {
				int name = (int)av.attribute.name;
				switch(name) {
					case DwarfConstants.DW_AT_name:
						cuName = (String)av.value;
						break;
					case DwarfConstants.DW_AT_comp_dir:
						cuCompDir = (String)av.value;
						break;
					case DwarfConstants.DW_AT_stmt_list:
						stmtList = ((Number)av.value).intValue();
						break;
					case DwarfConstants.DW_AT_macro_info:
						macroInfo = ((Number)av.value).intValue();
						break;
					default:
						break;
				}
			} catch (ClassCastException e) {
			}
		}

		addSourceFile(cuCompDir, cuName, macroInfo);
		if (stmtList > INVALID_OFFSET)	// this CU has "stmt_list" attribute
			parseSourceInCULineInfo(cuName, cuCompDir, stmtList);
	}
	
	/**
	 * @since 5.2
	 */
	public String[] getSourceFiles(IProgressMonitor monitor) {
		return getSourceFiles();
	}

}
