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
package org.eclipse.cdt.internal.ui.dialogs;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.eclipse.cdt.ui.dialogs.AbstractCommandLauncherDialog;

import org.eclipse.cdt.internal.core.WrappedCommandLauncher;

/**
 * A dialog for configuring advanced options on the Wrapped CommandLauncher
 * @since 5.2
 */
public class WrappedCommandLauncherDialog extends AbstractCommandLauncherDialog {

	private Text fNewCommand;
	private Text fNewPreArgs;
	private Text fNewPostArgs;

	public WrappedCommandLauncherDialog() {
		setTitle(Messages.WrappedCommandLauncherDialog_AdvancedTitle);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite c = (Composite) super.createDialogArea(parent);

		Composite comp = new Composite(c, SWT.NONE);
		GridLayout gl = new GridLayout(5, false);
		gl.horizontalSpacing = 15;
		comp.setLayout(gl);

		Label l = new Label(comp, SWT.WRAP);
		GridData gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		gd.horizontalSpan = 5;
		l.setLayoutData(gd);
		l.setText(Messages.WrappedCommandLauncherDialog_WrapDescription +
				Messages.WrappedCommandLauncherDialog_WrapExample);

		l = new Label(comp, SWT.WRAP);
		l.setText(""); //$NON-NLS-1$
		l.setLayoutData(new GridData(GridData.BEGINNING, GridData.BEGINNING, false, false, 5, 1));

		// Create column label
		createLabel(comp, Messages.WrappedCommandLauncherDialog_NewCommand);
		createLabel(comp, Messages.WrappedCommandLauncherDialog_OriginalCommand);
		createLabel(comp, Messages.WrappedCommandLauncherDialog_PrependCommand);
		createLabel(comp, Messages.WrappedCommandLauncherDialog_OriginalArgs);
		createLabel(comp, Messages.WrappedCommandLauncherDialog_AppendedArgs);

		fNewCommand = createText(comp);
		createLabel(comp, Messages.WrappedCommandLauncherDialog_egMake);
		fNewPreArgs = createText(comp);
		createLabel(comp, Messages.WrappedCommandLauncherDialog_egAll);
		fNewPostArgs = createText(comp);

		// Initialise the preferences from the prefstore
		fNewCommand.setText(getInitialValue(WrappedCommandLauncher.PREF_COMMAND_NAME, "")); //$NON-NLS-1$
		fNewPreArgs.setText(getInitialValue(WrappedCommandLauncher.PREF_PREPEND_ARGUMENT, "")); //$NON-NLS-1$
		fNewPostArgs.setText(getInitialValue(WrappedCommandLauncher.PREF_APPEND_ARGUMENT, "")); //$NON-NLS-1$

		return c;
	}

	private Text createText(Composite comp) {
		Text t = new Text(comp, SWT.BORDER);
		GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, true);
		t.setLayoutData(gd);
		return t;
	}

	private Label createLabel(Composite comp, String text) {
		Label l = new Label(comp, SWT.NONE);
		GridData gd = new GridData();
		gd.horizontalAlignment = GridData.CENTER;
		l.setLayoutData(gd);
		l.setText(text);
		return l;
	}

	/**
	 * Set the preference in the pref store. Remove the pref if the value is unset / whitespace
	 * @param prefsKey
	 * @param value
	 */
	private void setPrefs(String prefsKey, String value) {
		if (value == null)
			prefStore.remove(prefsKey);
		else
			prefStore.put(prefsKey, value);
	}

	@Override
	protected void buttonPressed(int buttonId) {
		if (buttonId == OK) {
			// Set the preferences
			setPrefs(WrappedCommandLauncher.PREF_COMMAND_NAME, 		fNewCommand.getText());
			setPrefs(WrappedCommandLauncher.PREF_PREPEND_ARGUMENT, 	fNewPreArgs.getText());
			setPrefs(WrappedCommandLauncher.PREF_APPEND_ARGUMENT,	fNewPostArgs.getText());
		}
		super.buttonPressed(buttonId);
	}

}
