/*
 * Copyright (C) 2014 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.s1tbx.dat.toolviews.Projects;

import org.jdom2.Element;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Jul 2, 2008
 * To change this template use File | Settings | File Templates.
 */
public class TestProjectSubFolders {

    private ProjectSubFolder projectSubFolders;
    private File projectFolder = new File(".");

    @Before
    public void setUp() throws Exception {
        projectSubFolders = new ProjectSubFolder(projectFolder, "Project", false,
                ProjectSubFolder.FolderType.ROOT);
    }

    @Test
    public void testSubFolders() {
        ProjectSubFolder newFolder = projectSubFolders.addSubFolder("newFolder");
        ProjectSubFolder dupFolder = projectSubFolders.addSubFolder("newFolder");

        assertEquals(newFolder, dupFolder);

        ProjectSubFolder foundFolder = projectSubFolders.findFolder("newFolder");
        assertEquals(foundFolder, newFolder);

        projectSubFolders.removeSubFolder(newFolder);
        foundFolder = projectSubFolders.findFolder("newFolder");
        assertNull(foundFolder);

        List<ProjectSubFolder> subFoldersList = projectSubFolders.getSubFolders();
        assertTrue(subFoldersList.isEmpty());

    }

    @Test
    public void testXML() {
        ProjectSubFolder folder1 = projectSubFolders.addSubFolder("Folder1");
        ProjectSubFolder folder2 = projectSubFolders.addSubFolder("Folder2");

        folder1.addFile(new ProjectFile(new File("abc"), "abc"));
        folder2.addFile(new ProjectFile(new File("xyz"), "xyz"));

        Element xmlElement = projectSubFolders.toXML();

        ProjectSubFolder loadedProject = new ProjectSubFolder(new File("."), "Project", false,
                ProjectSubFolder.FolderType.ROOT);

        List<ProjectSubFolder> folderList = new ArrayList();
        List<ProjectFile> prodList = new ArrayList();

        loadedProject.fromXML(xmlElement, folderList, prodList);

        ProjectSubFolder foundFolder = loadedProject.findFolder("Folder1");
        assertEquals(foundFolder.getPath(), folder1.getPath());

        foundFolder = loadedProject.findFolder("Folder2");
        assertEquals(foundFolder.getName(), folder2.getName());
    }
}
