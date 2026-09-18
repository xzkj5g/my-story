@file:Suppress("FunctionNaming")

package com.example.videocompiler.ui.sequence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.videocompiler.ui.MediaCompilerController
import com.example.videocompiler.ui.MediaCompilerUiState

/**
 * Displays the current [MediaCompilerUiState.selectionSequence] as an ordered list independent
 * of selection order (FR-002), with per-item reorder (up/down, bound to
 * `SelectionSequence.move`, FR-003) and remove (bound to `SelectionSequence.remove`, FR-004)
 * controls. The displayed order updates immediately since it's driven directly from
 * [MediaCompilerController]'s state (Acceptance Scenario 1).
 *
 * Up/down buttons are used instead of drag-and-drop for a simpler, more reliably testable
 * interaction (constitution Principle III: Simplicity/YAGNI) while still satisfying FR-003's
 * "user rearranges the playback sequence" requirement.
 */
@Composable
fun SequenceScreen(
    state: MediaCompilerUiState,
    controller: MediaCompilerController,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Sequence")
            val items = state.selectionSequence.items
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sequenceItem-$index"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${index + 1}. ${item.displayName}")
                    Row {
                        TextButton(
                            onClick = { controller.moveItem(index, index - 1) },
                            enabled = index > 0,
                            modifier = Modifier.testTag("moveUp-$index"),
                        ) {
                            Text("Up")
                        }
                        TextButton(
                            onClick = { controller.moveItem(index, index + 1) },
                            enabled = index < items.lastIndex,
                            modifier = Modifier.testTag("moveDown-$index"),
                        ) {
                            Text("Down")
                        }
                        TextButton(
                            onClick = { controller.removeItem(index) },
                            modifier = Modifier.testTag("removeItem-$index"),
                        ) {
                            Text("Remove")
                        }
                    }
                }
            }
        }
    }
}
