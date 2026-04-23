package com.yaoxincs.portforwarding.ui

import com.yaoxincs.portforwarding.PortForwardingBundle
import com.yaoxincs.portforwarding.model.PortForwardDirection
import com.yaoxincs.portforwarding.model.PortForwardRuleState
import com.yaoxincs.portforwarding.runtime.localBindAddressOptions
import com.yaoxincs.portforwarding.runtime.normalizedRule
import com.yaoxincs.portforwarding.runtime.validateRule
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel

class PortForwardRuleDialog(
    private val initialRule: PortForwardRuleState,
) : DialogWrapper(true) {

    private val nameField = JBTextField(initialRule.name)
    private val directionCombo = ComboBox(PortForwardDirection.entries.toTypedArray())
    private val bindAddressField = ComboBox<String>()
    private val sourcePortField = JBTextField(initialRule.sourcePort.toString())
    private val destinationHostField = JBTextField(initialRule.destinationHost)
    private val destinationPortField = JBTextField(initialRule.destinationPort.toString())
    private val autoStartCheckBox = JBCheckBox(PortForwardingBundle.message("dialog.tunnel.auto.start"), initialRule.autoStart)
    private val autoReconnectCheckBox = JBCheckBox(PortForwardingBundle.message("dialog.tunnel.auto.reconnect"), initialRule.autoReconnect)
    private val scenarioLabel = JBLabel()
    private val listenerSeparator = TitledSeparator("")
    private val listenerAddressLabel = JBLabel()
    private val listenerPortLabel = JBLabel()
    private val targetSeparator = TitledSeparator("")
    private val targetHostLabel = JBLabel()
    private val targetPortLabel = JBLabel()

    init {
        title = if (initialRule.name.isBlank() && initialRule.sourcePort == 8080 && initialRule.destinationPort == 80) {
            PortForwardingBundle.message("dialog.tunnel.new.title")
        } else {
            PortForwardingBundle.message("dialog.tunnel.edit.title")
        }
        directionCombo.selectedItem = initialRule.direction
        bindAddressField.isEditable = true
        refreshBindAddressOptions()
        bindAddressField.editor.item = initialRule.bindAddress
        directionCombo.addActionListener {
            refreshBindAddressOptions()
            refreshDirectionPresentation()
        }
        init()
    }

    fun ruleState(): PortForwardRuleState = normalizedRule(
        initialRule.copy(
            name = nameField.text,
            direction = directionCombo.selectedItem as? PortForwardDirection ?: initialRule.direction,
            bindAddress = bindAddressField.editor.item?.toString().orEmpty(),
            sourcePort = sourcePortField.text.toIntOrNull() ?: initialRule.sourcePort,
            destinationHost = destinationHostField.text,
            destinationPort = destinationPortField.text.toIntOrNull() ?: initialRule.destinationPort,
            autoStart = autoStartCheckBox.isSelected,
            autoReconnect = autoReconnectCheckBox.isSelected,
        ),
    )

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 12))
        panel.preferredSize = Dimension(700, 360)

        val form = JPanel(GridBagLayout())
        val c = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(0, 0, 12, 12)
            gridx = 0
            gridy = 0
        }

        form.add(JBLabel(PortForwardingBundle.message("dialog.tunnel.name")), c)
        c.gridx = 1
        c.weightx = 1.0
        c.gridwidth = 3
        form.add(nameField, c)
        c.gridwidth = 1
        c.weightx = 0.0

        c.gridy++
        c.gridx = 0
        form.add(JBLabel(PortForwardingBundle.message("dialog.tunnel.mode")), c)
        c.gridx = 1
        c.gridwidth = 3
        form.add(directionCombo, c)
        c.gridwidth = 1

        c.gridy++
        c.gridx = 0
        c.gridwidth = 4
        form.add(scenarioLabel, c)
        c.gridwidth = 1

        c.gridy++
        c.gridx = 0
        c.gridwidth = 4
        form.add(listenerSeparator, c)
        c.gridwidth = 1

        c.gridy++
        c.gridx = 0
        form.add(listenerAddressLabel, c)
        c.gridx = 1
        c.weightx = 1.0
        c.gridwidth = 3
        form.add(bindAddressField, c)
        c.gridwidth = 1
        c.weightx = 0.0

        c.gridy++
        c.gridx = 0
        form.add(listenerPortLabel, c)
        c.gridx = 1
        c.weightx = 1.0
        c.gridwidth = 3
        form.add(sourcePortField, c)
        c.gridwidth = 1
        c.weightx = 0.0

        c.gridy++
        c.gridx = 0
        c.gridwidth = 4
        form.add(targetSeparator, c)
        c.gridwidth = 1

        c.gridy++
        c.gridx = 0
        form.add(targetHostLabel, c)
        c.gridx = 1
        c.weightx = 1.0
        c.gridwidth = 3
        form.add(destinationHostField, c)
        c.gridwidth = 1
        c.weightx = 0.0

        c.gridy++
        c.gridx = 0
        form.add(targetPortLabel, c)
        c.gridx = 1
        c.weightx = 1.0
        c.gridwidth = 3
        form.add(destinationPortField, c)
        c.gridwidth = 1
        c.weightx = 0.0

        c.gridy++
        c.gridx = 1
        c.gridwidth = 3
        form.add(autoStartCheckBox, c)

        c.gridy++
        form.add(autoReconnectCheckBox, c)

        refreshDirectionPresentation()
        panel.add(form, BorderLayout.NORTH)
        return panel
    }

    override fun doValidate(): ValidationInfo? =
        validateRule(ruleState())?.let { ValidationInfo(it, sourcePortField) }

    private fun refreshDirectionPresentation() {
        val direction = directionCombo.selectedItem as? PortForwardDirection ?: PortForwardDirection.LOCAL_TO_REMOTE
        when (direction) {
            PortForwardDirection.LOCAL_TO_REMOTE -> {
                scenarioLabel.text = PortForwardingBundle.message("dialog.tunnel.scenario.local")
                listenerSeparator.text = PortForwardingBundle.message("dialog.tunnel.listener.local.title")
                listenerAddressLabel.text = PortForwardingBundle.message("dialog.tunnel.listener.local.address")
                listenerPortLabel.text = PortForwardingBundle.message("dialog.tunnel.listener.local.port")
                targetSeparator.text = PortForwardingBundle.message("dialog.tunnel.target.remote.title")
                targetHostLabel.text = PortForwardingBundle.message("dialog.tunnel.target.remote.host")
                targetPortLabel.text = PortForwardingBundle.message("dialog.tunnel.target.remote.port")
            }

            PortForwardDirection.REMOTE_TO_LOCAL -> {
                scenarioLabel.text = PortForwardingBundle.message("dialog.tunnel.scenario.remote")
                listenerSeparator.text = PortForwardingBundle.message("dialog.tunnel.listener.remote.title")
                listenerAddressLabel.text = PortForwardingBundle.message("dialog.tunnel.listener.remote.address")
                listenerPortLabel.text = PortForwardingBundle.message("dialog.tunnel.listener.remote.port")
                targetSeparator.text = PortForwardingBundle.message("dialog.tunnel.target.local.title")
                targetHostLabel.text = PortForwardingBundle.message("dialog.tunnel.target.local.host")
                targetPortLabel.text = PortForwardingBundle.message("dialog.tunnel.target.local.port")
            }
        }
    }

    private fun refreshBindAddressOptions() {
        val direction = directionCombo.selectedItem as? PortForwardDirection ?: PortForwardDirection.LOCAL_TO_REMOTE
        val current = bindAddressField.editor.item?.toString().orEmpty().ifBlank { initialRule.bindAddress }
        val options = localBindAddressOptions(current, includeInterfaces = direction == PortForwardDirection.LOCAL_TO_REMOTE)
        bindAddressField.removeAllItems()
        options.forEach(bindAddressField::addItem)
        bindAddressField.editor.item = current
    }
}
