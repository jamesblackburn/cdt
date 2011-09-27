package org.eclipse.cdt.buildconfig.txt;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * This dialog provides UI for exporting build configuration settings
 *
 * Relative paths are treated as relative to the project
 */
public class BuildConfigurationExportDialog extends Dialog {

	private final IProject project;
	private static IPath path = new Path(CDTBuildSettingsExporter.DEFAULT_BUILD_CONFIG_FILE);
	boolean boolVerbose = false;
	static boolean boolPushUpTree = false;

	private Text tPath;
	private Button bVerbose;
	private Button bPushUpTree;

	/**
	 * Provide a project for the export to be relative to
	 * @param project
	 * @param parentShell
	 */
	protected BuildConfigurationExportDialog(IProject project, Shell parentShell) {
		super(parentShell);
		this.project = project;
		setBlockOnOpen(true);
		setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		parent = (Composite)super.createDialogArea(parent);

		Composite c = new Composite(parent, SWT.NONE);
		c.setLayout(new GridLayout(4, false));
		c.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		final String toolTip = "Location of File to persist project's build settings...";
		Label l = new Label(c, SWT.NONE);
		l.setText("File");
		l.setToolTipText(toolTip);

		tPath = new Text(c, SWT.BORDER);
		tPath.setText(path.toOSString());
		tPath.setToolTipText(toolTip);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 2;
		gd.widthHint = 300;
		tPath.setLayoutData(gd);

		Button b = new Button(c, SWT.BORDER);
		b.setText("Browse...");
		b.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				openFileDialog();
			}
		});

		// Show removed options
		c = new Composite(parent, SWT.NONE);
		c.setLayout(new GridLayout(2, false));
		bVerbose = new Button(c, SWT.CHECK);
		bVerbose.setText("Verbose");
		bVerbose.setToolTipText("Includes additional information that would \n" +
									"ordinarily be discarded");
		bPushUpTree = new Button(c, SWT.CHECK);
		bPushUpTree.setText("Hide Common settings");
		bPushUpTree.setToolTipText("Ordinarily settings such as " +
									"include / defines are printed in their entirety at each level in the tree.\n" +
									"This option causes common occurrences " +
									"of these settings to be pushed up the tree.");
		bPushUpTree.setSelection(boolPushUpTree);

		return parent;
	}

	/**
	 * Method to show the user a file selection dialog, and update the path field
	 */
	private void openFileDialog() {
		FileDialog fd = new FileDialog(Display.getDefault().getActiveShell(), SWT.SAVE);
		fd.setText("Export Build Settings...");
		fd.setFileName(new Path(tPath.getText()).lastSegment());

		// Filter path is project for relative paths
		// Or absolute directory for non-relative paths
		String filterPath = null;
		if (!tPath.getText().equals("")) {
			IPath p = new Path(tPath.getText()).removeLastSegments(1);
			if (p.isAbsolute())
				filterPath = p.toOSString();
			else // Relative paths should be relative to project
				filterPath = project.getLocation().append(p).toOSString();
		}
		if (filterPath == null)
			filterPath = project.getLocation().toOSString();
		fd.setFilterPath(filterPath);

		String name = fd.open();
		if (name != null) {
			IPath prjLoc = project.getLocation();
			IPath thisPath = new Path(name);
			if (prjLoc.isPrefixOf(thisPath))
				thisPath = thisPath.removeFirstSegments(prjLoc.segmentCount());
			tPath.setText(thisPath.toOSString());
			path = thisPath;
		}
	}

	@Override
	protected void okPressed() {
		path = new Path(tPath.getText());
		boolVerbose = bVerbose.getSelection();
		boolPushUpTree = bPushUpTree.getSelection();

		boolean exists = false;
		if (path.isAbsolute())
			exists = new File(path.toOSString()).exists();
		else
			exists = project.findMember(path) != null && project.findMember(path).exists();

		boolean confirmed = true;
		if (exists)
			confirmed = MessageDialog.openQuestion(Display.getDefault().getActiveShell(), "Confirm overwrite", "Overwrite existing file:\n" + path + "?");
		if (confirmed)
			super.okPressed();
		else
			super.cancelPressed();
	}

	public String getLocation() {
		if (path.isAbsolute())
			return path.toOSString();
		else
			return project.getLocation().append(path).toOSString();
	}

	@Override
	protected void configureShell(Shell newShell) {
		newShell.setText("Export Build Settings...");
		super.configureShell(newShell);
	}

}
