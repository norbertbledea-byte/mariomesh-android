/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.feature.settings.hardware

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.hardware_control
import org.meshtastic.core.resources.hardware_control_no_target
import org.meshtastic.core.resources.hardware_control_relay_default
import org.meshtastic.core.resources.hardware_control_rename
import org.meshtastic.core.resources.hardware_control_rename_hint
import org.meshtastic.core.resources.hardware_control_target_node
import org.meshtastic.core.resources.save
import org.meshtastic.core.ui.component.BasicListItem
import org.meshtastic.core.ui.component.MainAppBar

@Composable
fun HardwareControlScreen(viewModel: HardwareControlViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var renamingPin by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.hardware_control),
                canNavigateUp = false,
                onNavigateUp = {},
                ourNode = null,
                showNodeChip = false,
                onClickChip = {},
                actions = {},
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding)) {
            NodePicker(
                nodes = uiState.nodes,
                selectedNodeNum = uiState.selectedNodeNum,
                onSelect = viewModel::selectNode,
            )

            if (uiState.selectedNodeNum == null) {
                Text(
                    text = stringResource(Res.string.hardware_control_no_target),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            uiState.switches.forEach { relay ->
                val defaultLabel = stringResource(Res.string.hardware_control_relay_default, relay.index + 1)
                BasicListItem(
                    text = relay.customLabel?.takeIf(String::isNotBlank) ?: defaultLabel,
                    supportingText = "GPIO ${relay.pin}",
                    enabled = uiState.selectedNodeNum != null,
                    trailingContent = {
                        Switch(
                            checked = relay.isOn,
                            enabled = uiState.selectedNodeNum != null,
                            onCheckedChange = { viewModel.setPin(relay.pin, it) },
                        )
                    },
                    onClick = { viewModel.setPin(relay.pin, !relay.isOn) },
                    onLongClick = { renamingPin = relay.pin },
                )
            }
        }
    }

    val pinBeingRenamed = renamingPin
    if (pinBeingRenamed != null) {
        val currentSwitch = uiState.switches.firstOrNull { it.pin == pinBeingRenamed }
        val currentDefault = stringResource(Res.string.hardware_control_relay_default, (currentSwitch?.index ?: 0) + 1)
        RenameDialog(
            initialValue = currentSwitch?.customLabel?.takeIf(String::isNotBlank) ?: currentDefault,
            onConfirm = { newLabel ->
                viewModel.renamePin(pinBeingRenamed, newLabel)
                renamingPin = null
            },
            onDismiss = { renamingPin = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodePicker(nodes: List<Node>, selectedNodeNum: Int?, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = nodes.firstOrNull { it.num == selectedNodeNum }

    // Auto-pick a default once nodes load, so the picker isn't stuck showing nothing selectable.
    LaunchedEffect(nodes, selectedNodeNum) {
        if (selectedNodeNum == null) {
            (nodes.firstOrNull { it.isFavorite } ?: nodes.firstOrNull())?.let { onSelect(it.num) }
        }
    }

    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected?.user?.long_name.orEmpty().ifBlank { selected?.num?.let { "!${it.toString(16)}" } ?: "" },
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(Res.string.hardware_control_target_node)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                nodes.forEach { node ->
                    DropdownMenuItem(
                        text = { Text(node.user.long_name.ifBlank { "!${node.num.toString(16)}" }) },
                        onClick = {
                            onSelect(node.num)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(initialValue: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.hardware_control_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(Res.string.hardware_control_rename_hint)) },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(Res.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
