package com.yaoxincs.portforwarding.settings

import com.yaoxincs.portforwarding.model.PortForwardDirection
import com.yaoxincs.portforwarding.model.PortForwardRuleState
import com.yaoxincs.portforwarding.model.SshAuthenticationType
import com.yaoxincs.portforwarding.model.SshConnectionState
import com.yaoxincs.portforwarding.model.SshPortForwardingState
import com.yaoxincs.portforwarding.model.StrictHostKeyCheckingMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SshPortForwardingStorageCodecTest {

    @Test
    fun `round trips sessions and tunnels without writing secrets`() {
        val connection = SshConnectionState(
            id = "session-1",
            projectScoped = true,
            host = "server.example.com",
            port = 22022,
            userName = "deploy",
            localPort = "legacy-local-port",
            authenticationType = SshAuthenticationType.KEY_PAIR,
            password = "secret-password",
            privateKeyPath = "C:/Users/me/.ssh/id_rsa",
            passphrase = "secret-passphrase",
            useOpenSshConfig = false,
            sendKeepAliveMessages = false,
            keepAliveIntervalSeconds = 60,
            strictHostKeyCheckingEnabled = true,
            strictHostKeyCheckingMode = StrictHostKeyCheckingMode.YES,
            hashKnownHosts = true,
            portForwardRules = listOf(
                PortForwardRuleState(
                    id = "rule-1",
                    name = "db & api",
                    direction = PortForwardDirection.REMOTE_TO_LOCAL,
                    bindAddress = "0.0.0.0",
                    sourcePort = 15432,
                    destinationHost = "127.0.0.1",
                    destinationPort = 5432,
                    autoStart = true,
                    autoReconnect = true,
                ),
            ),
        )
        val state = SshPortForwardingState(connections = listOf(connection))

        val output = ByteArrayOutputStream()
        SshPortForwardingStorageCodec.write(state, output)
        val xml = output.toString(Charsets.UTF_8)

        assertFalse(xml.contains("secret-password"))
        assertFalse(xml.contains("secret-passphrase"))
        assertFalse(xml.contains("savePassword"))
        assertFalse(xml.contains("savePassphrase"))
        assertEquals(
            state.copy(connections = listOf(connection.copy(password = "", passphrase = ""))),
            SshPortForwardingStorageCodec.read(ByteArrayInputStream(output.toByteArray())),
        )
    }
}
