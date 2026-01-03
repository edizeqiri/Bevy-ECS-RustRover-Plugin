package ch.edizeqiri.bevyecs

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.util.descendantsOfType
import org.rust.lang.core.psi.*
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.ext.name
import org.rust.lang.core.psi.ext.queryAttributes

class MessageStructScanner(private val project: Project) {

    fun findMessages(): List<MessageStruct> {
        val result = mutableListOf<MessageStruct>()

        val scope = GlobalSearchScope.projectScope(project)
        val files = FileTypeIndex.getFiles(RsFileType, scope)

        for (vf in files) {
            val file = PsiManager.getInstance(project).findFile(vf) as? RsFile
                ?: continue

            file.descendantsOfType<RsStructItem>().forEach { struct ->
                if (!hasMessageDerive(struct)) return@forEach

                val fields = struct.blockFields
                    ?.namedFieldDeclList
                    ?.map {
                        MessageField(
                            name = it.name ?: "<unknown>",
                            type = it.typeReference?.text ?: "unknown"
                        )
                    }
                    ?: emptyList()

                result += MessageStruct(
                    name = struct.name ?: "<unnamed>",
                    fields = fields
                )
            }
        }

        return result
    }

    private fun hasMessageDerive(struct: RsStructItem): Boolean {
        // Debug: print what we find
        println("Checking struct: ${struct.name}")

        struct.outerAttrList.forEach { attr ->
            println("  Attribute: ${attr.text}")
            val metaItem = attr.metaItem
            println("  MetaItem name: ${metaItem?.name}")

            if (metaItem?.name == "derive") {
                val traits = metaItem.metaItemArgs?.metaItemList?.mapNotNull { it.name }
                println("  Derived traits: $traits")

                if (traits?.contains("Message") == true) {
                    return true
                }
            }
        }

        return false
    }
}
