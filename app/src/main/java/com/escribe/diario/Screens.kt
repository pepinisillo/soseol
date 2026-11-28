package com.escribe.diario

import android.content.Intent
import android.widget.Toast
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale("es", "MX")
fun prettyDate(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy", ES))
}.getOrDefault(iso)

/** Forma de arco (como las fotos de la referencia de viajes). */
private val Arch = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 12, bottomStartPercent = 12)

private val placeholderColors = listOf(C.LilaSoft, C.Ink, C.Peach, C.Sky)

/** Portada de una historia, o un relleno de color con la inicial si no tiene. */
@Composable
fun Cover(entryTitle: String, path: String?, modifier: Modifier, seed: Long, letterSize: Int = 64) {
    val img = remember(path) { Covers.load(path) }
    if (img != null) {
        Image(img, null, modifier, contentScale = ContentScale.Crop)
    } else {
        val bg = placeholderColors[(seed % placeholderColors.size).toInt().let { if (it < 0) -it else it }]
        Box(modifier.background(bg), contentAlignment = Alignment.Center) {
            H(entryTitle.take(1).uppercase(), letterSize, if (bg == C.Ink) C.Lila else C.Ink.copy(alpha = 0.75f), italic = true)
        }
    }
}

/** Alto de los botones de Android, para que las listas terminen por encima de ellos y de la barra. */
@Composable
fun navBottom() = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

// ---------------------------------------------------------------- INICIO

@Composable
fun HomeScreen(store: Store, prompt: Prompt?, busy: Boolean, blocked: Boolean, tick: Int,
               onWrite: () -> Unit, onCustom: (String) -> Unit, onReroll: () -> Unit, onDownload: () -> Unit,
               onList: () -> Unit = {}, onContinue: (Long) -> Unit = {}) {
    val ctx = LocalContext.current
    val aiReady = remember(tick, busy) { Prompts.available(ctx, store) }
    val m = remember(tick) { store.model() }
    val partial = remember(tick, busy) { LocalModel.partialBytes(ctx, m) }
    val done = remember(tick) { store.doneToday() }
    val streak = remember(tick) { store.streak() }
    val days = remember(tick) { store.daysWritten() }
    val total = remember(tick) { store.totalWords() }
    var askCustom by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(today.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)).replaceFirstChar { it.uppercase() },
                    color = C.Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                H("Escribe hoy", 32)
            }
            Box(Modifier.size(48.dp).clip(CircleShape).background(C.Lila), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Create, null, tint = C.Ink)
            }
        }

        if (blocked && !done) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.Ink).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(C.Lila), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Lock, null, tint = C.Ink, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("Tus apps están en pausa hasta que escribas ${store.minWords} palabras. ¡Es cortito, tú puedes!",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (done) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.LilaSoft).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(C.Lila), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = C.Ink)
                }
                Spacer(Modifier.width(12.dp))
                Text("¡Listo por hoy! Ya escribiste y tus apps están libres.", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Reto del día
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(C.Lila).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(if (prompt != null) Prompts.label(prompt.kind) else "RETO DEL DÍA", C.Ink, C.Lila)
                Spacer(Modifier.weight(1f))
                if (!done && prompt != null) {
                    // «Lista» saca un reto al instante de la lista hecha a mano; discreto junto al de la IA.
                    Pill("Lista", { if (!busy) onList() }, bg = Color.White.copy(alpha = 0.45f), fg = C.Ink,
                        height = 34.dp, hPad = 14.dp, textSize = 13)
                    // Rehacer usa el origen de cada tipo (Musa o Lista); sin IA lista, sale de la lista.
                    Spacer(Modifier.width(8.dp)); RoundIcon(Icons.Rounded.Refresh, { if (!busy) onReroll() }, size = 38.dp)
                }
            }
            Spacer(Modifier.height(4.dp))
            when {
                prompt != null -> {
                    H(prompt.title, when (prompt.kind) { "palabra", "japonesa" -> 42; "raras", "random" -> 28; else -> 32 }, italic = true)
                    if (prompt.language.isNotBlank()) Text(prompt.language, fontFamily = Display, fontStyle = FontStyle.Italic, color = C.Ink.copy(alpha = 0.65f), fontSize = 15.sp)
                    if (prompt.meaning.isNotBlank()) Text(prompt.meaning, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    if (prompt.theme.isNotBlank() || prompt.twist.isNotBlank()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.55f)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (prompt.theme.isNotBlank()) Text(prompt.theme, fontSize = 17.sp)
                        if (prompt.twist.isNotBlank()) Text(prompt.twist, fontSize = 14.sp, color = C.Ink.copy(alpha = 0.7f))
                    }
                }
                busy -> { H("La musa está pensando…", 26, italic = true) }
                aiReady -> {
                    H("Aún no hay reto", 26)
                    Text("Pídele a la musa tu reto de hoy.", fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill("Pedir reto", onReroll, height = 46.dp, textSize = 15)
                        Spacer(Modifier.width(10.dp))
                        Pill("De la lista", onList, bg = Color.White.copy(alpha = 0.45f), fg = C.Ink, height = 46.dp, textSize = 15)
                    }
                }
                else -> {
                    H("Conoce a tu musa", 28)
                    Text("Los retos los inventa ${m.name}, una IA que vive en tu teléfono: gratis y sin internet. " +
                        "Solo hay que descargarla una vez (${LocalModel.gb(m.size)} GB, mejor con Wi-Fi). Puedes cambiar de modelo en Ajustes.",
                        fontSize = 15.sp, lineHeight = 21.sp)
                    Spacer(Modifier.height(6.dp))
                    Pill(if (partial > 0) "Continuar descarga (${LocalModel.gb(partial)} / ${LocalModel.gb(m.size)} GB)"
                        else "Descargar musa (${LocalModel.gb(m.size)} GB)", onDownload, height = 46.dp, textSize = 15)
                    Pill("O saca uno de la lista", onList, bg = Color.White.copy(alpha = 0.45f), fg = C.Ink, height = 42.dp, textSize = 14)
                }
            }
        }

        // Si este reto ya tiene una historia guardada sin terminar, se sigue esa en vez de empezar otra.
        val pending = remember(tick, prompt) { prompt?.let { p -> store.entries().firstOrNull { !it.complete && !it.custom && it.title == p.title } } }
        if (pending != null) Pill("Continuar escribiendo", { onContinue(pending.id) }, Modifier.fillMaxWidth(),
            enabled = !busy, icon = Icons.Rounded.Create)
        else if (prompt != null) Pill(if (done) "Escribir otro con este reto" else "Escribir ahora", onWrite, Modifier.fillMaxWidth(),
            enabled = !busy, icon = Icons.Rounded.Create)
        GhostPill("Escribir sobre un tema propio", { askCustom = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)

        // Semana (ref. flat "Daily goal")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(C.Ink).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val week = (0..6).map { monday.plusDays(it.toLong()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                H("Esta semana", 20, Color.White, Modifier.weight(1f))
                Tag("${week.count { it.toString() in days }}/7", C.Lila, C.Ink)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEachIndexed { i, d ->
                    val hit = d.toString() in days
                    val isToday = d == today
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(38.dp).clip(CircleShape)
                            .background(if (hit) C.Lila else if (isToday) C.Lila.copy(alpha = 0.28f) else C.Ink2),
                            contentAlignment = Alignment.Center) {
                            if (hit) Icon(Icons.Rounded.Check, null, tint = C.Ink, modifier = Modifier.size(20.dp))
                            else Text("${d.dayOfMonth}", color = if (isToday) C.Lila else if (d.isAfter(today)) C.Muted else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(listOf("L", "M", "M", "J", "V", "S", "D")[i], color = if (isToday) C.Lila else C.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).height(112.dp).clip(RoundedCornerShape(26.dp)).background(C.LilaSoft).padding(18.dp),
                verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_fire), null, tint = C.LilaDeep, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(8.dp))
                    H("$streak", 34)
                }
                Text("días seguidos", color = C.Ink.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).height(112.dp).clip(RoundedCornerShape(26.dp)).background(C.Card).padding(18.dp),
                verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_pen), null, tint = C.LilaDeep, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(8.dp))
                    H("%,d".format(total), 34)
                }
                Text("palabras escritas", color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (askCustom) {
        var t by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { askCustom = false }, containerColor = C.Bg, shape = RoundedCornerShape(28.dp),
            title = { H("Tu propio tema", 24) },
            text = { LineField(t, { t = it }, "¿Sobre qué quieres escribir?", singleLine = false) },
            confirmButton = { Pill("Escribir", { if (t.isNotBlank()) { askCustom = false; onCustom(t.trim()) } }, height = 44.dp) },
            dismissButton = { TextButton(onClick = { askCustom = false }) { Text("Cancelar", color = C.Ink) } })
    }
}

// ---------------------------------------------------------------- ESCRIBIR

/**
 * Pantalla de escribir. Con [editing] abre una historia ya guardada (para continuarla o corregirla)
 * en vez de una nueva; en ese caso no toca el borrador.
 */
@Composable
fun WriteScreen(store: Store, prompt: Prompt, custom: String?, onSaved: () -> Unit, onBack: () -> Unit, editing: Entry? = null) {
    val ctx = LocalContext.current
    val min = store.minWords; val max = store.maxWords
    var text by remember { mutableStateOf(editing?.body ?: store.draft) }
    var cover by remember { mutableStateOf(if (editing != null) editing.cover else store.draftCover) }
    var celebrate by remember { mutableStateOf(false) }
    val words = wordCount(text)
    // Si ya contaba como terminada al abrirla; para saber si al guardar hay que celebrar.
    val startedComplete = remember { editing?.complete ?: false }

    // Cronómetro: solo corre con el cursor en el recuadro y la app en pantalla (se pausa al salir o apagar la pantalla).
    var seconds by remember { mutableLongStateOf(editing?.seconds ?: store.draftSeconds) }
    var focused by remember { mutableStateOf(false) }
    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState().value
        .isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(focused && resumed) {
        while (focused && resumed) {
            delay(1000); seconds++
            if (editing == null) store.draftSeconds = seconds
        }
    }

    // Autoguardado: una historia nueva se guarda como borrador con cada cambio; una que se está editando,
    // en la historia misma, un momento después de dejar de teclear y al salir de la pantalla.
    fun persist(body: String = text, c: String? = cover) {
        if (editing == null) { store.draft = body; store.draftCover = c } else store.autosaveEntry(editing.id, body.trim(), c, seconds)
    }
    LaunchedEffect(text) { if (editing != null) delay(800); persist() }
    val latest by rememberUpdatedState(text to cover)
    DisposableEffect(Unit) { onDispose { if (editing != null) persist(latest.first, latest.second) } }

    // La portada se guarda en cuanto se elige o se quita.
    fun setCover(c: String?) { Covers.delete(cover); cover = c; persist(c = c) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) Covers.import(ctx, uri)?.let { setCover(it) }
    }
    val isCustom = editing?.custom ?: (custom != null)
    val title = editing?.title ?: custom ?: prompt.title
    val language = editing?.language ?: if (custom == null) prompt.language else ""
    val theme = editing?.theme ?: if (custom == null) prompt.theme else ""

    val pickCover = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    // Con portada, arriba queda oscuro: los íconos de la hora y la batería se ponen blancos; al salir vuelven a negros.
    val view = LocalView.current
    DisposableEffect(cover != null) {
        val window = (view.context as? android.app.Activity)?.window
        val bars = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        bars?.isAppearanceLightStatusBars = cover == null
        onDispose { bars?.isAppearanceLightStatusBars = true }
    }
    // La portada va de fondo de toda la pantalla, aclarada para que se lea encima (como un fondo de terminal).
    Box(Modifier.fillMaxSize()) {
    if (cover != null) {
        Cover(title, cover, Modifier.fillMaxSize(), 0)
        Box(Modifier.fillMaxSize().background(C.Bg.copy(alpha = 0.35f)))
        // Arriba se oscurece poco a poco, como un póster, para que el título blanco se lea sin recuadro.
        Box(Modifier.fillMaxWidth().height(320.dp).background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.8f), 0.55f to Color.Black.copy(alpha = 0.45f), 1f to Color.Transparent)))
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundIcon(Icons.Rounded.ArrowBack, onBack)
            Spacer(Modifier.weight(1f))
            if (cover == null) Pill("Portada", pickCover, bg = C.Soft, fg = C.Ink, height = 36.dp, hPad = 14.dp, textSize = 13, icon = Icons.Rounded.Add)
            else {
                RoundIcon(Icons.Rounded.Edit, pickCover, bg = Color.White, fg = C.Ink, size = 36.dp)
                RoundIcon(Icons.Rounded.Close, { setCover(null) },
                    bg = Color.White, fg = C.Ink, size = 36.dp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Column {
            H(title, 30, if (cover != null) Color.White else C.Ink, italic = true)
            if (theme.isNotBlank()) Text(theme, color = if (cover != null) Color.White.copy(alpha = 0.7f) else C.Muted,
                fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))

        // Oscuro con letra blanca si está activado en Ajustes; con portada, además, transparente para que se vea la imagen.
        val dark = store.darkWriter
        val ink = if (dark) Color.White else C.Ink
        val muted = if (dark) Color.White.copy(alpha = 0.6f) else C.Muted
        val accent = if (dark) C.Lila else C.LilaDeep
        val line = if (dark) Color.White.copy(alpha = 0.15f) else C.Line
        Column(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(26.dp))
            .background(when { dark && cover != null -> C.Ink.copy(alpha = 0.72f); dark -> C.Ink
                cover != null -> C.Card.copy(alpha = 0.8f); else -> C.Card })) {
            BasicTextField(text, { n -> text = if (wordCount(n) > max) truncateWords(n, max) else n },
                Modifier.fillMaxWidth().weight(1f).padding(start = 20.dp, end = 20.dp, top = 18.dp)
                    .onFocusChanged { focused = it.isFocused },
                textStyle = TextStyle(fontFamily = Reading, fontSize = 18.sp, lineHeight = 29.sp, color = ink),
                cursorBrush = SolidColor(accent),
                decorationBox = { inner ->
                    if (text.isEmpty()) Text("Empieza con una sola frase…", fontFamily = Reading, fontStyle = FontStyle.Italic, fontSize = 18.sp, color = muted)
                    inner()
                })
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = line)
            // Barra compacta estilo Twitter: estado · anillo · Guardar
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(when {
                        words < min -> "Faltan ${min - words}"
                        words >= max -> "Llegaste al límite"
                        else -> "Mínimo"
                    }, color = if (words >= min) accent else muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    if (words in min until max) {
                        Spacer(Modifier.width(4.dp))
                        Icon(painterResource(R.drawable.ic_check), null, tint = accent, modifier = Modifier.size(16.dp))
                    }
                }
                Ring(words, min, max, dark)
                Spacer(Modifier.width(6.dp))
                Text("$words/$max", color = muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(24.dp).background(line))
                // Se puede guardar desde una palabra para no perder el reto; si no llega al mínimo, queda «sin terminar».
                Pill("Guardar", {
                    val wasComplete = startedComplete
                    val complete = if (editing != null) {
                        store.updateEntry(editing.id, text.trim(), cover, seconds)?.complete ?: false
                    } else {
                        store.addEntry(title, language, theme, text.trim(), cover, isCustom, seconds = seconds)
                        words >= min
                    }
                    when {
                        complete && !wasComplete -> celebrate = true
                        else -> {
                            Toast.makeText(ctx, if (complete) "Cambios guardados." else
                                "Guardada sin terminar: no cuenta para hoy hasta llegar a $min palabras.", Toast.LENGTH_LONG).show()
                            onSaved()
                        }
                    }
                }, enabled = words >= 1, height = 36.dp, hPad = 18.dp, textSize = 14,
                    bg = if (dark) C.Lila else C.Ink, fg = if (dark) C.Ink else Color.White)
            }
        }
    }
    }

    if (celebrate) Celebrate(words, store.streak()) { celebrate = false; onSaved() }
}

/** Anillo pequeño como el de Twitter: crece y muestra lo que queda al acercarse al límite. */
@Composable
fun Ring(words: Int, min: Int, max: Int, dark: Boolean = false) {
    val left = max - words
    val near = left <= 20
    val progress = (words.toFloat() / max).coerceIn(0f, 1f)
    val color = when { left <= 0 -> Color(0xFFD9534F); near -> Color(0xFFE5A03A); words >= min -> if (dark) C.Lila else C.LilaDeep; else -> if (dark) Color.White else C.Ink }
    Box(Modifier.size(if (near) 30.dp else 22.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            drawArc(if (dark) Color.White.copy(alpha = 0.2f) else C.Line, 0f, 360f, false, style = stroke)
            if (progress > 0f) drawArc(color, -90f, 360f * progress, false, style = stroke)
            // puntito del mínimo
            if (words < min) {
                val a = Math.toRadians((-90.0 + 360.0 * min / max))
                val r = size.minDimension / 2
                drawCircle(if (dark) C.Lila else C.LilaDeep, 2.dp.toPx(), center.copy(x = center.x + r * Math.cos(a).toFloat(), y = center.y + r * Math.sin(a).toFloat()))
            }
        }
        if (near) Text("$left", color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Celebrate(words: Int, streak: Int, onDone: () -> Unit) {
    val msg = remember { listOf("Hoy escribiste $words palabras.", "Poquito a poquito se hacen las grandes historias.", "Otro día cumplido. Qué orgullo.").random() }
    Dialog(onDismissRequest = onDone) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(C.Ink).padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_trophy), null, tint = C.Lila, modifier = Modifier.size(72.dp))
            H("¡Sigue así!", 30, Color.White)
            Text(msg, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center, fontSize = 15.sp)
            Row(Modifier.clip(RoundedCornerShape(50)).background(C.Lila).padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_fire), null, tint = C.Ink, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("$streak ${if (streak == 1) "DÍA" else "DÍAS"} DE RACHA", color = C.Ink, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.8.sp)
            }
            Spacer(Modifier.height(4.dp))
            Pill("Genial", onDone, Modifier.fillMaxWidth(), bg = Color.White, fg = C.Ink, height = 50.dp)
        }
    }
}

// ---------------------------------------------------------------- HISTORIAL

@Composable
fun HistoryScreen(store: Store, tick: Int, onOpen: (Long) -> Unit) {
    val all = remember(tick) { store.entries() }
    var filter by remember { mutableIntStateOf(0) }
    val list = when (filter) { 1 -> all.filter { it.cover != null }; 2 -> all.filter { it.custom }; else -> all }

    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 80.dp + navBottom()),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item(span = { GridItemSpan(2) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                H("Tus historias", 32)
                Text("${all.size} historias · ${"%,d".format(all.sumOf { it.words })} palabras", color = C.Muted, fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Todas", "Con portada", "Temas propios").forEachIndexed { i, s -> Chip(s, filter == i, { filter = i }) }
                }
            }
        }
        if (list.isEmpty()) item(span = { GridItemSpan(2) }) {
            Text("Aún no hay historias aquí. ¡La primera es la más importante!", color = C.Muted, modifier = Modifier.padding(top = 30.dp))
        }
        items(list, key = { it.id }) { e ->
            Column(Modifier.clickable { onOpen(e.id) }) {
                Cover(e.title, e.cover, Modifier.fillMaxWidth().aspectRatio(0.82f).clip(Arch), e.id)
                Spacer(Modifier.height(8.dp))
                H(e.title, 19, maxLines = 2, italic = true)
                Text("${prettyDate(e.date)} · ${e.words} pal.", color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------------------------------------------------------------- LEER (ref. Kidory)

@Composable
fun ReadScreen(store: Store, id: Long, onBack: () -> Unit, onChanged: () -> Unit, onEdit: (Long) -> Unit = {}, tick: Int = 0) {
    val ctx = LocalContext.current
    var e by remember(id, tick) { mutableStateOf(store.entry(id)) }
    var confirmDelete by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val cur = e
        if (uri != null && cur != null) Covers.import(ctx, uri)?.let {
            Covers.delete(cur.cover); store.setCover(cur.id, it); e = store.entry(id); onChanged()
        }
    }
    val entry = e ?: run { onBack(); return }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(if (entry.cover != null) 340.dp else 220.dp)
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))) {
            Cover(entry.title, entry.cover, Modifier.fillMaxSize(), entry.id, letterSize = 120)
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
                RoundIcon(Icons.Rounded.ArrowBack, onBack, bg = Color.White, fg = C.Ink)
                Spacer(Modifier.weight(1f))
                RoundIcon(if (entry.cover == null) Icons.Rounded.Add else Icons.Rounded.Edit,
                    { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, bg = Color.White, fg = C.Ink)
            }
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            H(entry.title, 34, italic = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (entry.custom) "tema propio" else entry.language.ifBlank { "reto del día" }, fontFamily = Display, fontStyle = FontStyle.Italic, color = C.LilaDeep, fontSize = 16.sp)
                Text("  ·  ${entry.words} palabras${if (entry.seconds > 0) "  ·  ${duration(entry.seconds)}" else ""}  ·  ${prettyDate(entry.date)}", color = C.Muted, fontSize = 13.sp)
            }
            if (entry.theme.isNotBlank()) Text(entry.theme, color = C.Muted, fontStyle = FontStyle.Italic, fontSize = 15.sp)
            Pill(if (entry.complete) "Editar" else "Continuar escribiendo", { onEdit(entry.id) }, bg = C.Soft, fg = C.Ink,
                height = 40.dp, hPad = 18.dp, textSize = 14, icon = Icons.Rounded.Create)
            HorizontalDivider(color = C.Line)
            val body = entry.body
            Text(buildAnnotatedString {
                if (body.isNotEmpty()) {
                    withStyle(SpanStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 48.sp, color = C.LilaDeep)) { append(body.take(1)) }
                    append(body.drop(1))
                }
            }, fontFamily = Reading, fontSize = 18.sp, lineHeight = 30.sp, color = C.Ink)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = C.Line)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (entry.cover != null) TextButton(onClick = {
                    Covers.delete(entry.cover); store.setCover(entry.id, null); e = store.entry(id); onChanged()
                }) { Text("Quitar portada", color = C.Ink, fontWeight = FontWeight.Medium) }
                TextButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Rounded.Delete, null, tint = Color(0xFFB4483C), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Eliminar historia", color = Color(0xFFB4483C), fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }

    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, containerColor = C.Bg,
        title = { H("¿Eliminar esta historia?", 22) },
        text = { Text("No se puede deshacer.") },
        confirmButton = { Pill("Eliminar", { Covers.delete(entry.cover); store.delete(entry.id); confirmDelete = false; onChanged(); onBack() }, height = 44.dp) },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar", color = C.Ink) } })
}

// ---------------------------------------------------------------- AJUSTES

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(C.Card).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        H(title, 19); content()
    }
}

/** Campo con contorno suave y esquinas redondeadas (estilo de Ajustes). */
@Composable
fun LineField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
              number: Boolean = false, singleLine: Boolean = true) =
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Ink, unfocusedBorderColor = C.Line,
            focusedLabelColor = C.LilaDeep, unfocusedLabelColor = C.Muted, cursorColor = C.Ink))

/** Botón de píldora con contorno (solo en Ajustes). */
@Composable
fun OutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    Box(modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(50)).background(C.Card)
        .border(1.5.dp, C.Line, RoundedCornerShape(50)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }

@Composable
fun SettingsScreen(store: Store, tick: Int = 0, onDownload: (ModelSpec) -> Unit = {}, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    var min by remember { mutableStateOf(store.minWords.toString()) }
    var max by remember { mutableStateOf(store.maxWords.toString()) }
    var hour by remember { mutableStateOf(store.cutoffHour.toString()) }
    var blocker by remember { mutableStateOf(store.blockerOn) }
    var key by remember { mutableStateOf(store.apiKey) }
    var model by remember { mutableStateOf(store.model) }
    var musa by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(store.blockedApps) }
    var pickApps by remember { mutableStateOf(false) }
    var useClaude by remember { mutableStateOf(store.useClaude) }
    var darkWriter by remember { mutableStateOf(store.darkWriter) }
    var chosen by remember { mutableStateOf(store.modelId) }
    var refresh by remember { mutableIntStateOf(0) }

    if (pickApps) { AppPicker(blocked, { blocked = it }) { pickApps = false }; return }
    if (musa) { MusaScreen(store) { musa = false }; return }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        H("Ajustes", 32)
        Section("Tu meta diaria") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineField(min, { min = it }, "Mínimo", Modifier.weight(1f), number = true)
                LineField(max, { max = it }, "Máximo", Modifier.weight(1f), number = true)
            }
            Text("palabras por historia", color = C.Muted, fontSize = 13.sp)
        }
        Section("Bloqueo de apps") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bloquear si no he escrito", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(blocker, { blocker = it }, colors = SwitchDefaults.colors(checkedTrackColor = C.Ink, checkedThumbColor = C.Lila))
            }
            LineField(hour, { hour = it }, "Desde la hora (0-23)", number = true)
            OutlinePill("Elegir apps (${blocked.size})", { pickApps = true })
            OutlinePill("Activar permiso de accesibilidad", {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
            Text("Accesibilidad → Apps instaladas → soseol → Activar", color = C.Muted, fontSize = 12.sp)
        }
        Section("Musa con IA") {
            Text("Modelo en tu teléfono", fontWeight = FontWeight.SemiBold)
            LocalModel.models.forEach { m ->
                val ready = remember(tick, refresh) { LocalModel.isReady(ctx, m) }
                val partial = remember(tick, refresh) { LocalModel.partialBytes(ctx, m) }
                ModelCard(m, selected = chosen == m.id, ready = ready, partial = partial,
                    onSelect = { chosen = m.id; store.modelId = m.id },
                    onDownload = { onDownload(m) },
                    onDelete = { LocalModel.delete(ctx, m); refresh++ })
            }
            Text("Puedes descargar las dos y cambiar cuando quieras.", color = C.Muted, fontSize = 12.sp)
            HorizontalDivider(color = C.Line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Usar Claude", fontWeight = FontWeight.SemiBold)
                    Text("Más preciso, necesita internet y API key", color = C.Muted, fontSize = 12.sp)
                }
                Switch(useClaude, { useClaude = it }, colors = SwitchDefaults.colors(checkedTrackColor = C.Ink, checkedThumbColor = C.Lila))
            }
            if (useClaude) {
                LineField(key, { key = it }, "API key de Anthropic")
                LineField(model, { model = it }, "Modelo")
                if (key.isBlank()) Text("Sin API key se usa la musa del teléfono.", color = C.Muted, fontSize = 12.sp)
            }
            HorizontalDivider(color = C.Line)
            // Gustos, tipos de reto e instrucciones viven en su propia pantalla para no llenar Ajustes.
            SettingRow("Tu musa", "gustos, retos e instrucciones") { musa = true }
        }
        Section("Al escribir") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recuadro oscuro al escribir", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(darkWriter, { darkWriter = it }, colors = SwitchDefaults.colors(checkedTrackColor = C.Ink, checkedThumbColor = C.Lila))
            }
        }
        Pill("Guardar cambios", {
            val mn = min.toIntOrNull()?.coerceAtLeast(1) ?: 200
            store.minWords = mn
            store.maxWords = (max.toIntOrNull() ?: 400).coerceAtLeast(mn)
            store.cutoffHour = (hour.toIntOrNull() ?: 17).coerceIn(0, 23)
            store.blockerOn = blocker; store.apiKey = key; store.model = model; store.blockedApps = blocked
            store.useClaude = useClaude; store.darkWriter = darkWriter
            onSaved()
        }, Modifier.fillMaxWidth())
    }
}

@Composable
fun AppPicker(selected: Set<String>, onChange: (Set<String>) -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val apps = remember {
        val pm = ctx.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }.filter { it.first != ctx.packageName }.sortedBy { it.second.lowercase() }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.Rounded.ArrowBack, onDone)
            Spacer(Modifier.width(12.dp))
            H("Apps a bloquear", 24, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 80.dp + navBottom())) {
            items(apps) { (pkg, label) ->
                val on = pkg in selected
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card)
                    .clickable { onChange(if (on) selected - pkg else selected + pkg) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Box(Modifier.size(28.dp).clip(CircleShape).background(if (on) C.Lila else C.Soft), contentAlignment = Alignment.Center) {
                        if (on) Icon(Icons.Rounded.Check, null, tint = C.Ink, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- TRABAJANDO

/** Lo que la musa está haciendo: descarga o generación. progress va de 0 a 1. */
data class Work(val title: String, val stage: String, val progress: Float, val detail: String? = null,
                val cancellable: Boolean = false, val cancelLabel: String = "Pausar")

/** Aviso tipo «felicitación» con anillo de progreso. No se cierra tocando fuera, para no interrumpir. */
@Composable
fun WorkDialog(w: Work, onCancel: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        val view = LocalView.current
        DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
        val shown by animateFloatAsState(w.progress.coerceIn(0f, 1f), tween(450), label = "progreso")
        Column(Modifier.padding(horizontal = 12.dp).widthIn(max = 340.dp).fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(C.Ink).padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxSize(), color = C.Lila,
                    trackColor = C.Ink2, strokeWidth = 9.dp, strokeCap = StrokeCap.Round)
                Text("${(shown * 100).toInt()}%", color = Color.White, fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 30.sp)
            }
            Spacer(Modifier.height(2.dp))
            H(w.title, 26, Color.White)
            Text(w.stage, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center, fontSize = 15.sp)
            if (w.detail != null) Text(w.detail, color = C.Lila, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("No cierres la app mientras trabaja.", color = C.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            if (w.cancellable) TextButton(onClick = onCancel) { Text(w.cancelLabel, color = Color.White) }
        }
    }
}

@Composable
fun ErrorDialog(message: String, onRetry: (() -> Unit)?, onClose: () -> Unit) =
    AlertDialog(onDismissRequest = onClose, containerColor = C.Bg, shape = RoundedCornerShape(28.dp),
        title = { H("Algo salió mal", 24) }, text = { Text(message) },
        confirmButton = { if (onRetry != null) Pill("Reintentar", onRetry, height = 44.dp) else Pill("Cerrar", onClose, height = 44.dp) },
        dismissButton = { if (onRetry != null) TextButton(onClick = onClose) { Text("Cerrar", color = C.Ink) } })

/** Tarjeta de un modelo del teléfono: se elige tocándola; abajo, descargar o eliminar. */
@Composable
fun ModelCard(m: ModelSpec, selected: Boolean, ready: Boolean, partial: Long, onSelect: () -> Unit, onDownload: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (selected) C.LilaSoft else C.Bg)
        .clickable(onClick = onSelect).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(if (selected) C.Ink else C.Line), contentAlignment = Alignment.Center) {
                if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(C.Lila))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(m.blurb, color = C.Muted, fontSize = 12.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(when {
                ready -> "Descargada · ${LocalModel.gb(m.size)} GB"
                partial > 0 -> "A medias · ${LocalModel.gb(partial)} / ${LocalModel.gb(m.size)} GB"
                else -> "No descargada · ${LocalModel.gb(m.size)} GB"
            }, Modifier.weight(1f).padding(start = 34.dp), color = if (ready) C.LilaDeep else C.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (ready) TextButton(onClick = onDelete) { Text("Eliminar", color = Color(0xFFB4483C)) }
            else Pill(if (partial > 0) "Continuar" else "Descargar", onDownload, height = 36.dp, hPad = 16.dp, textSize = 13)
        }
    }
}

/** 45 s → «<1 min», 754 s → «12 min», 4000 s → «1 h 6 min». */
fun duration(seconds: Long): String {
    val m = seconds / 60
    return when {
        m < 1 -> "<1 min"
        m < 60 -> "$m min"
        m % 60 == 0L -> "${m / 60} h"
        else -> "${m / 60} h ${m % 60} min"
    }
}
