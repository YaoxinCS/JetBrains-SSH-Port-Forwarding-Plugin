package com.yaoxincs.portforwarding.settings

import com.yaoxincs.portforwarding.model.PortForwardDirection
import com.yaoxincs.portforwarding.model.PortForwardRuleState
import com.yaoxincs.portforwarding.model.SshAuthenticationType
import com.yaoxincs.portforwarding.model.SshConnectionState
import com.yaoxincs.portforwarding.model.SshPortForwardingState
import com.yaoxincs.portforwarding.model.StrictHostKeyCheckingMode
import java.io.InputStream
import java.io.OutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element

object SshPortForwardingStorageCodec {

    private const val ROOT_ELEMENT = "ssh-port-forwarding"
    private const val CONNECTIONS_ELEMENT = "connections"
    private const val CONNECTION_ELEMENT = "connection"
    private const val RULES_ELEMENT = "rules"
    private const val RULE_ELEMENT = "rule"

    fun read(input: InputStream): SshPortForwardingState {
        val document = documentBuilderFactory().newDocumentBuilder().parse(input)
        val root = document.documentElement ?: return SshPortForwardingState()
        if (root.tagName != ROOT_ELEMENT) return SshPortForwardingState()

        val connectionContainer = root.directChild(CONNECTIONS_ELEMENT) ?: root
        return SshPortForwardingState(
            connections = connectionContainer.directChildren(CONNECTION_ELEMENT).map(::readConnection),
        )
    }

    fun write(state: SshPortForwardingState, output: OutputStream) {
        val document = documentBuilderFactory().newDocumentBuilder().newDocument()
        val root = document.createElement(ROOT_ELEMENT).also { element ->
            element.setAttribute("version", "1")
            document.appendChild(element)
        }
        val connectionsElement = document.createElement(CONNECTIONS_ELEMENT).also(root::appendChild)

        state.connections.forEach { connection ->
            val connectionElement = document.createElement(CONNECTION_ELEMENT).also(connectionsElement::appendChild)
            connectionElement.setAttribute("id", connection.id)
            connectionElement.setAttribute("projectScoped", connection.projectScoped.toString())
            connectionElement.setAttribute("host", connection.host)
            connectionElement.setAttribute("port", connection.port.toString())
            connectionElement.setAttribute("userName", connection.userName)
            connectionElement.setAttribute("localPort", connection.localPort)
            connectionElement.setAttribute("authenticationType", connection.authenticationType.name)
            connectionElement.setAttribute("privateKeyPath", connection.privateKeyPath)
            connectionElement.setAttribute("useOpenSshConfig", connection.useOpenSshConfig.toString())
            connectionElement.setAttribute("sendKeepAliveMessages", connection.sendKeepAliveMessages.toString())
            connectionElement.setAttribute("keepAliveIntervalSeconds", connection.keepAliveIntervalSeconds.toString())
            connectionElement.setAttribute("strictHostKeyCheckingEnabled", connection.strictHostKeyCheckingEnabled.toString())
            connectionElement.setAttribute("strictHostKeyCheckingMode", connection.strictHostKeyCheckingMode.name)
            connectionElement.setAttribute("hashKnownHosts", connection.hashKnownHosts.toString())

            val rulesElement = document.createElement(RULES_ELEMENT).also(connectionElement::appendChild)
            connection.portForwardRules.forEach { rule ->
                val ruleElement = document.createElement(RULE_ELEMENT).also(rulesElement::appendChild)
                ruleElement.setAttribute("id", rule.id)
                ruleElement.setAttribute("name", rule.name)
                ruleElement.setAttribute("direction", rule.direction.name)
                ruleElement.setAttribute("bindAddress", rule.bindAddress)
                ruleElement.setAttribute("sourcePort", rule.sourcePort.toString())
                ruleElement.setAttribute("destinationHost", rule.destinationHost)
                ruleElement.setAttribute("destinationPort", rule.destinationPort.toString())
                ruleElement.setAttribute("autoStart", rule.autoStart.toString())
                ruleElement.setAttribute("autoReconnect", rule.autoReconnect.toString())
            }
        }

        val transformerFactory = TransformerFactory.newInstance().apply {
            trySetFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }
        transformerFactory.newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        }.transform(DOMSource(document), StreamResult(output))
    }

    private fun readConnection(element: Element): SshConnectionState =
        SshConnectionState(
            id = element.stringAttribute("id", SshConnectionState().id),
            projectScoped = element.booleanAttribute("projectScoped", false),
            host = element.stringAttribute("host"),
            port = element.intAttribute("port", 22),
            userName = element.stringAttribute("userName"),
            localPort = element.stringAttribute("localPort"),
            authenticationType = element.enumAttribute("authenticationType", SshAuthenticationType.PASSWORD),
            password = "",
            privateKeyPath = element.stringAttribute("privateKeyPath"),
            passphrase = "",
            useOpenSshConfig = element.booleanAttribute("useOpenSshConfig", true),
            sendKeepAliveMessages = element.booleanAttribute("sendKeepAliveMessages", true),
            keepAliveIntervalSeconds = element.intAttribute("keepAliveIntervalSeconds", 300),
            strictHostKeyCheckingEnabled = element.booleanAttribute("strictHostKeyCheckingEnabled", false),
            strictHostKeyCheckingMode = element.enumAttribute("strictHostKeyCheckingMode", StrictHostKeyCheckingMode.ASK),
            hashKnownHosts = element.booleanAttribute("hashKnownHosts", false),
            portForwardRules = element.directChild(RULES_ELEMENT)?.directChildren(RULE_ELEMENT).orEmpty().map(::readRule),
        )

    private fun readRule(element: Element): PortForwardRuleState =
        PortForwardRuleState(
            id = element.stringAttribute("id", PortForwardRuleState().id),
            name = element.stringAttribute("name"),
            direction = element.enumAttribute("direction", PortForwardDirection.LOCAL_TO_REMOTE),
            bindAddress = element.stringAttribute("bindAddress", "127.0.0.1"),
            sourcePort = element.intAttribute("sourcePort", 8080),
            destinationHost = element.stringAttribute("destinationHost", "127.0.0.1"),
            destinationPort = element.intAttribute("destinationPort", 80),
            autoStart = element.booleanAttribute("autoStart", false),
            autoReconnect = element.booleanAttribute("autoReconnect", false),
        )

    private fun documentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            trySetFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            trySetFeature("http://xml.org/sax/features/external-general-entities", false)
            trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }

    private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun TransformerFactory.trySetFeature(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun Element.directChild(name: String): Element? =
        directChildren(name).firstOrNull()

    private fun Element.directChildren(name: String): List<Element> =
        (0 until childNodes.length)
            .mapNotNull { index -> childNodes.item(index) as? Element }
            .filter { it.tagName == name }

    private fun Element.stringAttribute(name: String, defaultValue: String = ""): String =
        getAttribute(name).takeIf { hasAttribute(name) } ?: defaultValue

    private fun Element.intAttribute(name: String, defaultValue: Int): Int =
        stringAttribute(name).toIntOrNull() ?: defaultValue

    private fun Element.booleanAttribute(name: String, defaultValue: Boolean): Boolean {
        val value = stringAttribute(name)
        return when {
            value.equals("true", ignoreCase = true) -> true
            value.equals("false", ignoreCase = true) -> false
            else -> defaultValue
        }
    }

    private inline fun <reified T : Enum<T>> Element.enumAttribute(name: String, defaultValue: T): T =
        enumValues<T>().firstOrNull { it.name == stringAttribute(name) } ?: defaultValue
}
