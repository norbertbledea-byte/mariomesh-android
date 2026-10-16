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

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed

/**
 * One relay/output row on the Hardware Control screen. [customLabel] is null until the user renames it; the screen
 * falls back to a localized "Relay N" using [index] in that case.
 */
data class RelaySwitch(val pin: Int, val index: Int, val customLabel: String?, val isOn: Boolean)

data class HardwareControlUiState(
    val nodes: List<Node> = emptyList(),
    val selectedNodeNum: Int? = null,
    val switches: List<RelaySwitch> = emptyList(),
)

/**
 * Drives the Hardware Control tab: picks a target node, then flips GPIO outputs on it via the mesh's RemoteHardware
 * module (REMOTE_HARDWARE_APP). The target node must have `remote_hardware.enabled` set - see the firmware's own
 * module config screen for that switch; this screen only sends writes, it never changes that config itself.
 *
 * Pin state shown here is optimistic (the last value this screen sent), not read back from the node, since the
 * RemoteHardware wire protocol's read/watch replies are not yet wired into this client.
 */
@KoinViewModel
class HardwareControlViewModel(private val commandSender: CommandSender, nodeRepository: NodeRepository, private val uiPrefs: UiPrefs) :
    ViewModel() {

    private val selectedNodeNum = MutableStateFlow<Int?>(null)
    private val pinStates = MutableStateFlow<Map<Int, Boolean>>(emptyMap())

    val uiState: StateFlow<HardwareControlUiState> =
        combine(nodeRepository.nodeDBbyNum, selectedNodeNum, pinStates, uiPrefs.hardwareControlLabels) {
            nodeByNum,
            selected,
            pins,
            labels,
            ->
            val nodes = nodeByNum.values.sortedByDescending { it.isFavorite }
            val effectiveSelection = selected ?: nodes.firstOrNull { it.isFavorite }?.num ?: nodes.firstOrNull()?.num
            HardwareControlUiState(
                nodes = nodes,
                selectedNodeNum = effectiveSelection,
                switches =
                RELAY_PINS.mapIndexed { index, pin ->
                    RelaySwitch(pin = pin, index = index, customLabel = labels[pin], isOn = pins[pin] == true)
                },
            )
        }
            .stateInWhileSubscribed(HardwareControlUiState())

    fun selectNode(nodeNum: Int) {
        selectedNodeNum.value = nodeNum
        pinStates.value = emptyMap()
    }

    fun setPin(pin: Int, on: Boolean) {
        val destNum = uiState.value.selectedNodeNum ?: return
        val mask = 1L shl pin
        safeLaunch(tag = "HardwareControl.setPin") {
            commandSender.sendGpioWrite(destNum = destNum, mask = mask, value = if (on) mask else 0L)
            pinStates.value = pinStates.value + (pin to on)
        }
    }

    fun renamePin(pin: Int, label: String) {
        uiPrefs.setHardwareControlLabel(pin, label)
    }

    companion object {
        /** GPIO pins wired for relay control, in display order. Channel 2 (pin 48) has a known-dead relay board. */
        val RELAY_PINS = listOf(26, 48, 47, 33, 2, 4, 5, 6)
    }
}
