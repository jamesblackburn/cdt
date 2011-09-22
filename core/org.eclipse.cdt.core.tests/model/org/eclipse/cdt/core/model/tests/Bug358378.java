/*******************************************************************************
 * Copyright (c) 2010, 2011 Broadcom Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Phil Mason (Broadcom Corp) - initial API and implementation
 *     Broadcom - Bug 2438
 *******************************************************************************/
package org.eclipse.cdt.core.model.tests;

import java.io.ByteArrayInputStream;
import java.util.List;

import junit.framework.Test;

import org.eclipse.cdt.core.dom.IPDOMManager;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.model.ICContainer;
import org.eclipse.cdt.core.model.ICElement;
import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.cdt.core.model.IParent;
import org.eclipse.cdt.core.model.ISourceRoot;
import org.eclipse.cdt.core.settings.model.CSourceEntry;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.settings.model.ICSourceEntry;
import org.eclipse.cdt.core.testplugin.CProjectHelper;
import org.eclipse.cdt.core.testplugin.util.BaseTestCase;
import org.eclipse.cdt.internal.core.settings.model.CProjectDescriptionManager;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;

/**
 * Regression test for Bug 358378.
 * If a file is moved when it is excluded from the build the exclusion should follow
 * the file. This test checks that it does.
 */
public class Bug358378 extends BaseTestCase {

	public static Test suite() {
		return suite(Bug358378.class, "_");
	}

	//private IProject project;
//    IWorkspace workspace;
    IWorkspace workspace;
    IWorkspaceRoot root;
    IProject project_c, project_cc;
    NullProgressMonitor monitor;
	@Override
	protected void setUp() throws Exception {
		IWorkspaceDescription desc;
		workspace= ResourcesPlugin.getWorkspace();
		root= workspace.getRoot();
		monitor = new NullProgressMonitor();
		if (workspace==null) 
			fail("Workspace was not setup");
		if (root==null)
			fail("Workspace root was not setup");
		desc=workspace.getDescription();
		desc.setAutoBuilding(false);
		workspace.setDescription(desc);
		
	}

	/**
	 * If A files is moved and has the exclusion property set check the exclusion follows the 
	 * file.
	 */
	public void testExclusionFollowsFile() throws Exception {
					 	ICProject testProject;
	        testProject=CProjectHelper.createCProject("bug358378", "none", IPDOMManager.ID_NO_INDEXER);
	        if (testProject==null)
	            fail("Unable to create project");
		
			// Create some test files and folders
	        IFolder subFolder = testProject.getProject().getFolder("sub");
	    	subFolder.create(true, true, monitor);
	    	IFile fileA = testProject.getProject().getFile("a.cpp");
	        fileA.create(new ByteArrayInputStream(new byte[0]), true, monitor);
	        IFile fileB = testProject.getProject().getFile("b.cpp");
	        fileB.create(new ByteArrayInputStream(new byte[0]), true, monitor);
	        
	        List<ICElement> cSourceRoots = ((IParent) testProject).getChildrenOfType(ICElement.C_CCONTAINER);
	        // Check that there is only one project
	        assertEquals(1, cSourceRoots.size());
	        // Check that it has the name we expect
	        assertEquals(((ICElement) testProject).getElementName(), cSourceRoots.get(0).getElementName());
	        
	        // Check that there is only one folder (sub) and that it has the name we expect
	        ISourceRoot sourceRoot = (ISourceRoot) cSourceRoots.get(0);	        
	        List<ICElement> cContainers = sourceRoot.getChildrenOfType(ICElement.C_CCONTAINER);
	        assertEquals(1, cContainers.size());
	        assertEquals(subFolder.getName(), cContainers.get(0).getElementName());

	        ICContainer subContainer = (ICContainer) cContainers.get(0);

	        // nothing in the folder yet
	        List<ICElement> tUnits = subContainer.getChildrenOfType(ICElement.C_UNIT);
	        assertEquals(0, tUnits.size());
	    
	        // There are two files in the root
	        tUnits = sourceRoot.getChildrenOfType(ICElement.C_UNIT);
	        assertEquals(2, tUnits.size());
	        assertEquals(fileA.getName(), tUnits.get(0).getElementName());
	        assertEquals(fileB.getName(), tUnits.get(1).getElementName());
	        
	        // Get the active config and check it is not null
			ICProjectDescription prjDesc= CoreModel.getDefault().getProjectDescription(testProject.getProject(), true);
			ICConfigurationDescription activeCfg= prjDesc.getActiveConfiguration();
			assertNotNull(activeCfg);
	        
	        // add filter to source entry
			ICSourceEntry[] entries = activeCfg.getSourceEntries();
			final String sourceEntryName = entries[0].getName();
			final IPath[] exclusionPatterns = new IPath[] { new Path("a.cpp") };

			ICSourceEntry entry = new CSourceEntry(sourceEntryName, exclusionPatterns, entries[0].getFlags());
			activeCfg.setSourceEntries(new ICSourceEntry[] {entry});

			// store the changed configuration
			CoreModel.getDefault().setProjectDescription(testProject.getProject(), prjDesc);

			// Move the files
			fileA.move(subContainer.getPath().append(fileA.getName()), true, monitor);
			fileB.move(subContainer.getPath().append(fileB.getName()), true, monitor);
			
			CProjectDescriptionManager.runWspModification(new IWorkspaceRunnable(){	

				public void run(IProgressMonitor monitor) {
				}
			}, new NullProgressMonitor());			
			
			//Thread.sleep(5000);
			
			
			// Check that there is only one project and it has the name we expect
			cSourceRoots = testProject.getChildrenOfType(ICElement.C_CCONTAINER);
	        assertEquals(1, cSourceRoots.size());
	        assertEquals(testProject.getElementName(), cSourceRoots.get(0).getElementName());
	        
	        sourceRoot = (ISourceRoot) cSourceRoots.get(0);
	        
			// Check that there is only one folder and it has the name we expect
	        cContainers = sourceRoot.getChildrenOfType(ICElement.C_CCONTAINER);
	        assertEquals(1, cContainers.size());
	        assertEquals(subFolder.getName(), cContainers.get(0).getElementName());
	        
	        subContainer = (ICContainer) cContainers.get(0);
	        
	        // There should still be nothing in the root folder
	        tUnits = sourceRoot.getChildrenOfType(ICElement.C_UNIT);
	        assertEquals(0, tUnits.size());

	        // Now only one thing in the folder as the exclusion will follow fileA
	        tUnits = subContainer.getChildrenOfType(ICElement.C_UNIT);
	        assertEquals(1, tUnits.size());
	        assertEquals(fileB.getName(), tUnits.get(0).getElementName());
	        
	        try {
	        	testProject.getProject().delete(true,true,monitor);
	        } 
	        catch (CoreException e) {}
	}


}
