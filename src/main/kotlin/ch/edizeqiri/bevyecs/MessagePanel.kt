package ch.edizeqiri.bevyecs

import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class MessagesPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val root = DefaultMutableTreeNode("Message")
    private val tree = JTree(root)
    private val model = DefaultTreeModel(root)

    init {
        tree.model = model
        add(JScrollPane(tree), BorderLayout.CENTER)
        refresh()
    }

    private fun refresh() {
        root.removeAllChildren()

        val messages = MessageStructScanner(project).findMessages()

        for (message in messages) {
            val messageNode = DefaultMutableTreeNode(message.name)
            root.add(messageNode)

            for (field in message.fields) {
                messageNode.add(
                    DefaultMutableTreeNode("${field.name}: ${field.type}")
                )
            }
        }

        model.reload()
        expandAll()
    }

    private fun expandAll() {
        for (i in 0 until tree.rowCount) {
            tree.expandRow(i)
        }
    }
}
