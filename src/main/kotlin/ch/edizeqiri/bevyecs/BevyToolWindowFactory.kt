package ch.edizeqiri.bevyecs

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.JButton
import java.awt.FlowLayout

class BevyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout())

        // Add refresh button at the top
        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val refreshButton = JButton("Refresh")
        topPanel.add(refreshButton)
        panel.add(topPanel, BorderLayout.NORTH)

        // Create tree
        val root = DefaultMutableTreeNode("Bevy ECS")
        val tree = Tree(root)

        // Function to refresh tree
        fun refreshTree() {
            root.removeAllChildren()

            val scanner = BevyScanner(project)
            val result = scanner.findBevyItems()

            // Add Messages
            val messagesNode = DefaultMutableTreeNode("Messages (${result.messages.size})")
            result.messages.forEach { item ->
                val itemNode = DefaultMutableTreeNode(item.name)
                item.fields.forEach { field ->
                    itemNode.add(DefaultMutableTreeNode("${field.name}: ${field.type}"))
                }
                messagesNode.add(itemNode)
            }
            root.add(messagesNode)

            // Add Components
            val componentsNode = DefaultMutableTreeNode("Components (${result.components.size})")
            result.components.forEach { item ->
                val itemNode = DefaultMutableTreeNode(item.name)
                item.fields.forEach { field ->
                    itemNode.add(DefaultMutableTreeNode("${field.name}: ${field.type}"))
                }
                componentsNode.add(itemNode)
            }
            root.add(componentsNode)

            // Add Systems (grouped)
            val totalSystems = result.systemGroups.sumOf { it.systems.size }
            val systemsNode = DefaultMutableTreeNode("Systems ($totalSystems)")
            result.systemGroups.forEach { group ->
                val groupNode = DefaultMutableTreeNode("${group.groupName} (${group.systems.size})")
                group.systems.forEach { system ->
                    groupNode.add(DefaultMutableTreeNode(system.name))
                }
                systemsNode.add(groupNode)
            }
            root.add(systemsNode)

            (tree.model as DefaultTreeModel).reload()

            // Expand top-level nodes
            for (i in 0 until tree.rowCount) {
                tree.expandRow(i)
            }
        }

        refreshButton.addActionListener { refreshTree() }

        // Initial load
        refreshTree()

        val scrollPane = JBScrollPane(tree)
        panel.add(scrollPane, BorderLayout.CENTER)

        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
