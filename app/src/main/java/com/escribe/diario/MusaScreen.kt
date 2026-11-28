package com.escribe.diario

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * «Tu musa»: gustos, qué tipos de reto salen y de dónde, y las instrucciones que recibe la IA.
 * Se abre desde Ajustes. Nada se guarda hasta tocar «Guardar».
 */
@Composable
fun MusaScreen(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var likes by remember { mutableStateOf(store.interests) }
    var off by remember { mutableStateOf(store.kindsOff) }
    val origins = remember { mutableStateMapOf<String, String>().apply { PromptText.kinds.forEach { put(it, store.origin(it)) } } }
    var instructions by remember { mutableStateOf(store.musaInstructions.ifBlank { PromptText.DEFAULT_INSTRUCTIONS }) }
    var advanced by remember { mutableStateOf(false) }
    var askMusa by remember { mutableStateOf<String?>(null) }   // tipo de palabras para el que se pidió «Musa»
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
        .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.Rounded.ArrowBack, onBack)
            Spacer(Modifier.width(12.dp))
            H("Tu musa", 32)
        }

        Section("Tus gustos") {
            Text("Lo que escribas aquí decide de qué van tus retos.", color = C.Muted, fontSize = 13.sp)
            LineField(likes, { likes = it }, "Ej.: fanfics de mi fandom, misterio, humor", singleLine = false)
            if (likes.isBlank()) Text("Vacío: la musa te dará ${PromptText.NO_LIKES}.", color = C.LilaDeep, fontSize = 12.sp)
        }

        Section("Tipos de reto") {
            Text("El reto del día sale al azar entre los que actives.", color = C.Muted, fontSize = 13.sp)
            PromptText.kindInfo.forEachIndexed { i, k ->
                val on = k.id !in off
                val last = on && PromptText.kinds.count { it !in off } == 1   // no se puede apagar el último
                if (i > 0) HorizontalDivider(color = C.Line)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(k.name, fontWeight = FontWeight.SemiBold, color = if (on) C.Ink else C.Muted)
                            if (k.isNew) { Spacer(Modifier.width(8.dp)); Tag("NUEVO", C.Lila, C.OnLila) }
                        }
                        Text(k.desc, color = C.Muted, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(on, { off = if (it) off - k.id else off + k.id }, enabled = !last,
                        colors = switchColors())
                }
                if (on) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Origen", Modifier.weight(1f), color = C.Muted, fontSize = 13.sp)
                    Segmented(listOf("lista" to "Lista", "musa" to "Musa"), origins[k.id] ?: k.origin) { o ->
                        // En los tipos de palabras, la musa tiene contras: antes de cambiar se avisa.
                        if (o == "musa" && k.id in WORD_KINDS && origins[k.id] != "musa") askMusa = k.id else origins[k.id] = o
                    }
                }
            }
            Text("Lista: palabras y frases verificadas, sin errores. Musa: más variedad, pero el modelo del teléfono puede equivocarse. " +
                "Sin IA descargada, todo sale de la lista. Nunca se repite algo que ya usaste en una historia.",
                color = C.Muted, fontSize = 11.sp, lineHeight = 15.sp)
        }

        // Instrucciones: escondidas por defecto; solo para quien quiera cambiar el tono o agregar reglas.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(C.Card).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clickable { advanced = !advanced }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    H("Instrucciones de la musa", 19)
                    Text("Opcional. Cambia el tono o agrega reglas a tu gusto.", color = C.Muted, fontSize = 13.sp)
                }
                Icon(if (advanced) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = C.Muted)
            }
            if (advanced) {
                LineField(instructions, { instructions = it }, "Instrucciones", singleLine = false)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Si lo dejas vacío se usa el predeterminado.", Modifier.weight(1f), color = C.Muted, fontSize = 11.sp)
                    Row(Modifier.clip(RoundedCornerShape(50)).clickable { instructions = PromptText.DEFAULT_INSTRUCTIONS }
                        .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Refresh, null, tint = C.LilaDeep, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Restaurar", color = C.LilaDeep, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                // Lo que de verdad recibe la musa, armado en vivo con los gustos y las instrucciones de arriba.
                val (head, tail) = PromptText.preview(instructions, likes)
                Text("Así le llega a la musa (ejemplo de un tema):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = C.Ink)) { append(head) }
                    withStyle(SpanStyle(color = C.Muted)) { append(tail) }
                }, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Soft).padding(12.dp),
                    fontSize = 12.sp, lineHeight = 17.sp)
                Text("Lo gris lo pone la app para poder leer la respuesta; no se puede cambiar.", color = C.Muted, fontSize = 11.sp)
            }
        }

        askMusa?.let { k -> MusaWordsDialog(onUse = { origins[k] = "musa"; askMusa = null }, onKeep = { askMusa = null }) }

        Pill("Guardar", {
            store.interests = likes.trim()
            store.kindsOff = off
            origins.forEach { (k, o) -> store.setOrigin(k, o) }
            // Si quedó igual al de fábrica se guarda vacío, para que siga al de fábrica si algún día cambia.
            store.musaInstructions = instructions.trim().takeIf { it != PromptText.DEFAULT_INSTRUCTIONS } ?: ""
            Toast.makeText(ctx, "Tu musa quedó guardada.", Toast.LENGTH_SHORT).show()
            onBack()
        }, Modifier.fillMaxWidth())
    }
}

/** Tipos de reto que son palabras: por defecto salen de la lista y pasarlos a «Musa» pide confirmar. */
private val WORD_KINDS = setOf("palabra", "japonesa", "raras")

/** Aviso con los contras de usar la musa para palabras. */
@Composable
private fun MusaWordsDialog(onUse: () -> Unit, onKeep: () -> Unit) = AlertDialog(
    onDismissRequest = onKeep, containerColor = C.Bg, shape = RoundedCornerShape(28.dp),
    title = { H("¿Palabras de la musa?", 24) },
    text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("La lista trae palabras reales y revisadas, y sale al instante.", fontWeight = FontWeight.SemiBold)
            Text("• La musa te da la palabra de memoria y a veces se confunde. En pruebas con la IA del teléfono, Gemma 4 E4B " +
                "dio palabras que no existen o mal escritas en casi la mitad de las veces en coreano y japonés; la 12B se " +
                "confundió poco, pero repetía mucho las mismas (como Komorebi).", fontSize = 14.sp)
            Text("• Tarda más: unos segundos con la E4B y casi un minuto con la 12B.", fontSize = 14.sp)
            Text("• Con Claude se equivoca mucho menos, pero necesita internet y tu API key.", fontSize = 14.sp)
            Text("• Nada que ya usaste en una historia se repite: si la musa propone algo repetido, se le pide otra cosa " +
                "y la espera puede ser el doble.", fontSize = 14.sp)
        }
    },
    confirmButton = { Pill("Usar musa", onUse, height = 44.dp) },
    dismissButton = { TextButton(onClick = onKeep) { Text("Quedarme con lista", color = C.Ink) } },
)

/** Selector de dos o más opciones en una cápsula (la elegida va en negro). */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) =
    Row(Modifier.clip(RoundedCornerShape(50)).background(C.Soft).padding(3.dp)) {
        options.forEach { (id, label) ->
            val sel = id == selected
            Box(Modifier.clip(RoundedCornerShape(50)).background(if (sel) C.Fill else Color.Transparent)
                .clickable { onSelect(id) }.padding(horizontal = 16.dp, vertical = 7.dp)) {
                Text(label, color = if (sel) C.OnFill else C.Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
        }
    }
