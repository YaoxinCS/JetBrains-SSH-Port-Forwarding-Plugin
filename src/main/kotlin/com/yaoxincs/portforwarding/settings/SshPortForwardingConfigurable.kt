package com.yaoxincs.portforwarding.settings

import com.yaoxincs.portforwarding.PortForwardingBundle
import com.yaoxincs.portforwarding.model.SshAuthenticationType
import com.yaoxincs.portforwarding.model.SshConnectionState
import com.yaoxincs.portforwarding.model.SshPortForwardingState
import com.yaoxincs.portforwarding.ui.SshPortForwardingEditorPanel
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import java.util.concurrent.TimeUnit
import javax.swing.JComponent

class SshPortForwardingConfigurable : SearchableConfigurable, Configurable.NoScroll {

    private val settings = ApplicationManager.getApplication().getService(SshPortForwardingSettings::class.java)
    private val credentialStore = ApplicationManager.getApplication().getService(SshCredentialStore::class.java)

    private var editorPanel: SshPortForwardingEditorPanel? = null
    private var currentState = loadSecrets(combinedState())

    override fun getId(): String = "com.yaoxincs.portforwarding.settings"

    override fun getDisplayName(): String = PortForwardingBundle.message("settings.display.name")

    override fun createComponent(): JComponent {
        currentState = loadSecrets(combinedState())
        return SshPortForwardingEditorPanel(
            initialState = currentState,
            onStateChanged = ::persistImmediately,
            currentProjectProvider = { currentProject() },
        ).also { editorPanel = it }
    }

    override fun getPreferredFocusedComponent(): JComponent? = editorPanel?.getPreferredFocusTarget()

    override fun isModified(): Boolean = false

    override fun apply() = Unit

    override fun reset() {
        currentState = loadSecrets(combinedState())
        editorPanel?.loadState(currentState)
    }

    override fun disposeUIResources() {
        editorPanel?.let(Disposer::dispose)
        editorPanel = null
    }

    private fun normalized(state: SshPortForwardingState): SshPortForwardingState {
        return state.copy(
            connections = state.connections.map { connection ->
                connection.copy(
                    host = connection.host.trim(),
                    userName = connection.userName.trim(),
                    password = "",
                    passphrase = "",
                    projectScoped = connection.projectScoped,
                    privateKeyPath = if (connection.authenticationType == SshAuthenticationType.KEY_PAIR) {
                        connection.privateKeyPath.trim()
                    } else {
                        ""
                    },
                    savePassword = connection.authenticationType == SshAuthenticationType.PASSWORD && connection.savePassword,
                    savePassphrase = connection.authenticationType == SshAuthenticationType.KEY_PAIR && connection.savePassphrase,
                    useOpenSshConfig = connection.useOpenSshConfig || connection.authenticationType == SshAuthenticationType.OPENSSH_AGENT,
                    port = connection.port.coerceIn(1, 65535),
                    keepAliveIntervalSeconds = connection.keepAliveIntervalSeconds.coerceAtLeast(1),
                    portForwardRules = connection.portForwardRules.map { rule ->
                        rule.copy(
                            bindAddress = rule.bindAddress.trim(),
                            destinationHost = rule.destinationHost.trim(),
                            sourcePort = rule.sourcePort.coerceIn(1, 65535),
                            destinationPort = rule.destinationPort.coerceIn(1, 65535),
                        )
                    },
                )
            },
        )
    }

    private fun loadSecrets(state: SshPortForwardingState): SshPortForwardingState =
        runOffEdt { credentialStore.inflateSecrets(state) }

    private fun persistImmediately(newState: SshPortForwardingState) {
        val project = currentProject()
        val persistedState = normalized(newState)
        val previousCombined = combinedState(project)
        val projectScopedConnections = projectConnections(persistedState)
        val applicationConnections = if (project == null) {
            globalConnections(persistedState) + projectScopedConnections.map { it.copy(projectScoped = false) }
        } else {
            globalConnections(persistedState)
        }

        settings.replaceState(SshPortForwardingState(applicationConnections))
        currentProjectSettings(project)?.replaceState(SshPortForwardingState(projectScopedConnections))
        currentState = newState
        persistSecretsAndSaveAsync(previousCombined, newState, project)
    }

    private fun combinedState(project: Project? = currentProject()): SshPortForwardingState {
        val globalConnections = settings.stateSnapshot().connections.map { it.copy(projectScoped = false) }
        val projectConnections = currentProjectSettings(project)?.stateSnapshot()?.connections?.map { it.copy(projectScoped = true) }.orEmpty()
        return SshPortForwardingState(connections = globalConnections + projectConnections)
    }

    private fun currentProject(): Project? =
        editorPanel?.let { DataManager.getInstance().getDataContext(it).getData(CommonDataKeys.PROJECT) }
            ?: ProjectManager.getInstance().openProjects.singleOrNull()

    private fun currentProjectSettings(project: Project? = currentProject()): SshProjectPortForwardingSettings? =
        project?.getService(SshProjectPortForwardingSettings::class.java)

    private fun globalConnections(state: SshPortForwardingState): List<SshConnectionState> =
        state.connections.filterNot(SshConnectionState::projectScoped).map { it.copy(projectScoped = false) }

    private fun projectConnections(state: SshPortForwardingState): List<SshConnectionState> =
        state.connections.filter(SshConnectionState::projectScoped).map { it.copy(projectScoped = true) }

    private fun persistSecretsAndSaveAsync(
        previousState: SshPortForwardingState,
        draftState: SshPortForwardingState,
        project: Project?,
    ) {
        val app = ApplicationManager.getApplication()
        app.executeOnPooledThread {
            credentialStore.persistSecrets(previousState, draftState)
            project?.save()
            app.saveSettings()
        }
    }

    private fun <T> runOffEdt(action: () -> T): T {
        val app = ApplicationManager.getApplication()
        return if (app.isDispatchThread) {
            app.executeOnPooledThread<T> { action() }.get(15, TimeUnit.SECONDS)
        } else {
            action()
        }
    }
}
