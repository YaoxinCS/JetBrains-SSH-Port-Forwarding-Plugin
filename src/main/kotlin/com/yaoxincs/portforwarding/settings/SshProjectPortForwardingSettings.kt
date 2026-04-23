package com.yaoxincs.portforwarding.settings

import com.yaoxincs.portforwarding.model.SshPortForwardingState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

@Service(Service.Level.PROJECT)
@State(
    name = "JetBrainsSshPortForwardingProjectSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
    category = SettingsCategory.TOOLS,
)
class SshProjectPortForwardingSettings :
    SerializablePersistentStateComponent<SshPortForwardingState>(SshPortForwardingState()) {

    fun stateSnapshot(): SshPortForwardingState = state

    fun replaceState(newState: SshPortForwardingState) {
        updateState { newState }
    }
}
