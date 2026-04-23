package com.yaoxincs.portforwarding.runtime

import com.yaoxincs.portforwarding.PortForwardingBundle
import com.yaoxincs.portforwarding.model.PortForwardDirection
import com.yaoxincs.portforwarding.model.PortForwardRuleState
import com.yaoxincs.portforwarding.model.SshAuthenticationType
import com.yaoxincs.portforwarding.model.SshConnectionState
import com.yaoxincs.portforwarding.model.TunnelRuntimeState
import com.yaoxincs.portforwarding.model.TunnelRuntimeStatus
import com.yaoxincs.portforwarding.settings.SshCredentialStore
import com.yaoxincs.portforwarding.settings.SshProjectPortForwardingSettings
import com.yaoxincs.portforwarding.settings.SshPortForwardingSettings
import com.yaoxincs.portforwarding.ui.SshPortForwardingUiSupport
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder
import net.schmizz.sshj.connection.channel.forwarded.SocketForwardingConnectListener

@Service(Service.Level.APP)
class SshTunnelManager : Disposable {

    private val settings = ApplicationManager.getApplication().getService(SshPortForwardingSettings::class.java)
    private val credentialStore = ApplicationManager.getApplication().getService(SshCredentialStore::class.java)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(PortForwardingBundle.message("runtime.executor.name"), 6)
    private val tunnels = ConcurrentHashMap<String, ManagedTunnel>()

    @Volatile
    private var autoStartInvoked = false
    private val projectAutoStartInvoked = ConcurrentHashMap.newKeySet<String>()

    init {
        settings.addChangeListener(this) { reconcileWithPersistedState() }
    }

    fun addChangeListener(parent: Disposable, listener: () -> Unit) {
        listeners += listener
        Disposer.register(parent) { listeners.remove(listener) }
    }

    fun stateFor(ruleId: String): TunnelRuntimeState = tunnels[ruleId]?.runtimeState ?: TunnelRuntimeState(ruleId)

    fun start(connection: SshConnectionState, rule: PortForwardRuleState, project: Project? = null) {
        val normalizedConnection = normalizedConnection(connection)
        val normalizedRule = normalizedRule(rule)
        validateConnectionForSave(normalizedConnection)?.let { error(it) }
        validateRule(normalizedRule)?.let { error(it) }

        val managed = tunnels.computeIfAbsent(rule.id) { ManagedTunnel(rule.id) }
        synchronized(managed.lock) {
            managed.connection = normalizedConnection
            managed.rule = normalizedRule
            managed.ownerProjectKey = projectKey(project, normalizedConnection.projectScoped)
            managed.desiredRunning = true
            managed.stopRequested = false
            if (managed.worker == null || managed.worker?.isDone == true) {
                managed.worker = executor.submit { runTunnel(managed) }
            } else if (managed.runtimeState.status == TunnelRuntimeStatus.ERROR || managed.runtimeState.status == TunnelRuntimeStatus.STOPPED) {
                managed.restartRequested = true
                closeTransport(managed)
            }
        }
        publishState(rule.id, TunnelRuntimeStatus.STARTING, PortForwardingBundle.message("runtime.starting"))
    }

    fun restart(connection: SshConnectionState, rule: PortForwardRuleState, project: Project? = null) {
        val managed = tunnels.computeIfAbsent(rule.id) { ManagedTunnel(rule.id) }
        synchronized(managed.lock) {
            managed.connection = normalizedConnection(connection)
            managed.rule = normalizedRule(rule)
            managed.ownerProjectKey = projectKey(project, normalizedConnection(connection).projectScoped)
            managed.desiredRunning = true
            managed.restartRequested = true
            managed.stopRequested = false
            if (managed.worker == null || managed.worker?.isDone == true) {
                managed.worker = executor.submit { runTunnel(managed) }
            } else {
                closeTransport(managed)
            }
        }
        publishState(rule.id, TunnelRuntimeStatus.STARTING, PortForwardingBundle.message("runtime.applying"))
    }

    fun updateConfiguration(connection: SshConnectionState, rule: PortForwardRuleState, project: Project? = null) {
        tunnels[rule.id]?.let { managed ->
            synchronized(managed.lock) {
                managed.connection = normalizedConnection(connection)
                managed.rule = normalizedRule(rule)
                managed.ownerProjectKey = projectKey(project, normalizedConnection(connection).projectScoped)
            }
        }
        notifyListeners()
    }

    fun stop(ruleId: String) {
        val managed = tunnels[ruleId] ?: return
        synchronized(managed.lock) {
            managed.desiredRunning = false
            managed.restartRequested = false
            managed.stopRequested = true
        }
        publishState(ruleId, TunnelRuntimeStatus.STOPPING, PortForwardingBundle.message("runtime.stopping"))
        closeTransport(managed)
    }

    fun stopConnection(connectionId: String) {
        tunnels.values.filter { it.connection?.id == connectionId }.forEach { stop(it.ruleId) }
    }

    fun removeRule(ruleId: String) {
        stop(ruleId)
        tunnels.remove(ruleId)
        notifyListeners()
    }

    fun startAll(connection: SshConnectionState, project: Project? = null) {
        val normalized = normalizedConnection(connection)
        normalized.portForwardRules
            .filter { rule ->
                val status = stateFor(rule.id).status
                status == TunnelRuntimeStatus.STOPPED || status == TunnelRuntimeStatus.ERROR
            }
            .forEach { rule -> start(normalized, rule, project) }
    }

    fun stopAll(connectionId: String) {
        stopConnection(connectionId)
    }

    fun startAutoConfiguredTunnels() {
        if (autoStartInvoked) return
        autoStartInvoked = true
        val snapshot = credentialStore.inflateSecrets(settings.stateSnapshot())
        snapshot.connections.forEach { connection ->
            connection.portForwardRules
                .filter { it.autoStart }
                .forEach { rule ->
                    try {
                        start(connection, rule)
                    } catch (t: Throwable) {
                        publishState(rule.id, TunnelRuntimeStatus.ERROR, t.message ?: PortForwardingBundle.message("runtime.autostart.failed"))
                    }
                }
        }
    }

    fun startAutoConfiguredTunnels(project: Project) {
        val projectKey = project.locationHash.takeIf(String::isNotBlank) ?: project.name
        if (!projectAutoStartInvoked.add(projectKey)) return

        val snapshot = credentialStore.inflateSecrets(project.getService(SshProjectPortForwardingSettings::class.java).stateSnapshot())
        snapshot.connections
            .map { it.copy(projectScoped = true) }
            .forEach { connection ->
                connection.portForwardRules
                    .filter { it.autoStart }
                    .forEach { rule ->
                        try {
                            start(connection, rule, project)
                        } catch (t: Throwable) {
                            publishState(rule.id, TunnelRuntimeStatus.ERROR, t.message ?: PortForwardingBundle.message("runtime.project.autostart.failed"))
                        }
                    }
            }
    }

    fun stopProjectScopedTunnels(project: Project) {
        val projectKey = project.locationHash.takeIf(String::isNotBlank) ?: project.name
        tunnels.values
            .filter { it.ownerProjectKey == projectKey }
            .forEach { stop(it.ruleId) }
        projectAutoStartInvoked.remove(projectKey)
    }

    override fun dispose() {
        tunnels.values.forEach { managed ->
            synchronized(managed.lock) {
                managed.desiredRunning = false
                managed.stopRequested = true
                managed.restartRequested = false
            }
            closeTransport(managed)
        }
        executor.shutdownNow()
    }

    private fun reconcileWithPersistedState() {
        val ruleIds = buildSet {
            addAll(settings.stateSnapshot().connections.flatMap { connection -> connection.portForwardRules.map { it.id } })
            ProjectManager.getInstance().openProjects.forEach { project ->
                addAll(project.getService(SshProjectPortForwardingSettings::class.java).stateSnapshot().connections.flatMap { connection ->
                    connection.portForwardRules.map { it.id }
                })
            }
        }
        tunnels.keys
            .filterNot(ruleIds::contains)
            .forEach(::removeRule)
    }

    private fun runTunnel(managed: ManagedTunnel) {
        while (true) {
            val connection = synchronized(managed.lock) { managed.connection }
            val rule = synchronized(managed.lock) { managed.rule }
            if (connection == null || rule == null || !managed.desiredRunning) {
                publishState(managed.ruleId, TunnelRuntimeStatus.STOPPED, "")
                return
            }
            managed.restartRequested = false

            try {
                if (connection.authenticationType == SshAuthenticationType.OPENSSH_AGENT) {
                    runOpenSshTunnel(managed, connection, rule)
                } else {
                    runSshjTunnel(managed, connection, rule)
                }

                if (!managed.desiredRunning || managed.stopRequested) {
                    publishState(managed.ruleId, TunnelRuntimeStatus.STOPPED, "")
                    return
                }

                if (!rule.autoReconnect && !managed.restartRequested) {
                    val message = PortForwardingBundle.message("runtime.stopped.unexpected")
                    publishState(managed.ruleId, TunnelRuntimeStatus.ERROR, message)
                    notifyError(connection, rule, message)
                    managed.desiredRunning = false
                    return
                }
            } catch (t: Throwable) {
                if (managed.stopRequested || !managed.desiredRunning) {
                    publishState(managed.ruleId, TunnelRuntimeStatus.STOPPED, "")
                    return
                }
                val message = t.message ?: PortForwardingBundle.message("runtime.failed")
                if (!rule.autoReconnect && !managed.restartRequested) {
                    publishState(managed.ruleId, TunnelRuntimeStatus.ERROR, message)
                    notifyError(connection, rule, message)
                    managed.desiredRunning = false
                    return
                }
                publishState(managed.ruleId, TunnelRuntimeStatus.RECONNECTING, PortForwardingBundle.message("runtime.reconnecting", message))
            } finally {
                closeTransport(managed)
            }

            if (!managed.desiredRunning) {
                publishState(managed.ruleId, TunnelRuntimeStatus.STOPPED, "")
                return
            }
            sleepBeforeRetry(managed)
        }
    }

    private fun runSshjTunnel(managed: ManagedTunnel, connection: SshConnectionState, rule: PortForwardRuleState) {
        val client = createAuthenticatedClient(connection)
        managed.client = client
        when (rule.direction) {
            PortForwardDirection.LOCAL_TO_REMOTE -> {
                val serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(rule.bindAddress, rule.sourcePort))
                }
                managed.serverSocket = serverSocket
                val forwarder = client.newLocalPortForwarder(
                    Parameters(rule.destinationHost, rule.destinationPort, rule.bindAddress, rule.sourcePort),
                    serverSocket,
                )
                managed.localForwarder = forwarder
                publishState(managed.ruleId, TunnelRuntimeStatus.RUNNING, PortForwardingBundle.message("runtime.listening", rule.bindAddress, rule.sourcePort.toString()))
                forwarder.listen()
                if (managed.desiredRunning && !managed.stopRequested) {
                    error(PortForwardingBundle.message("runtime.local.listener.stopped"))
                }
            }

            PortForwardDirection.REMOTE_TO_LOCAL -> {
                val listener = SocketForwardingConnectListener(InetSocketAddress(rule.destinationHost, rule.destinationPort))
                val forward = client.remotePortForwarder.bind(RemotePortForwarder.Forward(rule.bindAddress, rule.sourcePort), listener)
                managed.remoteForward = forward
                publishState(managed.ruleId, TunnelRuntimeStatus.RUNNING, PortForwardingBundle.message("runtime.remote.port.active", forward.port.toString()))
                while (managed.desiredRunning && !managed.stopRequested && client.isConnected) {
                    Thread.sleep(500)
                }
                if (managed.desiredRunning && !managed.stopRequested) {
                    error(PortForwardingBundle.message("runtime.session.disconnected"))
                }
            }
        }
    }

    private fun runOpenSshTunnel(managed: ManagedTunnel, connection: SshConnectionState, rule: PortForwardRuleState) {
        val process = ProcessBuilder(buildSystemSshTunnelCommand(connection, rule))
            .redirectErrorStream(true)
            .start()
        managed.process = process
        val outputBuffer = StringBuilder()
        val outputFuture = executor.submit {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    synchronized(outputBuffer) {
                        if (outputBuffer.length < 4000) {
                            if (outputBuffer.isNotEmpty()) outputBuffer.appendLine()
                            outputBuffer.append(line)
                        }
                    }
                }
            }
        }
        publishState(managed.ruleId, TunnelRuntimeStatus.RUNNING, PortForwardingBundle.message("runtime.system.tunnel.running"))
        while (managed.desiredRunning && !managed.stopRequested) {
            if (process.waitFor(500, TimeUnit.MILLISECONDS)) {
                break
            }
        }
        if (managed.stopRequested || !managed.desiredRunning) {
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
            }
        }
        outputFuture.get(3, TimeUnit.SECONDS)
        if (managed.desiredRunning && !managed.stopRequested) {
            val output = synchronized(outputBuffer) { outputBuffer.toString().trim() }
            val exitCode = process.exitValue()
            error(output.ifBlank { PortForwardingBundle.message("runtime.ssh.exit.code", exitCode.toString()) })
        }
    }

    private fun closeTransport(managed: ManagedTunnel) {
        try {
            managed.localForwarder?.close()
        } catch (_: Throwable) {
        } finally {
            managed.localForwarder = null
        }

        try {
            managed.serverSocket?.close()
        } catch (_: Throwable) {
        } finally {
            managed.serverSocket = null
        }

        try {
            managed.remoteForward?.let { forward ->
                managed.client?.remotePortForwarder?.cancel(forward)
            }
        } catch (_: Throwable) {
        } finally {
            managed.remoteForward = null
        }

        try {
            managed.process?.let { process ->
                if (process.isAlive) {
                    process.destroy()
                    if (!process.waitFor(2, TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                    }
                }
            }
        } catch (_: Throwable) {
        } finally {
            managed.process = null
        }

        try {
            managed.client?.close()
        } catch (_: Throwable) {
        } finally {
            managed.client = null
        }
    }

    private fun sleepBeforeRetry(managed: ManagedTunnel) {
        repeat(6) {
            if (!managed.desiredRunning || managed.stopRequested) return
            Thread.sleep(500)
        }
    }

    private fun publishState(ruleId: String, status: TunnelRuntimeStatus, message: String) {
        tunnels.computeIfAbsent(ruleId) { ManagedTunnel(ruleId) }.runtimeState = TunnelRuntimeState(ruleId, status, message)
        notifyListeners()
    }

    private fun notifyListeners() {
        ApplicationManager.getApplication().invokeLater(
            {
                listeners.forEach { it() }
            },
            ModalityState.any(),
        )
    }

    private fun notifyError(connection: SshConnectionState, rule: PortForwardRuleState, message: String) {
        val tunnelName = rule.name.ifBlank { "${rule.bindAddress}:${rule.sourcePort}" }
        SshPortForwardingUiSupport.notify(
            null,
            PortForwardingBundle.message("notification.tunnel.failed.title", connection.userName, connection.host),
            "$tunnelName: $message",
            com.intellij.notification.NotificationType.ERROR,
        )
    }

    private fun projectKey(project: Project?, projectScoped: Boolean): String? {
        if (!projectScoped || project == null) return null
        return project.locationHash.takeIf(String::isNotBlank) ?: project.name
    }

    private class ManagedTunnel(val ruleId: String) {
        val lock = Any()

        @Volatile
        var connection: SshConnectionState? = null

        @Volatile
        var rule: PortForwardRuleState? = null

        @Volatile
        var desiredRunning = false

        @Volatile
        var stopRequested = false

        @Volatile
        var restartRequested = false

        @Volatile
        var worker: Future<*>? = null

        @Volatile
        var client: SSHClient? = null

        @Volatile
        var localForwarder: LocalPortForwarder? = null

        @Volatile
        var serverSocket: ServerSocket? = null

        @Volatile
        var remoteForward: RemotePortForwarder.Forward? = null

        @Volatile
        var process: Process? = null

        @Volatile
        var runtimeState: TunnelRuntimeState = TunnelRuntimeState(ruleId)

        @Volatile
        var ownerProjectKey: String? = null
    }
}
