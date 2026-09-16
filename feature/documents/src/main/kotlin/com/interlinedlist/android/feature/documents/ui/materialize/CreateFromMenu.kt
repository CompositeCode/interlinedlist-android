package com.interlinedlist.android.feature.documents.ui.materialize

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.interlinedlist.android.core.materialize.domain.MaterializeTarget

/** Stable test tags for the "＋ Create" entry points. */
object CreateFromTestTags {
    /** The ＋ Create control on a document browser row. */
    fun row(documentId: String) = "docCreateFrom_$documentId"

    /** The ＋ Create control in the editor's app bar (the whole document). */
    const val EDITOR = "editorCreateFrom"

    /** The ＋ Create control for the editor's highlighted selection. */
    const val SELECTION = "editorCreateFromSelection"

    fun target(target: MaterializeTarget) = "createFromTarget_${target.apiValue}"
}

/**
 * The "＋ Create" menu: pick a destination, then the shared preview / edit /
 * confirm window opens on it.
 *
 * The menu opens for everyone. The subscriber gate is deliberately not consulted
 * here — it applies when a conversion is *confirmed*, and hiding the entry point
 * would leave a free account with no way to discover the feature.
 *
 * [excludedTargets] lets a surface drop a destination the product does not offer
 * from that source.
 */
@Composable
fun CreateFromMenu(
    onSelectTarget: (MaterializeTarget) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    contentDescription: String = "Create from this",
    excludedTargets: Set<MaterializeTarget> = emptySet(),
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        if (label == null) {
            IconButton(onClick = { expanded = true }, enabled = enabled) {
                Icon(Icons.Default.Add, contentDescription = contentDescription)
            }
        } else {
            TextButton(onClick = { expanded = true }, enabled = enabled) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(label)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MaterializeTarget.entries
                .filterNot { it in excludedTargets }
                .forEach { target ->
                    DropdownMenuItem(
                        text = { Text(target.menuLabel) },
                        onClick = {
                            expanded = false
                            onSelectTarget(target)
                        },
                        modifier = Modifier.testTag(CreateFromTestTags.target(target)),
                    )
                }
        }
    }
}

/**
 * The four destination labels the web menu uses. They are spelled out here
 * rather than borrowed from the window, which keeps its own labels private —
 * four words is a smaller price than widening another module's API.
 */
private val MaterializeTarget.menuLabel: String
    get() = when (this) {
        MaterializeTarget.LIST -> "To List"
        MaterializeTarget.DOC -> "To Doc"
        MaterializeTarget.BOTH -> "To List & Doc"
        MaterializeTarget.MESSAGE -> "To Message"
    }
