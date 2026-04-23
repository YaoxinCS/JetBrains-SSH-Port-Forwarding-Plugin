package com.yaoxincs.portforwarding.settings

import com.yaoxincs.portforwarding.model.SshConnectionState
import com.yaoxincs.portforwarding.model.SshPortForwardingState
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.APP)
@State(
    name = "JetBrainsSshPortForwardingSettings",
    storages = [Storage(value = "jetbrainsSshPortForwarding.xml", roamingType = RoamingType.DISABLED)],
    category = SettingsCategory.TOOLS,
)
class SshPortForwardingSettings :
    SerializablePersistentStateComponent<SshPortForwardingState>(SshPortForwardingState()) {

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun connections(): List<SshConnectionState> = state.connections

    fun stateSnapshot(): SshPortForwardingState = state

    fun findConnection(connectionId: String): SshConnectionState? = state.connections.firstOrNull { it.id == connectionId }

    fun addChangeListener(parent: Disposable, listener: () -> Unit) {
        listeners += listener
        Disposer.register(parent) { listeners.remove(listener) }
    }

    fun addConnection(connection: SshConnectionState): SshConnectionState {
        updateAndNotify { current -> current.copy(connections = current.connections + connection) }
        return connection
    }

    fun removeConnection(connectionId: String) {
        updateAndNotify { current -> current.copy(connections = current.connections.filterNot { it.id == connectionId }) }
    }

    fun updateConnection(updatedConnection: SshConnectionState) {
        updateAndNotify { current ->
            current.copy(
                connections = current.connections.map { connection ->
                    if (connection.id == updatedConnection.id) {
                        updatedConnection
                    } else {
                        connection
                    }
                },
            )
        }
    }

    fun replaceState(newState: SshPortForwardingState) {
        updateAndNotify { newState }
    }

    private fun updateAndNotify(transform: (SshPortForwardingState) -> SshPortForwardingState) {
        updateState(transform)
        listeners.forEach { it() }
    }
}
