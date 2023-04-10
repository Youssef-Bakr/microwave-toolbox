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

import org.esa.snap.rcp.util.Dialogs;
import org.esa.snap.ui.PopupMenuFactory;
import org.esa.snap.ui.PopupMenuHandler;
import org.esa.snap.ui.UIUtils;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Enumeration;

/**
 * A tree-view component for Projects
 */
class ProjectTree extends JTree implements PopupMenuFactory, ActionListener {

    private Object menuContext;
    private DefaultMutableTreeNode selectedNode;
    private TreePath selectedPath;
    private final Project project = Project.instance();

    /**
     * Constructs a new single selection <code>ProductTree</code>.
     */
    public ProjectTree() {
        this(false);
    }

    /**
     * Constructs a new <code>ProductTree</code> with the given selection mode.
     *
     * @param multipleSelect whether or not the tree is multiple selection capable
     */
    public ProjectTree(final boolean multipleSelect) {

        getSelectionModel().setSelectionMode(multipleSelect
                                                     ? TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
                                                     : TreeSelectionModel.SINGLE_TREE_SELECTION);
        addMouseListener(new PTMouseListener());
        setCellRenderer(new PTCellRenderer());
        setRootVisible(false);
        setShowsRootHandles(true);
        setToggleClickCount(2);
        setExpandsSelectedPaths(true);
        setScrollsOnExpand(true);
        setAutoscrolls(true);
        setDragEnabled(true);
        setDropMode(DropMode.ON);
        setTransferHandler(new TreeTransferHandler());
        putClientProperty("JTree.lineStyle", "Angled");
        ToolTipManager.sharedInstance().registerComponent(this);

        final PopupMenuHandler popupMenuHandler = new PopupMenuHandler(this);
        addMouseListener(popupMenuHandler);
        addKeyListener(popupMenuHandler);
    }

    public JPopupMenu createPopupMenu(final Component component) {
        return null;
    }

    public JPopupMenu createPopupMenu(MouseEvent event) {
        selectedPath = getPathForLocation(event.getX(), event.getY());
        if (selectedPath != null) {
            setSelectionPath(selectedPath);
            selectedNode = (DefaultMutableTreeNode) getLastSelectedPathComponent();
            if (selectedNode != null) {
                Object context = selectedNode.getUserObject();
                if (context != null) {
                    return createPopup(context);
                }
            }
        }
        return null;
    }

    JPopupMenu createPopup(final Object context) {

        final JPopupMenu popup = new JPopupMenu();
        menuContext = context;

        if (context instanceof ProjectSubFolder) {
            final ProjectSubFolder folder = (ProjectSubFolder) context;

            if (isExpanded(selectedPath))
                createMenuItem(popup, "Collapse");
            else
                createMenuItem(popup, "Expand");

            if (selectedNode.isRoot()) {
                createMenuItem(popup, "Expand All");
                addSeparator(popup);
                createMenuItem(popup, "New Project...");
                createMenuItem(popup, "Load Project...");
                createMenuItem(popup, "Save Project");
                createMenuItem(popup, "Save Project As...");
                createMenuItem(popup, "Close Project");
                createMenuItem(popup, "Refresh Project");
            } else {
                addSeparator(popup);
                createMenuItem(popup, "Create Folder");
                final JMenuItem menuItemRename = createMenuItem(popup, "Rename Folder");
                final JMenuItem menuItemRemove = createMenuItem(popup, "Remove Folder");

                if (!folder.canBeRemoved()) {
                    menuItemRename.setEnabled(false);
                    menuItemRemove.setEnabled(false);
                }
                if (!folder.isPhysical()) {
                    createMenuItem(popup, "Clear");
                }
            }

            if (folder.getFolderType() == ProjectSubFolder.FolderType.PRODUCTSET) {
                addSeparator(popup);
                createMenuItem(popup, "New ProductSet...");
            } else if (folder.getFolderType() == ProjectSubFolder.FolderType.GRAPH) {
                addSeparator(popup);
                createMenuItem(popup, "New Graph...");
            }
        } else if (context instanceof ProjectFile) {
            createMenuItem(popup, "Open");

            final DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();
            final ProjectSubFolder folder = (ProjectSubFolder) parentNode.getUserObject();
            if (folder.getFolderType() == ProjectSubFolder.FolderType.PRODUCT) {
                createMenuItem(popup, "Open Subset");
            }
            if (!folder.isPhysical()) {
                createMenuItem(popup, "Import as DIMAP");
                createMenuItem(popup, "Import Subset as DIMAP");
            }

            createMenuItem(popup, "Remove");
        }

        return popup;
    }

    private JMenuItem createMenuItem(final JPopupMenu popup, final String text) {
        final JMenuItem menuItem;
        menuItem = new JMenuItem(text);
        menuItem.addActionListener(this);
        popup.add(menuItem);
        return menuItem;
    }

    private static void addSeparator(JPopupMenu popup) {
        if (popup.getComponentCount() > 0) {
            popup.addSeparator();
        }
    }

    /**
     * Invoked when an action occurs.
     */
    public void actionPerformed(ActionEvent e) {

        final DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();
        final String actionCmd = e.getActionCommand();

        switch (actionCmd) {
            case "Create Folder": {
                final ProjectSubFolder subFolder = (ProjectSubFolder) menuContext;
                project.createNewFolder(subFolder);
                break;
            }
            case "Remove Folder":
                if (parentNode != null) {
                    final Object context = parentNode.getUserObject();
                    if (context != null) {
                        final ProjectSubFolder parentFolder = (ProjectSubFolder) context;
                        final ProjectSubFolder subFolder = (ProjectSubFolder) menuContext;
                        project.deleteFolder(parentFolder, subFolder);
                    }
                }
                break;
            case "Rename Folder":
                if (parentNode != null) {
                    final Object context = parentNode.getUserObject();
                    if (context != null) {
                        final ProjectSubFolder subFolder = (ProjectSubFolder) menuContext;
                        project.renameFolder(subFolder);
                    }
                }
                break;
            case "Remove":
                if (parentNode != null) {
                    final ProjectSubFolder parentFolder = (ProjectSubFolder) parentNode.getUserObject();
                    if (parentFolder != null) {
                        final ProjectFile file = (ProjectFile) menuContext;
                        final Dialogs.Answer status = Dialogs.requestDecision("Delete", "Are you sure you want to delete "
                                + file.getFile().toString(), true, null);
                        if (status == Dialogs.Answer.YES) {
                            project.removeFile(parentFolder, file.getFile());
                        }
                    }
                }
                break;
            case "Open": {
                final ProjectSubFolder parentFolder = (ProjectSubFolder) parentNode.getUserObject();
                final ProjectFile file = (ProjectFile) menuContext;
                Project.openFile(parentFolder, file.getFile());
                break;
            }
            case "Open Subset": {
                final ProjectSubFolder parentFolder = (ProjectSubFolder) parentNode.getUserObject();
                final ProjectFile file = (ProjectFile) menuContext;
                Project.openSubset(parentFolder, file.getFile());
                break;
            }
            case "Import as DIMAP": {
                final ProjectFile file = (ProjectFile) menuContext;
                project.ImportFileList(new File[]{file.getFile()});
                break;
            }
            case "Import Subset as DIMAP": {
                final ProjectSubFolder parentFolder = (ProjectSubFolder) parentNode.getUserObject();
                final ProjectFile file = (ProjectFile) menuContext;
                project.importSubset(parentFolder, file.getFile());
                break;
            }
            case "Clear": {
                final ProjectSubFolder subFolder = (ProjectSubFolder) menuContext;
                project.clearFolder(subFolder);
                break;
            }
            case "Expand All":
                expandAll();
                break;
            case "Expand":
                expandPath(selectedPath);
                break;
            case "Collapse":
                collapsePath(selectedPath);
                break;
            case "New Project...":
                project.CreateNewProject();
                break;
            case "Load Project...":
                project.LoadProject();
                break;
            case "Save Project":
                project.SaveProject();
                break;
            case "Save Project As...":
                project.SaveProjectAs();
                break;
            case "Close Project":
                project.CloseProject();
                break;
            case "Refresh Project":
                project.refreshProjectTree();
                project.notifyEvent(true);
                break;
            case "New ProductSet...": {
                final ProjectSubFolder subFolder = (ProjectSubFolder) menuContext;
                project.createNewProductSet(subFolder);
                break;
            }
            case "New Graph...": {
                final ProjectSubFolder subFolder = (ProjectSubFolder) menuContext;
                Project.createNewGraph(subFolder);
                break;
            }
        }
    }

    void expandAll() {
        final TreeNode root = (TreeNode) getModel().getRoot();
        expandAll(this, new TreePath(root), true);
    }

    /**
     * If expand is true, expands all nodes in the tree.
     * Otherwise, collapses all nodes in the tree.
     *
     * @param tree   the tree
     * @param parent the parent path
     * @param expand or collapse
     */
    private static void expandAll(JTree tree, TreePath parent, boolean expand) {
        // Traverse children
        final TreeNode node = (TreeNode) parent.getLastPathComponent();
        if (node.getChildCount() >= 0) {
            for (Enumeration e = node.children(); e.hasMoreElements(); ) {
                final TreeNode n = (TreeNode) e.nextElement();
                final TreePath path = parent.pathByAddingChild(n);
                expandAll(tree, path, expand);
            }
        }

        // Expansion or collapse must be done bottom-up
        if (expand) {
            tree.expandPath(parent);
        } else {
            tree.collapsePath(parent);
        }
    }


    public void populateTree(DefaultMutableTreeNode treeNode) {
        setModel(new DefaultTreeModel(treeNode));
        expandAll();
    }

    /**
     * Selects the specified object in this tree's model. If the given object has no representation in the tree, the
     * current selection will not be changed.
     *
     * @param toSelect the object whose representation in the tree will be selected.
     */
    public void select(Object toSelect) {
        final TreePath path = findTreePathFor(toSelect);
        if (path != null) {
            setSelectionPath(path);
        }
    }

    private class PTMouseListener extends MouseAdapter {

        @Override
        public void mousePressed(MouseEvent event) {
            int selRow = getRowForLocation(event.getX(), event.getY());
            if (selRow >= 0) {
                int clickCount = event.getClickCount();
                if (clickCount > 1) {
                    final TreePath selPath = getPathForLocation(event.getX(), event.getY());
                    if (selPath != null) {
                        final DefaultMutableTreeNode node = (DefaultMutableTreeNode) selPath.getLastPathComponent();
                        final DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();

                        final Object o = node.getUserObject();
                        if (o instanceof ProjectFile) {
                            final ProjectFile file = (ProjectFile) o;

                            final ProjectSubFolder parentFolder = (ProjectSubFolder) parentNode.getUserObject();
                            Project.openFile(parentFolder, file.getFile());
                        }
                    }
                }
            }
        }
    }

    private TreePath findTreePathFor(final Object o) {
        final DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) getModel().getRoot();
        final Enumeration enumeration = rootNode.depthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) enumeration.nextElement();
            if (treeNode.getUserObject() == o) {
                return new TreePath(treeNode.getPath());
            }
        }
        return null;
    }

    private static class PTCellRenderer extends DefaultTreeCellRenderer {

        private final ImageIcon productIcon;
        private final ImageIcon groupOpenIcon;
        private final ImageIcon projectIcon;

        public PTCellRenderer() {
            productIcon = UIUtils.loadImageIcon("icons/RsProduct16.gif");
            groupOpenIcon = UIUtils.loadImageIcon("icons/RsGroupOpen16.gif");
            projectIcon = UIUtils.loadImageIcon("icons/RsGroupClosed16.gif");

        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree,
                                                      Object value,
                                                      boolean sel,
                                                      boolean expanded,
                                                      boolean leaf,
                                                      int row,
                                                      boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) value;
            value = treeNode.getUserObject();

            if (value instanceof ProjectFile) {
                final ProjectFile file = (ProjectFile) value;
                this.setText(file.getDisplayName());
                this.setIcon(productIcon);
                this.setToolTipText(leaf ? file.getToolTipText() : null);
            } else if (value instanceof ProjectSubFolder) {
                final ProjectSubFolder subFolder = (ProjectSubFolder) value;
                this.setText(subFolder.getName());
                this.setIcon(projectIcon);
                this.setToolTipText(null);
            }

            return this;
        }
    }

    private static class TreeTransferHandler extends TransferHandler {

        public boolean canImport(TransferHandler.TransferSupport info) {
            return info.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        public int getSourceActions(JComponent c) {
            return TransferHandler.COPY;
        }

        /**
         * Bundle up the selected items in a single list for export.
         * Each line is separated by a newline.
         */
        protected Transferable createTransferable(JComponent c) {
            final JTree tree = (JTree) c;
            final TreePath path = tree.getSelectionPath();

            final DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            final DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();

            final Object context = node.getUserObject();
            if (context != null) {
                if (context instanceof ProjectFile) {
                    final ProjectSubFolder parentFolder = (ProjectSubFolder) parentNode.getUserObject();
                    final ProjectFile file = (ProjectFile) context;

                    if (parentFolder.getFolderType() == ProjectSubFolder.FolderType.PRODUCTSET) {
                        return new StringSelection(ProductSet.GetListAsString(file.getFile()));
                    } else {
                        return new StringSelection(file.getFile().getAbsolutePath());
                    }
                } else if (context instanceof ProjectSubFolder) {
                    final ProjectSubFolder parentFolder = (ProjectSubFolder) context;

                    return new StringSelection(parentFolder.getPath().getAbsolutePath());
                }
            }
            return null;
        }

        /**
         * Perform the actual import.  This only supports drag and drop.
         */
        public boolean importData(TransferHandler.TransferSupport info) {
            if (!info.isDrop()) {
                return false;
            }

            final JTree tree = (JTree) info.getComponent();
            final DefaultTreeModel treeModel = (DefaultTreeModel) tree.getModel();

            // Get the string that is being dropped.
            final Transferable t = info.getTransferable();
            String data;
            try {
                data = (String) t.getTransferData(DataFlavor.stringFlavor);
            } catch (Exception e) {
                return false;
            }

            // Wherever there is a newline in the incoming data,
            // break it into a separate item in the list.
            final String[] values = data.split("\n");

            // Perform the actual import.
            for (String value : values) {
                final TreePath dropPath = tree.getDropLocation().getPath();
                final DefaultMutableTreeNode node = (DefaultMutableTreeNode) dropPath.getLastPathComponent();
                final DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();

                final Object o = node.getUserObject();
                if (o instanceof ProjectFile) {
                    final ProjectFile projFile = (ProjectFile) o;
                    final ProjectSubFolder projSubFolder = (ProjectSubFolder) parentNode.getUserObject();
                    if (projSubFolder.getFolderType() == ProjectSubFolder.FolderType.PRODUCTSET) {
                        ProductSet.AddProduct(projFile.getFile(), new File(value));
                    }
                }
            }
            return true;
        }
    }

}
