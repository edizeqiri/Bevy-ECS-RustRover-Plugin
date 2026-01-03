package ch.edizeqiri.bevyecs

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.util.descendantsOfType
import org.rust.lang.core.psi.*
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.ext.name
import java.io.File

data class MessageField(
    val name: String,
    val type: String
)

data class BevyItem(
    val name: String,
    val fields: List<MessageField>,
    val type: BevyItemType,
    val filePath: String? = null
)

enum class BevyItemType {
    MESSAGE, COMPONENT, SYSTEM
}

data class SystemGroup(
    val groupName: String,
    val systems: List<BevyItem>
)

class BevyScanner(private val project: Project) {

    data class ScanResult(
        val messages: List<BevyItem>,
        val components: List<BevyItem>,
        val systemGroups: List<SystemGroup>
    )

    fun findBevyItems(): ScanResult {
        val scope = GlobalSearchScope.projectScope(project)
        val files = FileTypeIndex.getFiles(RsFileType, scope)

        val messages = mutableListOf<BevyItem>()
        val components = mutableListOf<BevyItem>()
        val systems = mutableListOf<BevyItem>()

        for (vf in files) {
            val file = PsiManager.getInstance(project).findFile(vf) as? RsFile ?: continue
            val filePath = vf.path

            // Scan structs for Messages and Components
            file.descendantsOfType<RsStructItem>().forEach { struct ->
                val itemType = when {
                    hasDerivedTrait(struct, "Message") -> BevyItemType.MESSAGE
                    hasDerivedTrait(struct, "Component") -> BevyItemType.COMPONENT
                    else -> null
                }

                if (itemType != null) {
                    val fields = struct.blockFields
                        ?.namedFieldDeclList
                        ?.map {
                            MessageField(
                                name = it.name ?: "<unknown>",
                                type = it.typeReference?.text ?: "unknown"
                            )
                        }
                        ?: emptyList()

                    val item = BevyItem(
                        name = struct.name ?: "<unnamed>",
                        fields = fields,
                        type = itemType,
                        filePath = filePath
                    )

                    when (itemType) {
                        BevyItemType.MESSAGE -> messages.add(item)
                        BevyItemType.COMPONENT -> components.add(item)
                        else -> {}
                    }
                }
            }

            // Scan functions for Systems
            file.descendantsOfType<RsFunction>().forEach { function ->
                if (isSystem(function)) {
                    systems.add(
                        BevyItem(
                            name = function.name ?: "<unnamed>",
                            fields = emptyList(),
                            type = BevyItemType.SYSTEM,
                            filePath = filePath
                        )
                    )
                }
            }
        }

        // Group systems by folder/file
        val systemGroups = groupSystems(systems)

        return ScanResult(messages, components, systemGroups)
    }

    private fun groupSystems(systems: List<BevyItem>): List<SystemGroup> {
        val projectBasePath = project.basePath ?: return emptyList()

        val grouped = systems.groupBy { system ->
            val filePath = system.filePath ?: return@groupBy "Unknown"
            val relativePath = File(filePath).relativeTo(File(projectBasePath)).path

            // Extract folder path
            val parts = relativePath.split(File.separator)

            when {
                // If in src/ directory, use the file name (without extension) as group
                parts.size >= 2 && parts[0] == "src" && parts.size == 2 -> {
                    parts[1].removeSuffix(".rs")
                }
                // If nested deeper than src/, use the parent folder
                parts.size > 2 && parts[0] == "src" -> {
                    parts.dropLast(1).drop(1).joinToString("/")
                }
                // Otherwise use the parent folder
                parts.size > 1 -> {
                    parts.dropLast(1).joinToString("/")
                }

                else -> "Unknown"
            }
        }

        return grouped.map { (groupName, systemList) ->
            SystemGroup(groupName, systemList.sortedBy { it.name })
        }.sortedBy { it.groupName }
    }

    private fun hasDerivedTrait(struct: RsStructItem, traitName: String): Boolean {
        struct.outerAttrList.forEach { attr ->
            val metaItem = attr.metaItem
            if (metaItem?.name == "derive") {
                val traits = metaItem.metaItemArgs?.metaItemList?.mapNotNull { it.name }
                if (traits?.contains(traitName) == true) {
                    return true
                }
            }
        }
        return false
    }

    private fun isSystem(function: RsFunction): Boolean {
        val params = function.valueParameterList?.valueParameterList ?: return false

        return params.any { param ->
            val typeText = param.typeReference?.text ?: ""
            typeText.contains("Query") ||
                    typeText.contains("Commands") ||
                    typeText.contains("Res") ||
                    typeText.contains("ResMut") ||
                    typeText.contains("EventWriter") ||
                    typeText.contains("EventReader")
        }
    }
}