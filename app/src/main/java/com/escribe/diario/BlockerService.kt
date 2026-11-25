package com.escribe.diario

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import java.time.LocalTime

class BlockerService : AccessibilityService() {
    private var lastBlock = 0L

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        val s = Store(this)
        if (!s.blockerOn || pkg !in s.blockedApps) return
        if (LocalTime.now().hour < s.cutoffHour || s.doneToday()) return
        val now = System.currentTimeMillis()
        if (now - lastBlock < 1500) return
        lastBlock = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("blocked", true))
    }

    override fun onInterrupt() {}
}
