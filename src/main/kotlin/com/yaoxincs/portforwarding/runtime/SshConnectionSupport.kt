package com.yaoxincs.portforwarding.runtime

import com.yaoxincs.portforwarding.PortForwardingBundle
import com.yaoxincs.portforwarding.model.PortForwardDirection
import com.yaoxincs.portforwarding.model.PortForwardRuleState
import com.yaoxincs.portforwarding.model.SshAuthenticationType
import com.yaoxincs.portforwarding.model.SshConnectionState
import com.yaoxincs.portforwarding.model.StrictHostKeyCheckingMode
import com.intellij.notification.NotificationType
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier

data class ConnectionTestResult(
    val title: String,
    val content: String,
    val type: NotificationType,
)

fun normalizedConnection(connection: SshConnectionState): SshConnectionState =
    connection.copy(
        host = connection.host.trim(),
        userName = connection.userName.trim(),
        privateKeyPath = connection.privateKeyPath.trim(),
        portForwardRules = connection.portForwardRules.map(::normalizedRule),
    )

fun normalizedRule(rule: PortForwardRuleState): PortForwardRuleState =
    rule.copy(
        name = rule.name.trim(),
        bindAddress = rule.bindAddress.trim(),
        destinationHost = rule.destinationHost.trim(),
    )

fun validateConnectionForSave(connection: SshConnectionState): String? {
    if (connection.host.isBlank()) return PortForwardingBundle.message("validation.host.required")
    if (connection.userName.isBlank()) return PortForwardingBundle.message("validation.user.name.required")
    if (connection.authenticationType == SshAuthenticationType.KEY_PAIR && connection.privateKeyPath.endsWith(".pub", ignoreCase = true)) {
        return PortForwardingBundle.message("validation.private.key.pub")
    }
    return null
}

fun validateConnectionForTest(connection: SshConnectionState): String? {
    validateConnectionForSave(connection)?.let { return it }
    return when (connection.authenticationType) {
        SshAuthenticationType.PASSWORD ->
            if (connection.password.isBlank()) PortForwardingBundle.message("validation.password.required") else null

        SshAuthenticationType.KEY_PAIR ->
            if (connection.privateKeyPath.isBlank()) PortForwardingBundle.message("validation.private.key.required") else null

        SshAuthenticationType.OPENSSH_AGENT -> null
    }
}

fun validateRule(rule: PortForwardRuleState): String? {
    if (rule.bindAddress.isBlank()) return PortForwardingBundle.message("validation.bind.address.required")
    if (rule.destinationHost.isBlank()) return PortForwardingBundle.message("validation.destination.host.required")
    if (rule.sourcePort !in 1..65535) return PortForwardingBundle.message("validation.forward.port.range")
    if (rule.destinationPort !in 1..65535) return PortForwardingBundle.message("validation.destination.port.range")
    return null
}

fun testConnection(connection: SshConnectionState): ConnectionTestResult {
    validateConnectionForTest(connection)?.let {
        return ConnectionTestResult(PortForwardingBundle.message("test.connection.title"), it, NotificationType.WARNING)
    }
    val normalized = normalizedConnection(connection)
    return if (normalized.authenticationType == SshAuthenticationType.OPENSSH_AGENT) {
        testConnectionWithSystemSsh(normalized)
    } else {
        SSHClient().use { client ->
            configureClient(client, normalized)
            client.connect(normalized.host, normalized.port)
            authenticateClient(client, normalized)
            ConnectionTestResult(
                title = PortForwardingBundle.message("test.connection.success.title"),
                content = PortForwardingBundle.message("test.connection.success.content", normalized.host, normalized.port.toString()),
                type = NotificationType.INFORMATION,
            )
        }
    }
}

fun createAuthenticatedClient(connection: SshConnectionState): SSHClient {
    val normalized = normalizedConnection(connection)
    val client = SSHClient()
    try {
        configureClient(client, normalized)
        client.connect(normalized.host, normalized.port)
        authenticateClient(client, normalized)
        return client
    } catch (t: Throwable) {
        try {
            client.close()
        } catch (_: Throwable) {
        }
        throw t
    }
}

fun buildSystemSshTunnelCommand(connection: SshConnectionState, rule: PortForwardRuleState): List<String> {
    val normalizedConnection = normalizedConnection(connection)
    val normalizedRule = normalizedRule(rule)
    val destination = "${normalizedConnection.userName}@${normalizedConnection.host}"
    val forwardTarget =
        "${normalizedRule.bindAddress}:${normalizedRule.sourcePort}:${normalizedRule.destinationHost}:${normalizedRule.destinationPort}"
    return buildSystemSshBaseCommand(normalizedConnection).toMutableList().apply {
        add("-N")
        add("-o")
        add("ExitOnForwardFailure=yes")
        add(if (normalizedRule.direction == PortForwardDirection.LOCAL_TO_REMOTE) "-L" else "-R")
        add(forwardTarget)
        add(destination)
    }
}

fun localBindAddressOptions(currentValue: String, includeInterfaces: Boolean): List<String> {
    val values = linkedSetOf<String>()
    values += "0.0.0.0"
    values += "127.0.0.1"
    if (currentValue.isNotBlank()) {
        values += currentValue.trim()
    }
    if (includeInterfaces) {
        val addresses = java.net.NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .mapNotNull { address ->
                address.hostAddress
                    ?.substringBefore('%')
                    ?.takeIf { '.' in it }
            }
            .sorted()
        values += addresses
    }
    return values.toList()
}

private fun configureClient(client: SSHClient, connection: SshConnectionState) {
    if (!connection.strictHostKeyCheckingEnabled || connection.strictHostKeyCheckingMode == StrictHostKeyCheckingMode.NO) {
        client.addHostKeyVerifier(PromiscuousVerifier())
    } else {
        val knownHosts = resolveKnownHostsFile()
        if (knownHosts.exists()) {
            client.loadKnownHosts(knownHosts)
        } else {
            client.loadKnownHosts()
        }
    }
}

private fun resolveKnownHostsFile(): File {
    val home = System.getProperty("user.home").orEmpty()
    return File(home, ".ssh/known_hosts")
}

private fun authenticateClient(client: SSHClient, connection: SshConnectionState) {
    when (connection.authenticationType) {
        SshAuthenticationType.PASSWORD -> client.authPassword(connection.userName, connection.password)
        SshAuthenticationType.KEY_PAIR -> {
            val keyProvider = if (connection.passphrase.isBlank()) {
                client.loadKeys(connection.privateKeyPath)
            } else {
                client.loadKeys(connection.privateKeyPath, connection.passphrase)
            }
            client.authPublickey(connection.userName, keyProvider)
        }

        SshAuthenticationType.OPENSSH_AGENT -> error(PortForwardingBundle.message("runtime.openssh.agent.delegated"))
    }
    if (connection.sendKeepAliveMessages) {
        client.connection.keepAlive.keepAliveInterval = connection.keepAliveIntervalSeconds
    }
}

private fun testConnectionWithSystemSsh(connection: SshConnectionState): ConnectionTestResult {
    val command = buildSystemSshBaseCommand(connection).toMutableList().apply {
        add("${connection.userName}@${connection.host}")
        add("exit")
    }
    return try {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val completed = process.waitFor(12, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = if (completed) process.exitValue() else {
            process.destroyForcibly()
            -1
        }
        val success = completed && exitCode == 0
        ConnectionTestResult(
            title = if (success) {
                PortForwardingBundle.message("test.connection.success.title")
            } else {
                PortForwardingBundle.message("test.connection.failed.title")
            },
            content = when {
                success -> PortForwardingBundle.message("test.connection.success.content", connection.host, connection.port.toString())
                !completed -> PortForwardingBundle.message("test.connection.timeout.content", connection.host, connection.port.toString())
                output.isNotBlank() -> output
                else -> PortForwardingBundle.message("test.connection.exit.code", exitCode.toString())
            },
            type = if (success) NotificationType.INFORMATION else NotificationType.ERROR,
        )
    } catch (e: IOException) {
        ConnectionTestResult(
            PortForwardingBundle.message("test.connection.failed.title"),
            e.message ?: PortForwardingBundle.message("test.connection.run.failed"),
            NotificationType.ERROR,
        )
    }
}

private fun buildSystemSshBaseCommand(connection: SshConnectionState): List<String> = buildList {
    add("ssh")
    add("-p")
    add(connection.port.toString())
    add("-o")
    add("BatchMode=yes")
    add("-o")
    add("ConnectTimeout=8")
    if (connection.sendKeepAliveMessages) {
        add("-o")
        add("ServerAliveInterval=${connection.keepAliveIntervalSeconds}")
    }
    if (connection.strictHostKeyCheckingEnabled) {
        add("-o")
        add("StrictHostKeyChecking=${connection.strictHostKeyCheckingMode.sshOptionValue}")
    } else {
        add("-o")
        add("StrictHostKeyChecking=no")
    }
    if (!connection.useOpenSshConfig) {
        add("-F")
        add(if (SystemInfo.isWindows) "NUL" else "/dev/null")
    }
    if (connection.authenticationType == SshAuthenticationType.KEY_PAIR && connection.privateKeyPath.isNotBlank()) {
        add("-i")
        add(connection.privateKeyPath)
    }
}
