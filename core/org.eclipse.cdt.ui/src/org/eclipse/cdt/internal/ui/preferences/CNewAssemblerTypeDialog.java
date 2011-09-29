/**********************************************************************
 * Copyright (c) 2006 Broadcom Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 *     James Blackburn - Initial implementation
 ***********************************************************************/
package org.eclipse.cdt.internal.ui.preferences;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.filesystem.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class CNewAssemblerTypeDialog extends Dialog {
	
	public CNewAssemblerTypeDialog(Shell parentShell){
		super(parentShell);
	}
	
	private Button		fBrowseButton;
	private Text		fTextTypeName;
	private Text		fTextPath;
	private String[]	fMnemonics;
	private String		fAssemblerName;
	
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText(PreferencesMessages.CEditorNewAssemblerTypeDialog_title); //$NON-NLS-1$
	}

	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
		getButton(IDialogConstants.OK_ID).setEnabled(false);
	}

	protected Control createDialogArea(Composite parent) {
		GridData gd;
		Composite composite = (Composite) super.createDialogArea(parent);
		((GridLayout) composite.getLayout()).numColumns = 3;
		
		Label description= new Label(composite, SWT.NONE);
		description.setText(PreferencesMessages.CEditorNewAssemblerTypeDialog_description);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan=3;
		description.setLayoutData(gd);
		
		Label name = new Label(composite, SWT.NONE);
		name.setText(PreferencesMessages.CEditorNewAssemblerTypeDialog_name);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan=2;
		fTextTypeName = new Text(composite, SWT.BORDER | SWT.SINGLE);
		fTextTypeName.setLayoutData(gd);

		Label path = new Label(composite, SWT.NONE);
		path.setText(PreferencesMessages.CEditorNewAssemblerTypeDialog_path);
		fTextPath = new Text(composite, SWT.BORDER | SWT.SINGLE);
		fTextPath.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		gd = new GridData(GridData.FILL_HORIZONTAL);
		fTextPath.setLayoutData(gd);
		
		fTextPath.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				getButton(IDialogConstants.OK_ID).setEnabled(isValidPath());
			}
		});

		fBrowseButton = new Button(composite, SWT.PUSH);
		fBrowseButton.setText(PreferencesMessages.CEditorNewAssemblerTypeDialog_fileBrowse);
		
		fBrowseButton.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				FileDialog dialog = new FileDialog(getShell(), SWT.NONE);
				dialog.setText(PreferencesMessages.CEditorNewAssemblerTypeDialog_chooseFile); 
				String fileName = fTextPath.getText().trim();
				IPath filePath = new Path(fileName);
				filePath = filePath.removeLastSegments(1);
				dialog.setFilterPath(filePath.toOSString());
				String res = dialog.open();
				if (res == null) {
					return;
				}
				fTextPath.setText(res);
			}
		});

		return composite;
	}

	public String[] getMnemonics() {
		return fMnemonics;
	}
	
	public String getName() {
		return fAssemblerName;
	}
	
	protected void showError(String error) {
		MessageDialog.openInformation(super.getShell(), PreferencesMessages.CEditorNewAssemblerTypeDialog_errorTitle,
				error);	
	}
	
	protected boolean isValidPath() {
		try {
			IFileStore file = EFS.getStore(URIUtil.toURI(fTextPath.getText().trim()));
			return file.fetchInfo().exists(); 
		} catch (Exception e){
			return false;
		}
	}
		
	protected void okPressed() {
		Pattern p = Pattern.compile("\\p{Alnum}*");//$NON-NLS-1$
		fAssemblerName = fTextTypeName.getText().trim();
		if (!p.matcher(fAssemblerName).matches()) {
			fAssemblerName=null;
			showError(PreferencesMessages.CEditorNewAssemblerTypeDialog_errorName);
			return;
		}
		FileInputStream fis=null;
		/*
		 * Read the Mnemonics into a set, then return fMnemonics set up.
		 */
		try { 
			IFileStore file = EFS.getStore(URIUtil.toURI(fTextPath.getText().trim()));
			if (!isValidPath()){
				showError(PreferencesMessages.CEditorNewAssemblerTypeDialog_errorFile);
				return;
			}
		
			TreeSet mnSet = new TreeSet();
			fis = (FileInputStream)file.openInputStream(EFS.NONE, null);
			StreamTokenizer st = new StreamTokenizer(new BufferedReader(new InputStreamReader(fis)));
			
			while (st.nextToken() != StreamTokenizer.TT_EOF) {
				if (st.ttype != StreamTokenizer.TT_WORD || !p.matcher(st.sval).matches()) {
					showError(PreferencesMessages.CEditorNewAssemblerTypeDialog_errorMnemonic+" "+st.toString());//$NON-NLS-1$
					return;
				}
				mnSet.add(st.sval.toLowerCase());				
			} 
			fMnemonics = new String[mnSet.size()];
			mnSet.toArray(fMnemonics);
			super.okPressed();
		} catch (Exception e) {
			fAssemblerName=null;
			fMnemonics=null;
			showError(PreferencesMessages.CEditorNewAssemblerTypeDialog_errorFile);
		} finally {
			try {
				if (fis != null)
					fis.close();
			}catch(Exception e){}
		}
	} 
}
