package com.yaoxincs.portforwarding.ui

import com.yaoxincs.portforwarding.PortForwardingBundle
import com.yaoxincs.portforwarding.model.SshAuthenticationType
import com.yaoxincs.portforwarding.model.SshConnectionState
import com.yaoxincs.portforwarding.model.StrictHostKeyCheckingMode
import com.yaoxincs.portforwarding.runtime.normalizedConnection
import com.yaoxincs.portforwarding.runtime.testConnection
import com.yaoxincs.portforwarding.runtime.validateConnectionForSave
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.SwingUtilities

class SshConnectionDialog(
    private val initialConnection: SshConnectionState,
    private val projectScopeAvailable: Boolean,
    private val scopeEditable: Boolean = true,
) : DialogWrapper(true) {

    private val contentPanel = JPanel(BorderLayout())
    private val hostField = JBTextField(initialConnection.host)
    private val portField = JBTextField(initialConnection.port.toString())
    private val userNameField = JBTextField(initialConnection.userName)
    private val authenticationTypeCombo = JComboBox(SshAuthenticationType.entries.toTypedArray())
    private val parseConfigCheckBox = JBCheckBox(PortForwardingBundle.message("dialog.connection.parse.config"), initialConnection.useOpenSshConfig)
    private val passwordField = JPasswordField(initialConnection.password)
    private val privateKeyField = TextFieldWithBrowseButton()
    private val privateKeyFileChooserDescriptor = FileChooserDescriptor(true, false, false, false, false, false)
        .withTitle(PortForwardingBundle.message("dialog.connection.select.private.key.title"))
        .withDescription(PortForwardingBundle.message("dialog.connection.select.private.key.description"))
    private val passphraseField = JPasswordField(initialConnection.passphrase)
    private val authenticationCards = JPanel(CardLayout())
    private val testConnectionButton = javax.swing.JButton(PortForwardingBundle.message("button.test.connection"))
    private val testConnectionStatusLabel = JBLabel(" ")
    private val projectOnlyCheckBox = JBCheckBox(PortForwardingBundle.message("dialog.connection.project.only"), initialConnection.projectScoped)

    private val keepAliveCheckBox = JBCheckBox(PortForwardingBundle.message("dialog.connection.keep.alive"), initialConnection.sendKeepAliveMessages)
    private val keepAliveSecondsField = JBTextField(initialConnection.keepAliveIntervalSeconds.toString())
    private val strictHostKeyCheckingCheckBox = JBCheckBox(PortForwardingBundle.message("dialog.connection.strict.host.key.checking"), initialConnection.strictHostKeyCheckingEnabled)
    private val strictHostKeyCheckingCombo = JComboBox(StrictHostKeyCheckingMode.entries.toTypedArray())
    private val hashKnownHostsCheckBox = JBCheckBox(PortForwardingBundle.message("dialog.connection.hash.known.hosts"), initialConnection.hashKnownHosts)

    init {
        title = if (initialConnection.host.isBlank()) {
            PortForwardingBundle.message("dialog.connection.new.title")
        } else {
            PortForwardingBundle.message("dialog.connection.edit.title")
        }
        authenticationTypeCombo.selectedItem = initialConnection.authenticationType
        strictHostKeyCheckingCombo.selectedItem = initialConnection.strictHostKeyCheckingMode
        privateKeyField.text = initialConnection.privateKeyPath
        projectOnlyCheckBox.isEnabled = projectScopeAvailable && scopeEditable
        if (!projectScopeAvailable) {
            projectOnlyCheckBox.isSelected = false
            projectOnlyCheckBox.toolTipText = PortForwardingBundle.message("dialog.connection.project.only.disabled")
        } else if (!scopeEditable) {
            projectOnlyCheckBox.toolTipText = null
        }
        privateKeyField.addActionListener { choosePrivateKeyFile() }
        authenticationTypeCombo.addActionListener { refreshAuthenticationCard() }
        keepAliveCheckBox.addActionListener { keepAliveSecondsField.isEnabled = keepAliveCheckBox.isSelected }
        strictHostKeyCheckingCheckBox.addActionListener { strictHostKeyCheckingCombo.isEnabled = strictHostKeyCheckingCheckBox.isSelected }
        testConnectionButton.addActionListener { runConnectionTest() }
        testConnectionButton.toolTipText = PortForwardingBundle.message("tooltip.test.connection")

        init()
        refreshAuthenticationCard()
        keepAliveSecondsField.isEnabled = keepAliveCheckBox.isSelected
        strictHostKeyCheckingCombo.isEnabled = strictHostKeyCheckingCheckBox.isSelected
    }

    fun connectionState(): SshConnectionState = normalizedConnection(
        initialConnection.copy(
            host = hostField.text,
            port = portField.text.toIntOrNull()?.coerceIn(1, 65535) ?: 22,
            userName = userNameField.text,
            projectScoped = if (scopeEditable) {
                projectOnlyCheckBox.isSelected && projectScopeAvailable
            } else {
                initialConnection.projectScoped
            },
            authenticationType = authenticationTypeCombo.selectedItem as? SshAuthenticationType ?: initialConnection.authenticationType,
            useOpenSshConfig = parseConfigCheckBox.isSelected,
            password = String(passwordField.password),
            privateKeyPath = privateKeyField.text,
            passphrase = String(passphraseField.password),
            sendKeepAliveMessages = keepAliveCheckBox.isSelected,
            keepAliveIntervalSeconds = keepAliveSecondsField.text.toIntOrNull()?.coerceAtLeast(1) ?: 300,
            strictHostKeyCheckingEnabled = strictHostKeyCheckingCheckBox.isSelected,
            strictHostKeyCheckingMode = strictHostKeyCheckingCombo.selectedItem as? StrictHostKeyCheckingMode
                ?: initialConnection.strictHostKeyCheckingMode,
            hashKnownHosts = hashKnownHostsCheckBox.isSelected,
        ),
    )

    override fun createCenterPanel(): JComponent {
        contentPanel.preferredSize = Dimension(760, 560)
        val form = JPanel(GridBagLayout())
        val c = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(0, 0, 12, 12)
            gridx = 0
            gridy = 0
        }

        form.add(JBLabel(PortForwardingBundle.message("dialog.connection.host")), c)
        c.gridx = 1
        c.weightx = 1.0
        form.add(hostField, c)
        c.gridx = 2
        c.weightx = 0.0
        form.add(JBLabel(PortForwardingBundle.message("dialog.connection.port")), c)
        c.gridx = 3
        form.add(portField, c)

        c.gridy++
        c.gridx = 0
        form.add(JBLabel(PortForwardingBundle.message("dialog.connection.user.name")), c)
        c.gridx = 1
        c.gridwidth = 3
        c.weightx = 1.0
        form.add(userNameField, c)
        c.gridwidth = 1
        c.weightx = 0.0

        c.gridy++
        c.gridx = 0
        form.add(JBLabel(PortForwardingBundle.message("dialog.connection.authentication.type")), c)
        c.gridx = 1
        c.gridwidth = 3
        form.add(authenticationTypeCombo, c)
        c.gridwidth = 1

        c.gridy++
        c.gridx = 0
        c.gridwidth = 4
        form.add(authenticationCards, c)
        c.gridwidth = 1

        c.gridy++
        c.gridx = 1
        c.gridwidth = 3
        form.add(parseConfigCheckBox, c)
        c.gridwidth = 1

        c.gridy++
        c.gridx = 1
        c.gridwidth = 3
        form.add(projectOnlyCheckBox, c)
        c.gridwidth = 1

        c.gridy++
        c.gridx = 1
        form.add(testConnectionButton, c)
        c.gridx = 2
        c.gridwidth = 2
        c.weightx = 1.0
        form.add(testConnectionStatusLabel, c)
        c.gridwidth = 1
        c.weightx = 0.0

        c.gridy++
        c.gridx = 0
        c.gridwidth = 4
        c.insets = Insets(16, 0, 8, 0)
        form.add(TitledSeparator(PortForwardingBundle.message("dialog.connection.connection.parameters")), c)
        c.insets = Insets(0, 0, 12, 12)
        c.gridwidth = 1

        c.gridy++
        c.gridx = 0
        form.add(keepAliveCheckBox, c)
        c.gridx = 1
        form.add(keepAliveSecondsField, c)
        c.gridx = 2
        c.gridwidth = 2
        form.add(JBLabel(PortForwardingBundle.message("dialog.connection.seconds")), c)
        c.gridwidth = 1

        c.gridy++
        c.gridx = 0
        form.add(strictHostKeyCheckingCheckBox, c)
        c.gridx = 1
        form.add(strictHostKeyCheckingCombo, c)

        c.gridy++
        c.gridx = 0
        c.gridwidth = 4
        form.add(hashKnownHostsCheckBox, c)
        c.gridwidth = 1

        c.gridy++
        c.gridx = 0
        c.weighty = 1.0
        c.fill = GridBagConstraints.VERTICAL
        form.add(JPanel(), c)

        buildAuthenticationCards()
        contentPanel.add(ScrollPaneFactory.createScrollPane(form), BorderLayout.CENTER)
        return contentPanel
    }

    override fun doValidate(): ValidationInfo? =
        validateConnectionForSave(connectionState())?.let { ValidationInfo(it, hostField) }

    private fun buildAuthenticationCards() {
        authenticationCards.add(
            JPanel(GridBagLayout()).apply {
                val c = GridBagConstraints().apply {
                    anchor = GridBagConstraints.WEST
                    fill = GridBagConstraints.HORIZONTAL
                    insets = Insets(0, 0, 8, 12)
                    gridx = 0
                    gridy = 0
                }
                add(JBLabel(PortForwardingBundle.message("dialog.connection.password")), c)
                c.gridx = 1
                c.weightx = 1.0
                c.gridwidth = 2
                add(passwordField, c)
            },
            SshAuthenticationType.PASSWORD.name,
        )

        authenticationCards.add(
            JPanel(GridBagLayout()).apply {
                val c = GridBagConstraints().apply {
                    anchor = GridBagConstraints.WEST
                    fill = GridBagConstraints.HORIZONTAL
                    insets = Insets(0, 0, 8, 12)
                    gridx = 0
                    gridy = 0
                }
                add(JBLabel(PortForwardingBundle.message("dialog.connection.private.key.file")), c)
                c.gridx = 1
                c.gridwidth = 2
                c.weightx = 1.0
                add(privateKeyField, c)
                c.gridwidth = 1
                c.gridy++
                c.gridx = 0
                c.weightx = 0.0
                add(JBLabel(PortForwardingBundle.message("dialog.connection.passphrase")), c)
                c.gridx = 1
                c.gridwidth = 2
                c.weightx = 1.0
                add(passphraseField, c)
            },
            SshAuthenticationType.KEY_PAIR.name,
        )

        authenticationCards.add(
            JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                add(JBLabel(PortForwardingBundle.message("dialog.connection.openssh.agent.info")))
            },
            SshAuthenticationType.OPENSSH_AGENT.name,
        )
    }

    private fun refreshAuthenticationCard() {
        val type = authenticationTypeCombo.selectedItem as? SshAuthenticationType ?: return
        (authenticationCards.layout as CardLayout).show(authenticationCards, type.name)
    }

    private fun choosePrivateKeyFile() {
        val fileToSelect = privateKeyField.text
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        val selectedFile = FileChooser.chooseFile(privateKeyFileChooserDescriptor, contentPanel, null, fileToSelect)
        if (selectedFile != null) {
            privateKeyField.text = selectedFile.path
        }
    }

    private fun runConnectionTest() {
        setTestLoadingState(true)
        testConnectionStatusLabel.text = PortForwardingBundle.message("test.connection.progress")
        testConnectionStatusLabel.toolTipText = testConnectionStatusLabel.text
        AppExecutorUtil.getAppExecutorService().submit {
            val result = try {
                testConnection(connectionState())
            } catch (t: Throwable) {
                com.yaoxincs.portforwarding.runtime.ConnectionTestResult(
                    title = PortForwardingBundle.message("test.connection.failed.title"),
                    content = t.message ?: PortForwardingBundle.message("runtime.failed"),
                    type = com.intellij.notification.NotificationType.ERROR,
                )
            }
            SwingUtilities.invokeLater {
                setTestLoadingState(false)
                testConnectionStatusLabel.text = result.content
                testConnectionStatusLabel.toolTipText = result.content
                SshPortForwardingUiSupport.showDialog(contentPanel, result.title, result.content, result.type)
                SshPortForwardingUiSupport.notify(null, result.title, result.content, result.type)
            }
        }
    }

    private fun setTestLoadingState(loading: Boolean) {
        testConnectionButton.isEnabled = !loading
        testConnectionButton.text = if (loading) {
            PortForwardingBundle.message("button.test.connection.loading")
        } else {
            PortForwardingBundle.message("button.test.connection")
        }
        testConnectionButton.toolTipText = PortForwardingBundle.message("tooltip.test.connection")
    }
}
