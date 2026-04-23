package com.yaoxincs.portforwarding.ui

import com.yaoxincs.portforwarding.PortForwardingBundle
import com.yaoxincs.portforwarding.model.PortForwardDirection
import com.yaoxincs.portforwarding.model.PortForwardRuleState
import com.yaoxincs.portforwarding.model.SshConnectionState
import com.yaoxincs.portforwarding.model.TunnelRuntimeState
import javax.swing.table.AbstractTableModel

data class PortForwardRow(
    val connection: SshConnectionState,
    val rule: PortForwardRuleState,
    val runtime: TunnelRuntimeState,
)

class PortForwardOverviewTableModel(
    private val rowsProvider: () -> List<PortForwardRow>,
) : AbstractTableModel() {

    private val columns = listOf(
        "table.column.name",
        "table.column.mode",
        "table.column.listen.on",
        "table.column.forward.to",
        "table.column.status",
        "table.column.control",
        "table.column.settings",
    )

    override fun getRowCount(): Int = rowsProvider().size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String = PortForwardingBundle.message(columns[column])

    override fun getColumnClass(columnIndex: Int): Class<*> =
        when (columnIndex) {
            4, 5, 6 -> PortForwardRow::class.java
            else -> String::class.java
        }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == 5 || columnIndex == 6

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rowsProvider()[rowIndex]
        return when (columnIndex) {
            0 -> row.rule.name
            1 -> if (row.rule.direction == PortForwardDirection.LOCAL_TO_REMOTE) {
                PortForwardingBundle.message("table.mode.local")
            } else {
                PortForwardingBundle.message("table.mode.remote")
            }
            2 -> if (row.rule.direction == PortForwardDirection.LOCAL_TO_REMOTE) {
                PortForwardingBundle.message("table.listen.local", row.rule.bindAddress, row.rule.sourcePort.toString())
            } else {
                PortForwardingBundle.message("table.listen.remote", row.rule.bindAddress, row.rule.sourcePort.toString())
            }
            3 -> if (row.rule.direction == PortForwardDirection.LOCAL_TO_REMOTE) {
                PortForwardingBundle.message("table.forward.remote", row.rule.destinationHost, row.rule.destinationPort.toString())
            } else {
                PortForwardingBundle.message("table.forward.local", row.rule.destinationHost, row.rule.destinationPort.toString())
            }
            4 -> row
            5 -> row
            6 -> row
            else -> ""
        }
    }

    fun rowAt(rowIndex: Int): PortForwardRow? = rowsProvider().getOrNull(rowIndex)

    fun refresh() {
        fireTableDataChanged()
    }
}
