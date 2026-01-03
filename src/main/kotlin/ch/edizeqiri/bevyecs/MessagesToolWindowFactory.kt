package ch.edizeqiri.bevyecs

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.JTextArea

class MessageToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val scanner = MessageStructScanner(project)
        val messages = scanner.findMessages()

        val textArea = JTextArea()
        textArea.text = messages.joinToString("\n\n") { msg ->
            "${msg.name}:\n" + msg.fields.joinToString("\n") { "  ${it.name}: ${it.type}" }
        }
        textArea.isEditable = false

        val content = ContentFactory.getInstance().createContent(textArea, "", false)
        toolWindow.contentManager.addContent(content)
    }
}