package com.example.simplelauncher

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Menghitung jumlah notifikasi aktif per aplikasi untuk badge di ikon. */
class NotifListener : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onListenerConnected() = update()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = update()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = update()

    private fun update() {
        val map = HashMap<String, Int>()
        try {
            activeNotifications?.forEach { sbn ->
                val n = sbn.notification
                val isSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0
                if (isSummary || sbn.isOngoing) return@forEach
                val add = if (n.number > 0) n.number else 1
                map[sbn.packageName] = (map[sbn.packageName] ?: 0) + add
            }
        } catch (e: Exception) {
        }
        BadgeStore.counts = map
        handler.post { BadgeStore.onChange?.invoke() }
    }
}
