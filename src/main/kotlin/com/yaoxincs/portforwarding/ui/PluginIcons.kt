package com.yaoxincs.portforwarding.ui

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object PluginIcons {
    val AddTunnel: Icon = load("/icons/sshpf/add.svg")
    val StartTunnel: Icon = load("/icons/sshpf/start.svg")
    val StopTunnel: Icon = load("/icons/sshpf/stop.svg")
    val StatusSuccess: Icon = load("/icons/sshpf/autostart.svg")
    val StatusError: Icon = load("/icons/sshpf/delete.svg")
    val StatusLoading: Icon = load("/icons/sshpf/reconnect.svg")
    val BindAddress: Icon = load("/icons/sshpf/bind.svg")
    val EditTunnel: Icon = load("/icons/sshpf/edit.svg")
    val DeleteTunnel: Icon = load("/icons/sshpf/delete.svg")
    val AutoStart: Icon = load("/icons/sshpf/autostart.svg")
    val AutoReconnect: Icon = load("/icons/sshpf/reconnect.svg")

    private fun load(path: String): Icon = IconLoader.getIcon(path, PluginIcons::class.java)
}
