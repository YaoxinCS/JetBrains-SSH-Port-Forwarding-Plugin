package com.yaoxincs.portforwarding.runtime

import com.yaoxincs.portforwarding.PortForwardingBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer

class SshProjectTunnelAutostartActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        try {
            val manager = ApplicationManager.getApplication().getService(SshTunnelManager::class.java)
            manager.startAutoConfiguredTunnels()
            manager.startAutoConfiguredTunnels(project)
            Disposer.register(project) {
                ApplicationManager.getApplication().getService(SshTunnelManager::class.java).stopProjectScopedTunnels(project)
            }
        } catch (t: Throwable) {
            LOG.error(PortForwardingBundle.message("log.project.autostart.failed"), t)
        }
    }

    private companion object {
        private val LOG = Logger.getInstance(SshProjectTunnelAutostartActivity::class.java)
    }
}
