package com.yaoxincs.portforwarding.settings

import com.yaoxincs.portforwarding.model.SshAuthenticationType
import com.yaoxincs.portforwarding.model.SshConnectionState
import com.yaoxincs.portforwarding.model.SshPortForwardingState
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service

@Service(Service.Level.APP)
class SshCredentialStore {

    fun inflateSecrets(state: SshPortForwardingState): SshPortForwardingState {
        return state.copy(
            connections = state.connections.map(::inflateSecrets),
        )
    }

    fun persistSecrets(previousState: SshPortForwardingState, draftState: SshPortForwardingState) {
        val previousById = previousState.connections.associateBy { it.id }
        val nextById = draftState.connections.associateBy { it.id }

        (previousById.keys - nextById.keys).forEach { id -> clearSecrets(previousById.getValue(id)) }
        nextById.forEach { (id, connection) ->
            previousById[id]?.takeIf { credentialKey(it) != credentialKey(connection) }?.let(::clearSecrets)
            persistSecrets(connection)
        }
    }

    private fun inflateSecrets(connection: SshConnectionState): SshConnectionState {
        val attributes = credentialAttributes(connection, memoryOnly = false)
        val credentials = PasswordSafe.instance.get(attributes)
        if (credentials == null) {
            return connection.copy(
                password = "",
                passphrase = "",
            )
        }

        val secret = credentials.getPasswordAsString().orEmpty()
        return when (connection.authenticationType) {
            SshAuthenticationType.PASSWORD -> connection.copy(
                password = secret,
                passphrase = "",
            )
            SshAuthenticationType.KEY_PAIR -> connection.copy(
                password = "",
                passphrase = secret,
            )
            SshAuthenticationType.OPENSSH_AGENT -> connection.copy(
                password = "",
                passphrase = "",
                useOpenSshConfig = true,
            )
        }
    }

    private fun persistSecrets(connection: SshConnectionState) {
        val secret = when (connection.authenticationType) {
            SshAuthenticationType.PASSWORD -> connection.password
            SshAuthenticationType.KEY_PAIR -> connection.passphrase
            SshAuthenticationType.OPENSSH_AGENT -> return
        }
        PasswordSafe.instance.set(
            credentialAttributes(connection, memoryOnly = false),
            Credentials(connection.userName.trim().ifEmpty { null }, secret),
        )
    }

    private fun clearSecrets(connection: SshConnectionState) {
        SshAuthenticationType.entries.forEach { authType ->
            listOf(false, true).forEach { memoryOnly ->
                PasswordSafe.instance.set(
                    credentialAttributes(connection.copy(authenticationType = authType), memoryOnly),
                    null,
                )
            }
        }
    }

    private fun credentialKey(connection: SshConnectionState): String =
        "${connection.userName.trim()}@${connection.host.trim()}:${connection.port}:${connection.authenticationType.name}"

    private fun credentialAttributes(connection: SshConnectionState, memoryOnly: Boolean): CredentialAttributes {
        val qualifier = when (connection.authenticationType) {
            SshAuthenticationType.PASSWORD -> "password"
            SshAuthenticationType.KEY_PAIR -> "passphrase"
            SshAuthenticationType.OPENSSH_AGENT -> "empty"
        }
        val serviceName = "IntelliJ Platform Remote Credentials ssh://${connection.userName.trim()}@${connection.host.trim()}:${connection.port}($qualifier)"
        return CredentialAttributes(serviceName, connection.userName.trim().ifEmpty { null }, memoryOnly)
    }

}
