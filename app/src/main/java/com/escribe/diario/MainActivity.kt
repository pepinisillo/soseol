package com.escribe.diario

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

enum class Tab { HOME, HISTORY, SETTINGS }

class MainActivity : ComponentActivity() {
    private var blockedMsg by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Barras del sistema transparentes: se ve el fondo de la app detrás de la hora y de los botones de Android.
        val clear = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(clear, clear), navigationBarStyle = SystemBarStyle.light(clear, clear))
        super.onCreate(savedInstanceState)
        // Con navegación de 3 botones, Android pone un fondo blanco detrás de ellos; esto lo quita.
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        blockedMsg = intent.getBooleanExtra("blocked", false)
        setContent { EscribeTheme { App(blockedMsg) { blockedMsg = false } } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        blockedMsg = intent.getBooleanExtra("blocked", false)
    }
}

@Composable
fun App(blocked: Boolean, dismissBlocked: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var tab by remember { mutableStateOf(Tab.HOME) }
    var writing by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf<String?>(null) }
    var reading by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<Long?>(null) }   // historia guardada que se está continuando o corrigiendo
    var tick by remember { mutableIntStateOf(0) }
    var prompt by remember { mutableStateOf(store.savedPrompt()) }
    var work by remember { mutableStateOf<Work?>(null) }
    var failure by remember { mutableStateOf<Pair<String, (() -> Unit)?>?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    fun generate(kind: String = Prompts.randomKind()) {
        if (work != null) return
        val previous = job
        job = scope.launch {
            work = Work("Buscando inspiración", "Preparando…", 0f, cancellable = true, cancelLabel = "Cancelar")
            // Si se canceló un reto mientras el modelo cargaba, se espera a que lo suelte: dos modelos a la vez no caben en memoria.
            previous?.cancelAndJoin()
            try {
                val p = Prompts.generate(ctx, store, kind) { pr, st -> work = work?.copy(progress = pr, stage = st) }
                store.savePrompt(p); prompt = p
            } catch (e: CancellationException) { throw e
            } catch (e: NoModelException) { tick++
            } catch (e: Throwable) { failure = (e.message ?: "Algo falló.") to { generate(kind) }
            } finally { work = null; tick++ }
        }
    }

    /** Reto al instante de la lista hecha a mano, sin IA. */
    fun fromList() {
        if (work != null) return
        val p = Prompts.fromList(ctx, store); store.savePrompt(p); prompt = p; tick++
    }

    fun download(spec: ModelSpec = store.model()) {
        if (work != null) return
        job = scope.launch {
            val total = LocalModel.gb(spec.size)
            work = Work("Descargando a tu musa", "${spec.name} · solo esta vez", 0f, "0.0 / $total GB", cancellable = true)
            var ok = false
            try {
                LocalModel.download(ctx, spec) { have ->
                    work = work?.copy(progress = have.toFloat() / spec.size, detail = "${LocalModel.gb(have)} / $total GB")
                }
                ok = true
            } catch (e: CancellationException) { throw e
            } catch (e: Throwable) { failure = (e.message ?: "La descarga falló.") to { download(spec) }
            } finally { work = null; tick++ }
            if (ok && store.savedPrompt() == null && spec.id == store.modelId) generate()
        }
    }

    LaunchedEffect(Unit) { if (prompt == null && Prompts.available(ctx, store)) generate() }
    BackHandler(writing || reading != null || tab != Tab.HOME) {
        when { writing -> { writing = false; editing = null }; reading != null -> reading = null; else -> tab = Tab.HOME }
    }

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        when {
            writing -> Box(Modifier.systemBarsPadding().imePadding()) {
                // Al guardar, vuelve a donde estaba: a la historia si se editó desde ahí, o al inicio.
                WriteScreen(store, prompt ?: Prompt("", "", "", ""), custom,
                    onSaved = { tick++; dismissBlocked(); writing = false; editing = null; if (reading == null) tab = Tab.HOME },
                    onBack = { writing = false; editing = null }, editing = editing?.let { store.entry(it) })
            }
            reading != null -> ReadScreen(store, reading!!, onBack = { reading = null }, onChanged = { tick++ },
                onEdit = { editing = it; writing = true }, tick = tick)
            else -> {
                Box(Modifier.statusBarsPadding()) {
                    when (tab) {
                        Tab.HOME -> HomeScreen(store, prompt, work != null, blocked, tick,
                            onWrite = { custom = null; writing = true },
                            onCustom = { custom = it; writing = true },
                            onReroll = { generate() },
                            onDownload = { download() },
                            onList = { fromList() },
                            onContinue = { editing = it; custom = null; writing = true })
                        Tab.HISTORY -> HistoryScreen(store, tick) { reading = it }
                        Tab.SETTINGS -> SettingsScreen(store, tick, onDownload = { download(it) }) { tick++; tab = Tab.HOME }
                    }
                }
                BottomBar(tab, Modifier.align(Alignment.BottomCenter)) { tab = it }
            }
        }
        work?.let { w -> WorkDialog(w) { job?.cancel(); work = null; tick++ } }
        failure?.let { (msg, retry) -> ErrorDialog(msg, retry?.let { r -> { failure = null; r() } }) { failure = null } }
    }
}

/** Barra inferior: una cápsula oscura pequeña, centrada, solo con íconos. */
@Composable
fun BottomBar(tab: Tab, modifier: Modifier = Modifier, onTab: (Tab) -> Unit) {
    Row(modifier.navigationBarsPadding().padding(bottom = 10.dp).height(52.dp)
        .clip(RoundedCornerShape(50)).background(C.Ink).padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        NavItem(Icons.Rounded.Home, "Hoy", tab == Tab.HOME) { onTab(Tab.HOME) }
        NavItem(Icons.Rounded.List, "Historias", tab == Tab.HISTORY) { onTab(Tab.HISTORY) }
        NavItem(Icons.Rounded.Settings, "Ajustes", tab == Tab.SETTINGS) { onTab(Tab.SETTINGS) }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(width = 52.dp, height = 40.dp).clip(RoundedCornerShape(50))
        .background(if (selected) C.Lila else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (selected) C.Ink else Color.White.copy(alpha = 0.75f), modifier = Modifier.size(21.dp))
    }
}
