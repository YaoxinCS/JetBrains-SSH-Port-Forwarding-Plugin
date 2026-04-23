package com.yaoxincs.portforwarding.ui

import com.yaoxincs.portforwarding.model.PortForwardDirection
import com.yaoxincs.portforwarding.model.PortForwardRuleState
import javax.swing.table.AbstractTableModel

class PortForwardRuleTableModel(
    private val rulesProvider: () -> List<PortForwardRuleState>,
    private val onRuleChanged: (PortForwardRuleState) -> Unit,
) : AbstractTableModel() {

    private val columns = listOf(
        "Direction",
        "Bind Address",
        "Source Port",
        "Destination Host",
        "Destination Port",
    )

    override fun getRowCount(): Int = rulesProvider().size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String = columns[column]

    override fun getColumnClass(columnIndex: Int): Class<*> =
        when (columnIndex) {
            0 -> PortForwardDirection::class.java
            2, 4 -> Int::class.javaObjectType
            else -> String::class.java
        }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val rule = rulesProvider()[rowIndex]
        return when (columnIndex) {
            0 -> rule.direction
            1 -> rule.bindAddress
            2 -> rule.sourcePort
            3 -> rule.destinationHost
            4 -> rule.destinationPort
            else -> ""
        }
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        val rule = rulesProvider().getOrNull(rowIndex) ?: return
        val updated = when (columnIndex) {
            0 -> rule.copy(direction = aValue as? PortForwardDirection ?: rule.direction)
            1 -> rule.copy(bindAddress = aValue?.toString().orEmpty())
            2 -> parsePort(aValue)?.let { rule.copy(sourcePort = it) } ?: return
            3 -> rule.copy(destinationHost = aValue?.toString().orEmpty())
            4 -> parsePort(aValue)?.let { rule.copy(destinationPort = it) } ?: return
            else -> return
        }
        onRuleChanged(updated)
    }

    fun refresh() {
        fireTableDataChanged()
    }

    fun ruleAt(rowIndex: Int): PortForwardRuleState? = rulesProvider().getOrNull(rowIndex)

    private fun parsePort(value: Any?): Int? = value?.toString()?.toIntOrNull()?.takeIf { it in 1..65535 }
}
