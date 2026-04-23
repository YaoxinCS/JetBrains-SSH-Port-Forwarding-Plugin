package com.yaoxincs.portforwarding.model

import com.yaoxincs.portforwarding.PortForwardingBundle
import java.util.UUID

enum class SshAuthenticationType(private val bundleKey: String) {
    PASSWORD("enum.auth.password"),
    KEY_PAIR("enum.auth.key.pair"),
    OPENSSH_AGENT("enum.auth.openssh.agent");

    override fun toString(): String = PortForwardingBundle.message(bundleKey)
}

enum class StrictHostKeyCheckingMode(
    private val bundleKey: String,
    val sshOptionValue: String,
) {
    ASK("enum.host.key.ask", "ask"),
    YES("enum.host.key.yes", "yes"),
    NO("enum.host.key.no", "no");

    override fun toString(): String = PortForwardingBundle.message(bundleKey)
}

enum class PortForwardDirection(private val bundleKey: String, val sshFlag: String) {
    LOCAL_TO_REMOTE("enum.direction.local.to.remote", "-L"),
    REMOTE_TO_LOCAL("enum.direction.remote.to.local", "-R");

    override fun toString(): String = PortForwardingBundle.message(bundleKey)
}

data class PortForwardRuleState(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val direction: PortForwardDirection = PortForwardDirection.LOCAL_TO_REMOTE,
    val bindAddress: String = "127.0.0.1",
    val sourcePort: Int = 8080,
    val destinationHost: String = "127.0.0.1",
    val destinationPort: Int = 80,
    val autoStart: Boolean = false,
    val autoReconnect: Boolean = false,
)

data class SshConnectionState(
    val id: String = UUID.randomUUID().toString(),
    val projectScoped: Boolean = false,
    val host: String = "",
    val port: Int = 22,
    val userName: String = "",
    val localPort: String = "",
    val authenticationType: SshAuthenticationType = SshAuthenticationType.PASSWORD,
    val password: String = "",
    val savePassword: Boolean = false,
    val privateKeyPath: String = "",
    val passphrase: String = "",
    val savePassphrase: Boolean = false,
    val useOpenSshConfig: Boolean = true,
    val sendKeepAliveMessages: Boolean = true,
    val keepAliveIntervalSeconds: Int = 300,
    val strictHostKeyCheckingEnabled: Boolean = false,
    val strictHostKeyCheckingMode: StrictHostKeyCheckingMode = StrictHostKeyCheckingMode.ASK,
    val hashKnownHosts: Boolean = false,
    val portForwardRules: List<PortForwardRuleState> = emptyList(),
)

data class SshPortForwardingState(
    val connections: List<SshConnectionState> = emptyList(),
)
