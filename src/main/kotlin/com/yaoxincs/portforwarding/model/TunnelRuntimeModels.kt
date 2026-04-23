package com.yaoxincs.portforwarding.model

enum class TunnelRuntimeStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    RECONNECTING,
    ERROR,
    ;

    fun isActive(): Boolean = this == STARTING || this == RUNNING || this == RECONNECTING || this == STOPPING
}

data class TunnelRuntimeState(
    val ruleId: String,
    val status: TunnelRuntimeStatus = TunnelRuntimeStatus.STOPPED,
    val message: String = "",
)
