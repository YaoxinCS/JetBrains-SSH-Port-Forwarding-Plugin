package com.yaoxincs.portforwarding.ui

import com.intellij.notification.NotificationType
import com.yaoxincs.portforwarding.PortForwardingBundle
import com.yaoxincs.portforwarding.model.PortForwardRuleState
import com.yaoxincs.portforwarding.model.SshConnectionState
import com.yaoxincs.portforwarding.model.SshPortForwardingState
import com.yaoxincs.portforwarding.model.TunnelRuntimeStatus
import com.yaoxincs.portforwarding.runtime.SshTunnelManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Insets
import javax.swing.AbstractCellEditor
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class SshPortForwardingEditorPanel(
    initialState: SshPortForwardingState,
    private val onStateChanged: (SshPortForwardingState) -> Unit,
    private val currentProjectProvider: () -> Project?,
) : JPanel(BorderLayout()), Disposable {

    private val tunnelManager = ApplicationManager.getApplication().getService(SshTunnelManager::class.java)

    private var state = initialState
    private var selectedConnectionId: String? = initialState.connections.firstOrNull()?.id
    private var updatingUi = false
    private var lastRuntimeSnapshot: List<Pair<String, TunnelRuntimeStatus>> = emptyList()

    private val listModel = javax.swing.DefaultListModel<SshConnectionState>()
    private val connectionList = JBList(listModel)
    private val addButton = JButton(PluginIcons.AddTunnel)
    private val editButton = JButton(PluginIcons.EditTunnel)
    private val removeButton = JButton(PluginIcons.DeleteTunnel)
    private val runtimeRefreshTimer = Timer(250) { pollRuntimeState() }

    private val emptyPanel = JPanel(BorderLayout()).apply {
        border = EmptyBorder(18, 18, 18, 18)
        add(JBLabel(PortForwardingBundle.message("ui.session.placeholder")), BorderLayout.NORTH)
    }
    private val overviewPanel = JPanel(BorderLayout(0, 10))
    private val detailCards = JPanel(CardLayout()).apply {
        add(emptyPanel, "empty")
        add(overviewPanel, "overview")
    }

    private val sessionTitleLabel = JBLabel(PortForwardingBundle.message("ui.session.title"))
    private val tunnelTableModel = PortForwardOverviewTableModel(::currentRows)
    private val tunnelTable = JBTable(tunnelTableModel)

    private val newTunnelButton = createFooterButton(PortForwardingBundle.message("button.new.tunnel"), PluginIcons.AddTunnel)
    private val startAllButton = createFooterButton(PortForwardingBundle.message("button.start.all"), PluginIcons.StartTunnel)
    private val stopAllButton = createFooterButton(PortForwardingBundle.message("button.stop.all"), PluginIcons.StopTunnel)

    init {
        connectionList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        connectionList.emptyText.text = PortForwardingBundle.message("ui.session.none")
        connectionList.cellRenderer = object : ColoredListCellRenderer<SshConnectionState>() {
            override fun customizeCellRenderer(
                list: javax.swing.JList<out SshConnectionState>,
                value: SshConnectionState?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                append(value?.let(::displayConnectionName) ?: PortForwardingBundle.message("ui.session.default"))
            }
        }
        connectionList.addListSelectionListener {
            if (!updatingUi && !it.valueIsAdjusting) {
                selectedConnectionId = connectionList.selectedValue?.id
                refreshUiState()
            }
        }

        addButton.addActionListener { startAddConnection() }
        editButton.addActionListener { startEditConnection() }
        removeButton.addActionListener { removeSelectedConnection() }
        newTunnelButton.addActionListener { addTunnel() }
        startAllButton.addActionListener { currentConnection()?.let { tunnelManager.startAll(it, currentProjectProvider()) } }
        stopAllButton.addActionListener { currentConnection()?.let { tunnelManager.stopAll(it.id) } }

        buildOverviewPanel()
        val splitter = OnePixelSplitter(false, 0.16f).apply {
            setHonorComponentsMinimumSize(false)
            setDividerWidth(10)
            firstComponent = createListPanel()
            secondComponent = detailCards
        }
        add(splitter, BorderLayout.CENTER)

        tunnelManager.addChangeListener(this) {
            endTableEditing()
            tunnelTableModel.refresh()
            refreshFooterState()
            refreshTunnelTableVisuals()
            lastRuntimeSnapshot = currentRuntimeSnapshot()
        }

        loadState(initialState)
        runtimeRefreshTimer.start()
    }

    fun loadState(newState: SshPortForwardingState) {
        state = newState
        if (selectedConnectionId == null || state.connections.none { it.id == selectedConnectionId }) {
            selectedConnectionId = state.connections.firstOrNull()?.id
        }
        refreshUiState()
        lastRuntimeSnapshot = currentRuntimeSnapshot()
    }

    fun getPreferredFocusTarget(): JComponent = connectionList

    override fun dispose() {
        runtimeRefreshTimer.stop()
    }

    private fun buildOverviewPanel() {
        tunnelTable.rowHeight = 44
        tunnelTable.autoCreateRowSorter = false
        tunnelTable.tableHeader.reorderingAllowed = false
        tunnelTable.putClientProperty("terminateEditOnFocusLost", true)
        tunnelTable.emptyText.text = PortForwardingBundle.message("ui.session.none.selected")
        (tunnelTable.tableHeader.defaultRenderer as? DefaultTableCellRenderer)?.horizontalAlignment = SwingConstants.CENTER
        tunnelTable.columnModel.getColumn(0).preferredWidth = 140
        tunnelTable.columnModel.getColumn(1).preferredWidth = 90
        tunnelTable.columnModel.getColumn(2).preferredWidth = 180
        tunnelTable.columnModel.getColumn(3).preferredWidth = 180
        tunnelTable.columnModel.getColumn(4).preferredWidth = 72
        tunnelTable.columnModel.getColumn(5).preferredWidth = 92
        tunnelTable.columnModel.getColumn(6).preferredWidth = 92

        tunnelTable.columnModel.getColumn(4).cellRenderer = PanelCellRenderer { row, table, selected ->
            buildStatusPanel(row, table, selected)
        }
        tunnelTable.columnModel.getColumn(5).cellRenderer = PanelCellRenderer { row, table, selected ->
            buildStartStopPanel(row, table, selected, interactive = false)
        }
        tunnelTable.columnModel.getColumn(5).cellEditor = PanelCellEditor { row, table, selected ->
            buildStartStopPanel(row, table, selected, interactive = true)
        }
        tunnelTable.columnModel.getColumn(6).cellRenderer = PanelCellRenderer { row, table, selected ->
            buildSettingsPanel(row, table, selected, interactive = false)
        }
        tunnelTable.columnModel.getColumn(6).cellEditor = PanelCellEditor { row, table, selected ->
            buildSettingsPanel(row, table, selected, interactive = true)
        }

        val headerPanel = JPanel(BorderLayout(0, 6)).apply {
            border = EmptyBorder(16, 16, 0, 16)
            add(sessionTitleLabel, BorderLayout.NORTH)
        }
        val footerPanel = JPanel(FlowLayout(FlowLayout.LEFT, 12, 10)).apply {
            border = BorderFactory.createEmptyBorder(0, 12, 12, 12)
            add(newTunnelButton)
            add(startAllButton)
            add(stopAllButton)
        }
        overviewPanel.add(headerPanel, BorderLayout.NORTH)
        overviewPanel.add(ScrollPaneFactory.createScrollPane(tunnelTable), BorderLayout.CENTER)
        overviewPanel.add(footerPanel, BorderLayout.SOUTH)
    }

    private fun createListPanel(): JComponent {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            border = EmptyBorder(12, 12, 0, 12)
            add(addButton)
            add(editButton)
            add(removeButton)
        }
        addButton.toolTipText = PortForwardingBundle.message("tooltip.new.session")
        editButton.toolTipText = PortForwardingBundle.message("tooltip.edit.session")
        removeButton.toolTipText = PortForwardingBundle.message("tooltip.delete.session")
        listOf(addButton, editButton, removeButton).forEach { button ->
            button.preferredSize = Dimension(32, 32)
            button.minimumSize = button.preferredSize
            button.maximumSize = button.preferredSize
            button.isFocusPainted = false
            button.margin = Insets(0, 0, 0, 0)
            button.horizontalAlignment = SwingConstants.CENTER
        }
        return JPanel(BorderLayout(0, 8)).apply {
            preferredSize = Dimension(220, 0)
            minimumSize = Dimension(140, 0)
            add(toolbar, BorderLayout.NORTH)
            add(ScrollPaneFactory.createScrollPane(connectionList), BorderLayout.CENTER)
        }
    }

    private fun startAddConnection() {
        val dialog = SshConnectionDialog(
            initialConnection = SshConnectionState(),
            projectScopeAvailable = currentProjectProvider() != null,
            scopeEditable = true,
        )
        if (!dialog.showAndGet()) return
        val connection = dialog.connectionState()
        state = state.copy(connections = state.connections + connection)
        selectedConnectionId = connection.id
        onStateChanged(state)
        refreshUiState()
    }

    private fun startEditConnection() {
        val current = currentConnection() ?: return
        val dialog = SshConnectionDialog(
            initialConnection = current,
            projectScopeAvailable = currentProjectProvider() != null,
            scopeEditable = false,
        )
        if (!dialog.showAndGet()) return
        val updated = dialog.connectionState().copy(id = current.id, portForwardRules = current.portForwardRules)
        val changed = current.copy(portForwardRules = emptyList()) != updated.copy(portForwardRules = emptyList())
        replaceConnection(updated)
        if (changed) {
            tunnelManager.stopAll(current.id)
            showInfoDialog(
                PortForwardingBundle.message("info.session.updated.title"),
                PortForwardingBundle.message("info.session.updated.content"),
            )
        }
    }

    private fun removeSelectedConnection() {
        val current = currentConnection() ?: return
        val confirmed = Messages.showYesNoDialog(
            this,
            PortForwardingBundle.message("confirm.delete.session.message", displayConnectionName(current)),
            PortForwardingBundle.message("confirm.delete.session.title"),
            Messages.getQuestionIcon(),
        )
        if (confirmed != Messages.YES) return

        current.portForwardRules.forEach { tunnelManager.removeRule(it.id) }
        state = state.copy(connections = state.connections.filterNot { it.id == current.id })
        selectedConnectionId = state.connections.firstOrNull()?.id
        onStateChanged(state)
        refreshUiState()
    }

    private fun addTunnel() {
        val connection = currentConnection() ?: return
        val nextPort = 8080 + connection.portForwardRules.size
        val dialog = PortForwardRuleDialog(
            PortForwardRuleState(
                sourcePort = nextPort,
                destinationPort = nextPort,
            ),
        )
        if (!dialog.showAndGet()) return
        val rule = dialog.ruleState()
        val updatedConnection = updateSelectedConnection { it.copy(portForwardRules = it.portForwardRules + rule) } ?: return
        tunnelManager.updateConfiguration(updatedConnection, rule, currentProjectProvider())
    }

    private fun editTunnel(row: PortForwardRow) {
        val dialog = PortForwardRuleDialog(row.rule)
        if (!dialog.showAndGet()) return
        val updatedRule = dialog.ruleState().copy(id = row.rule.id)
        val updatedConnection = updateRule(row.rule.id, updatedRule) ?: return
        if (requiresTunnelRestart(row.rule, updatedRule) && row.runtime.status.isActive()) {
            tunnelManager.updateConfiguration(updatedConnection, updatedRule, currentProjectProvider())
            tunnelManager.stop(row.rule.id)
            showInfoDialog(
                PortForwardingBundle.message("info.tunnel.updated.title"),
                PortForwardingBundle.message("info.tunnel.updated.content"),
            )
        } else {
            tunnelManager.updateConfiguration(updatedConnection, updatedRule, currentProjectProvider())
        }
    }

    private fun deleteTunnel(row: PortForwardRow) {
        val confirmed = Messages.showYesNoDialog(
            this,
            PortForwardingBundle.message(
                "confirm.delete.tunnel.message",
                row.rule.name.ifBlank { "${row.rule.bindAddress}:${row.rule.sourcePort}" },
            ),
            PortForwardingBundle.message("confirm.delete.tunnel.title"),
            Messages.getQuestionIcon(),
        )
        if (confirmed != Messages.YES) return
        tunnelManager.removeRule(row.rule.id)
        updateSelectedConnection { connection ->
            connection.copy(portForwardRules = connection.portForwardRules.filterNot { it.id == row.rule.id })
        }
    }

    private fun refreshUiState() {
        reloadList()
        val current = currentConnection()
        val layout = detailCards.layout as CardLayout
        if (current == null) {
            layout.show(detailCards, "empty")
        } else {
            sessionTitleLabel.text = displayConnectionName(current)
            tunnelTableModel.refresh()
            layout.show(detailCards, "overview")
        }

        val hasSelection = current != null
        editButton.isEnabled = hasSelection
        removeButton.isEnabled = hasSelection
        refreshFooterState()
    }

    private fun reloadList() {
        updatingUi = true
        listModel.removeAllElements()
        state.connections.forEach(listModel::addElement)
        val selected = state.connections.firstOrNull { it.id == selectedConnectionId } ?: state.connections.firstOrNull()
        selectedConnectionId = selected?.id
        if (selected != null) {
            connectionList.setSelectedValue(selected, true)
        } else {
            connectionList.clearSelection()
        }
        updatingUi = false
    }

    private fun refreshFooterState() {
        val current = currentConnection()
        val hasRules = current?.portForwardRules?.isNotEmpty() == true
        val hasStartableRules = current?.portForwardRules?.any {
            val status = tunnelManager.stateFor(it.id).status
            status == TunnelRuntimeStatus.STOPPED || status == TunnelRuntimeStatus.ERROR
        } == true
        val hasActiveRules = current?.portForwardRules?.any { tunnelManager.stateFor(it.id).status.isActive() } == true
        newTunnelButton.isEnabled = current != null
        startAllButton.isEnabled = hasRules && hasStartableRules
        stopAllButton.isEnabled = hasActiveRules
    }

    private fun currentConnection(): SshConnectionState? = state.connections.firstOrNull { it.id == selectedConnectionId }

    private fun currentRows(): List<PortForwardRow> {
        val connection = currentConnection() ?: return emptyList()
        return connection.portForwardRules.map { rule ->
            PortForwardRow(connection, rule, tunnelManager.stateFor(rule.id))
        }
    }

    private fun updateSelectedConnection(transform: (SshConnectionState) -> SshConnectionState): SshConnectionState? {
        val current = currentConnection() ?: return null
        val updated = transform(current)
        return replaceConnection(updated)
    }

    private fun replaceConnection(updatedConnection: SshConnectionState): SshConnectionState {
        state = state.copy(
            connections = state.connections.map { existing ->
                if (existing.id == updatedConnection.id) updatedConnection else existing
            },
        )
        selectedConnectionId = updatedConnection.id
        onStateChanged(state)
        refreshUiState()
        return updatedConnection
    }

    private fun updateRule(ruleId: String, updatedRule: PortForwardRuleState): SshConnectionState? =
        updateSelectedConnection { connection ->
            connection.copy(
                portForwardRules = connection.portForwardRules.map { existing ->
                    if (existing.id == ruleId) updatedRule else existing
                },
            )
        }

    private fun displayConnectionName(connection: SshConnectionState): String =
        if (connection.userName.isNotBlank() && connection.host.isNotBlank()) {
            buildString {
                append("${connection.userName}@${connection.host}:${connection.port}")
                if (connection.projectScoped) {
                    append(PortForwardingBundle.message("ui.session.project.suffix"))
                }
            }
        } else {
            if (connection.projectScoped) {
                PortForwardingBundle.message("ui.session.default.project")
            } else {
                PortForwardingBundle.message("ui.session.default")
            }
        }

    private fun requiresTunnelRestart(previous: PortForwardRuleState, updated: PortForwardRuleState): Boolean =
        previous.copy(name = updated.name, id = updated.id) != updated

    private fun buildStartStopPanel(row: PortForwardRow, table: JTable, selected: Boolean, interactive: Boolean): JPanel {
        val panel = actionPanel(table, selected)
        val canStart = row.runtime.status == TunnelRuntimeStatus.STOPPED || row.runtime.status == TunnelRuntimeStatus.ERROR
        val canStop = row.runtime.status.isActive()

        panel.add(
            createIconButton(
                icon = PluginIcons.StartTunnel,
                tooltip = row.runtime.message.ifBlank { PortForwardingBundle.message("tooltip.start.tunnel") },
                interactive = interactive,
                enabled = canStart,
                accent = if (canStart) Color(0x244233) else null,
            ) {
                tunnelManager.start(row.connection, row.rule, currentProjectProvider())
            },
        )
        panel.add(
            createIconButton(
                icon = PluginIcons.StopTunnel,
                tooltip = row.runtime.message.ifBlank { PortForwardingBundle.message("tooltip.stop.tunnel") },
                interactive = interactive,
                enabled = canStop,
                accent = if (canStop) Color(0x4A2525) else null,
            ) {
                tunnelManager.stop(row.rule.id)
            },
        )
        return panel
    }

    private fun buildStatusPanel(row: PortForwardRow, table: JTable, selected: Boolean): JPanel {
        val panel = actionPanel(table, selected)
        val label = JBLabel(statusIcon(row.runtime.status)).apply {
            toolTipText = row.runtime.message.ifBlank { statusText(row.runtime.status) }
            horizontalAlignment = SwingConstants.CENTER
            border = BorderFactory.createEmptyBorder(2, 2, 2, 2)
        }
        panel.add(label)
        return panel
    }

    private fun buildSettingsPanel(row: PortForwardRow, table: JTable, selected: Boolean, interactive: Boolean): JPanel {
        val panel = actionPanel(table, selected)
        panel.add(
            createIconButton(
                icon = PluginIcons.EditTunnel,
                tooltip = PortForwardingBundle.message("tooltip.edit.tunnel"),
                interactive = interactive,
                enabled = true,
                accent = java.awt.Color(0x322B46),
            ) {
                editTunnel(row)
            },
        )
        panel.add(
            createIconButton(
                icon = PluginIcons.DeleteTunnel,
                tooltip = PortForwardingBundle.message("tooltip.delete.tunnel"),
                interactive = interactive,
                enabled = true,
                accent = java.awt.Color(0x4A2525),
            ) {
                deleteTunnel(row)
            },
        )
        return panel
    }

    private fun actionPanel(table: JTable, selected: Boolean): JPanel =
        JPanel(FlowLayout(FlowLayout.CENTER, 6, 6)).apply {
            isOpaque = true
            background = if (selected) table.selectionBackground else table.background
        }

    private fun createIconButton(
        icon: javax.swing.Icon,
        tooltip: String,
        interactive: Boolean,
        enabled: Boolean,
        accent: java.awt.Color?,
        action: () -> Unit,
    ): JButton =
        JButton(icon).apply {
            toolTipText = tooltip
            isEnabled = enabled
            isFocusPainted = false
            isContentAreaFilled = accent != null
            isOpaque = accent != null
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(java.awt.Color(0x5A6370), 1, true),
                BorderFactory.createEmptyBorder(4, 4, 4, 4),
            )
            horizontalAlignment = SwingConstants.CENTER
            preferredSize = Dimension(28, 24)
            minimumSize = preferredSize
            maximumSize = preferredSize
            margin = Insets(0, 0, 0, 0)
            background = accent ?: java.awt.Color(0x3A4250)
            if (interactive) {
                addActionListener {
                    action()
                    endTableEditing()
                    refreshTunnelTableVisuals()
                }
            }
        }

    private fun createFooterButton(text: String, icon: javax.swing.Icon): JButton =
        JButton(text, icon).apply {
            isFocusPainted = false
            margin = Insets(8, 14, 8, 14)
            toolTipText = when (text) {
                PortForwardingBundle.message("button.new.tunnel") -> PortForwardingBundle.message("tooltip.new.tunnel")
                PortForwardingBundle.message("button.start.all") -> PortForwardingBundle.message("tooltip.start.all")
                PortForwardingBundle.message("button.stop.all") -> PortForwardingBundle.message("tooltip.stop.all")
                else -> text
            }
        }

    private class PanelCellRenderer(
        private val builder: (PortForwardRow, JTable, Boolean) -> JPanel,
    ) : TableCellRenderer {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component = builder(value as PortForwardRow, table, isSelected)
    }

    private class PanelCellEditor(
        private val builder: (PortForwardRow, JTable, Boolean) -> JPanel,
    ) : AbstractCellEditor(), TableCellEditor {

        override fun getCellEditorValue(): Any = ""

        override fun getTableCellEditorComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val panel = builder(value as PortForwardRow, table, isSelected)
            collectButtons(panel).forEach { button ->
                button.addActionListener {
                    SwingUtilities.invokeLater { stopCellEditing() }
                }
            }
            return panel
        }

        private fun collectButtons(component: Component): List<JButton> =
            when (component) {
                is JButton -> listOf(component)
                is JPanel -> component.components.flatMap(::collectButtons)
                else -> emptyList()
            }
    }

    private fun statusText(status: TunnelRuntimeStatus): String =
        when (status) {
            TunnelRuntimeStatus.STARTING -> PortForwardingBundle.message("status.starting")
            TunnelRuntimeStatus.RUNNING -> PortForwardingBundle.message("status.running")
            TunnelRuntimeStatus.STOPPING -> PortForwardingBundle.message("status.stopping")
            TunnelRuntimeStatus.RECONNECTING -> PortForwardingBundle.message("status.retrying")
            TunnelRuntimeStatus.ERROR -> PortForwardingBundle.message("status.error")
            TunnelRuntimeStatus.STOPPED -> PortForwardingBundle.message("status.stopped")
        }

    private fun statusIcon(status: TunnelRuntimeStatus): javax.swing.Icon =
        when (status) {
            TunnelRuntimeStatus.RUNNING -> PluginIcons.StatusSuccess
            TunnelRuntimeStatus.ERROR -> PluginIcons.StatusError
            TunnelRuntimeStatus.STARTING,
            TunnelRuntimeStatus.STOPPING,
            TunnelRuntimeStatus.RECONNECTING -> PluginIcons.StatusLoading
            TunnelRuntimeStatus.STOPPED -> PluginIcons.StopTunnel
        }

    private fun showInfoDialog(title: String, content: String) {
        SshPortForwardingUiSupport.showDialog(this, title, content, NotificationType.INFORMATION)
    }

    private fun pollRuntimeState() {
        if (!isShowing) return
        val snapshot = currentRuntimeSnapshot()
        if (snapshot == lastRuntimeSnapshot) return
        lastRuntimeSnapshot = snapshot
        endTableEditing()
        tunnelTableModel.refresh()
        refreshFooterState()
        refreshTunnelTableVisuals()
    }

    private fun currentRuntimeSnapshot(): List<Pair<String, TunnelRuntimeStatus>> =
        currentConnection()
            ?.portForwardRules
            ?.map { it.id to tunnelManager.stateFor(it.id).status }
            .orEmpty()

    private fun endTableEditing() {
        if (tunnelTable.isEditing) {
            tunnelTable.cellEditor?.cancelCellEditing()
            tunnelTable.removeEditor()
        }
    }

    private fun refreshTunnelTableVisuals() {
        tunnelTable.revalidate()
        tunnelTable.repaint()
        val visibleRect = tunnelTable.visibleRect
        if (!visibleRect.isEmpty) {
            tunnelTable.paintImmediately(visibleRect)
        }
    }
}
