package com.yaoxincs.portforwarding.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.project.Project
import java.awt.Component

object SshPortForwardingUiSupport {

    private const val NOTIFICATION_GROUP_ID = "port.forwarding.notifications"

    fun notify(project: Project?, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, content, type)
            .notify(project)
    }

    fun showDialog(parent: Component?, title: String, content: String, type: NotificationType) {
        when (type) {
            NotificationType.INFORMATION ->
                if (parent != null) Messages.showInfoMessage(parent, content, title) else Messages.showInfoMessage(content, title)
            NotificationType.WARNING ->
                if (parent != null) Messages.showWarningDialog(parent, content, title) else Messages.showWarningDialog(content, title)
            NotificationType.ERROR ->
                if (parent != null) Messages.showErrorDialog(parent, content, title) else Messages.showErrorDialog(content, title)
            else ->
                if (parent != null) Messages.showInfoMessage(parent, content, title) else Messages.showInfoMessage(content, title)
        }
    }
}
