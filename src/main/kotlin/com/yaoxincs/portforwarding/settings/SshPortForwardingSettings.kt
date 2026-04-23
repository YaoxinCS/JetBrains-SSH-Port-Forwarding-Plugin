package com.yaoxincs.portforwarding.settings

import com.yaoxincs.portforwarding.model.SshConnectionState
import com.yaoxincs.portforwarding.model.SshPortForwardingState
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.APP)
class SshPortForwardingSettings {

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val lock = Any()
    private val storagePath = SshPortForwardingFileStorage.applicationStoragePath()

    private var state = SshPortForwardingFileStorage.read(storagePath)

    fun connections(): List<SshConnectionState> = stateSnapshot().connections

    fun stateSnapshot(): SshPortForwardingState = synchronized(lock) { state }

    fun findConnection(connectionId: String): SshConnectionState? =
        stateSnapshot().connections.firstOrNull { it.id == connectionId }

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

    fun replaceState(newState: SshPortForwardingState): Boolean =
        updateAndNotify { newState }

    private fun updateAndNotify(transform: (SshPortForwardingState) -> SshPortForwardingState): Boolean {
        val nextState = synchronized(lock) {
            transform(state).also { state = it }
        }
        val persisted = SshPortForwardingFileStorage.write(storagePath, nextState)
        listeners.forEach { it() }
        return persisted
    }
}
