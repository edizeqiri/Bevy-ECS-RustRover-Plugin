package ch.edizeqiri.bevyecs

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.application.ApplicationManager
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.JButton
import java.awt.FlowLayout

class BevyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout())

        // Add buttons at the top
        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val expandAllButton = JButton("Expand All")
        val collapseAllButton = JButton("Collapse All")
        topPanel.add(expandAllButton)
        topPanel.add(collapseAllButton)
        panel.add(topPanel, BorderLayout.NORTH)

        // Create tree
        val root = DefaultMutableTreeNode("Bevy ECS")
        val tree = Tree(root)

        // Add double-click listener for navigation
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
                    val userObject = node.userObject

                    if (userObject is BevyItem) {
                        navigateToItem(project, userObject)
                    }
                }
            }
        })

        // Function to refresh tree
// Function to refresh tree
        fun refreshTree() {
            // Run scanning in background thread with read access
            ApplicationManager.getApplication().executeOnPooledThread {
                val result = ApplicationManager.getApplication().runReadAction<BevyScanner.ScanResult> {
                    val scanner = BevyScanner(project)
                    scanner.findBevyItems()
                }

                // Update UI on EDT
                ApplicationManager.getApplication().invokeLater {
                    root.removeAllChildren()

                    // Add Messages (grouped)
                    val totalMessages = result.messageGroups.sumOf { it.items.size }
                    val messagesNode = DefaultMutableTreeNode("Messages ($totalMessages)")
                    result.messageGroups.forEach { group ->
                        val groupNode = DefaultMutableTreeNode("${group.groupName} (${group.items.size})")
                        group.items.forEach { item ->
                            val itemNode = DefaultMutableTreeNode(item)
                            item.fields.forEach { field ->
                                itemNode.add(DefaultMutableTreeNode("${field.name}: ${field.type}"))
                            }
                            groupNode.add(itemNode)
                        }
                        messagesNode.add(groupNode)
                    }
                    root.add(messagesNode)

                    // Add Components (grouped)
                    val totalComponents = result.componentGroups.sumOf { it.items.size }
                    val componentsNode = DefaultMutableTreeNode("Components ($totalComponents)")
                    result.componentGroups.forEach { group ->
                        val groupNode = DefaultMutableTreeNode("${group.groupName} (${group.items.size})")
                        group.items.forEach { item ->
                            val itemNode = DefaultMutableTreeNode(item)
                            item.fields.forEach { field ->
                                itemNode.add(DefaultMutableTreeNode("${field.name}: ${field.type}"))
                            }
                            groupNode.add(itemNode)
                        }
                        componentsNode.add(groupNode)
                    }
                    root.add(componentsNode)

                    // Add Systems (grouped)
                    val totalSystems = result.systemGroups.sumOf { it.items.size }
                    val systemsNode = DefaultMutableTreeNode("Systems ($totalSystems)")
                    result.systemGroups.forEach { group ->
                        val groupNode = DefaultMutableTreeNode("${group.groupName} (${group.items.size})")
                        group.items.forEach { system ->
                            groupNode.add(DefaultMutableTreeNode(system))
                        }
                        systemsNode.add(groupNode)
                    }
                    root.add(systemsNode)

                    (tree.model as DefaultTreeModel).reload()

                    // Expand top-level nodes by default
                    tree.expandRow(0) // Root
                    tree.expandRow(1) // Messages
                    tree.expandRow(2) // Components
                    tree.expandRow(3) // Systems
                }
            }
        }


        expandAllButton.addActionListener {
            // Expand categories → groups → items (depth 0-3), but not fields
            expandNode(tree, root, maxDepth = 3)
        }

        collapseAllButton.addActionListener {
            // Collapse all groups but keep categories visible
            collapseNode(tree, root, collapseRoot = false)
        }

        // Set up auto-refresh on file changes
        val connection = project.messageBus.connect()
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                // Check if any Rust files were modified
                val hasRustChanges = events.any { event ->
                    event.file?.extension == "rs"
                }

                if (hasRustChanges) {
                    refreshTree()
                }
            }
        })

        // Initial load
        refreshTree()

        val scrollPane = JBScrollPane(tree)
        panel.add(scrollPane, BorderLayout.CENTER)

        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)

        // Clean up connection when tool window is closed
        content.setDisposer {
            connection.disconnect()
        }
    }

    private fun expandNode(tree: Tree, node: DefaultMutableTreeNode, currentDepth: Int = 0, maxDepth: Int = 3) {
        if (currentDepth >= maxDepth) return

        tree.expandPath(TreePath(node.path))

        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            expandNode(tree, child, currentDepth + 1, maxDepth)
        }
    }

    private fun collapseNode(tree: Tree, node: DefaultMutableTreeNode, collapseRoot: Boolean = true) {
        // Collapse children first (bottom-up)
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            collapseNode(tree, child, collapseRoot = true)
        }

        if (collapseRoot && node != tree.model.root) {
            tree.collapsePath(TreePath(node.path))
        }
    }

    private fun navigateToItem(project: Project, item: BevyItem) {
        val filePath = item.filePath ?: return
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return

        val fileEditorManager = FileEditorManager.getInstance(project)

        // Use textOffset if available, otherwise use line number
        if (item.textOffset != null) {
            val descriptor = OpenFileDescriptor(project, virtualFile, item.textOffset)
            fileEditorManager.openTextEditor(descriptor, true)
        } else if (item.lineNumber != null) {
            val descriptor = OpenFileDescriptor(project, virtualFile, item.lineNumber, 0)
            fileEditorManager.openTextEditor(descriptor, true)
        } else {
            fileEditorManager.openFile(virtualFile, true)
        }
    }
}