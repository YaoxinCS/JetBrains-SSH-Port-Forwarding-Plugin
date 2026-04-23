package com.yaoxincs.portforwarding.settings

import com.yaoxincs.portforwarding.model.SshPortForwardingState
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class SshProjectPortForwardingSettings(project: Project) {

    private val lock = Any()
    private val storagePath = SshPortForwardingFileStorage.projectStoragePath(project)

    private var state = SshPortForwardingFileStorage.read(storagePath)

    fun stateSnapshot(): SshPortForwardingState = synchronized(lock) { state }

    fun replaceState(newState: SshPortForwardingState): Boolean {
        val nextState = synchronized(lock) {
            newState.also { state = it }
        }
        return SshPortForwardingFileStorage.write(storagePath, nextState)
    }
}
