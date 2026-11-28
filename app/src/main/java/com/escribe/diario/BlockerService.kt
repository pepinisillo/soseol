package com.escribe.diario

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import java.time.LocalTime

class BlockerService : AccessibilityService() {
    private var lastBlock = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val check = Runnable { activeApp()?.let(::maybeBlock) }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        e ?: return
        // Si el aviso dice de qué app es, se revisa al momento.
        if (e.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) e.packageName?.toString()?.let(::maybeBlock)
        // Además, un instante después de cualquier aviso se mira qué app quedó al frente: en Xiaomi, al volver
        // a una app desde recientes no llega «ventana nueva» y «cambiaron las ventanas» llega antes de que cambie.
        handler.removeCallbacks(check)
        handler.postDelayed(check, 350)
    }

    private fun maybeBlock(pkg: String) {
        val now = System.currentTimeMillis()
        if (now - lastBlock < 1500) return
        val s = Store(this)
        // De lo más barato a lo más caro: estos avisos llegan muy seguido.
        if (!s.blockerOn || pkg !in s.blockedApps) return
        if (LocalTime.now().hour < s.cutoffHour || s.doneToday()) return
        lastBlock = now
        Log.d("Bloqueo", "bloqueando $pkg")
        // Solo se abre la app encima. Antes se mandaba también «inicio», pero en Xiaomi llega tarde y tapa a la app.
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("blocked", true))
    }

    /** El paquete de la app que está al frente (solo su nombre; no se lee lo que hay en la pantalla). */
    private fun activeApp(): String? = runCatching {
        val w = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }
            ?: windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }
        val root = w?.root ?: rootInActiveWindow
        root?.packageName?.toString()
    }.getOrNull()

    override fun onServiceConnected() { super.onServiceConnected(); connected = true }
    override fun onDestroy() { connected = false; handler.removeCallbacks(check); super.onDestroy() }

    companion object {
        /** Si el servicio está corriendo en este proceso (Ajustes lo muestra como «Funcionando»). */
        @Volatile var connected = false
    }
    override fun onInterrupt() {}
}
