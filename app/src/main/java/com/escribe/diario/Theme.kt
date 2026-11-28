package com.escribe.diario

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colores de la app. Cambian con el modo oscuro: como [mode] es estado de Compose, al cambiarlo
 * se vuelve a pintar todo con la otra paleta.
 */
object C {
    /** «claro», «oscuro» o «sistema» (lo elegido en Ajustes). */
    var mode by mutableStateOf("claro")
    /** Si el teléfono está en modo oscuro (para «sistema»); lo pone la actividad al arrancar. */
    var systemDark by mutableStateOf(false)
    val dark get() = mode == "oscuro" || (mode == "sistema" && systemDark)
    private fun pick(light: Long, night: Long) = Color(if (dark) night else light)

    val Bg get() = pick(0xFFF7F4FA, 0xFF16141C)        // blanco lavanda / casi negro
    val Ink get() = pick(0xFF1D1A24, 0xFFF1EDF7)       // texto: casi negro con un toque ciruela / casi blanco
    val Ink2 get() = pick(0xFF2F2B38, 0xFF2A2633)      // círculos de días sobre fondo oscuro
    val Lila = Color(0xFFC6B3F2)                       // morado pastel (acento principal, igual en los dos)
    val LilaDeep get() = pick(0xFF7657BD, 0xFFB9A3F0)  // morado para texto y detalles
    val LilaSoft get() = pick(0xFFEDE6FB, 0xFF2B2540)
    val Peach = Color(0xFFF5DCC7)
    val Sky = Color(0xFFD5E5EC)
    val Card get() = pick(0xFFFFFFFF, 0xFF211E28)
    val Muted get() = pick(0xFF7D7889, 0xFF9E98AB)
    val Line get() = pick(0xFFE6E0EE, 0xFF322E3B)
    val Soft get() = pick(0xFFECE6F5, 0xFF2A2633)      // fondo de botones secundarios (sin contorno)

    /** Texto oscuro sobre fondos claros fijos (la tarjeta lila, botones blancos): igual en los dos modos. */
    val OnLila = Color(0xFF1D1A24)
    /** Fondos que siempre son oscuros: la semana, avisos, diálogos de la musa. */
    val Night get() = pick(0xFF1D1A24, 0xFF0F0E14)
    /** Botón principal: negro en claro, lila en oscuro. */
    val Fill get() = if (dark) Lila else Color(0xFF1D1A24)
    val OnFill get() = if (dark) OnLila else Color.White
    /** Barra de abajo. */
    val Bar get() = pick(0xFF1D1A24, 0xFF2A2733)
}

/** Interruptores con los colores de la app (en oscuro, la palanca es oscura sobre lila). */
@Composable
fun switchColors() = SwitchDefaults.colors(checkedTrackColor = C.Fill, checkedThumbColor = if (C.dark) C.Night else C.Lila,
    uncheckedTrackColor = C.Soft, uncheckedThumbColor = C.Muted, uncheckedBorderColor = C.Line)

val Display = FontFamily(
    Font(R.font.playfair_medium, FontWeight.Medium), Font(R.font.playfair_semibold, FontWeight.SemiBold),
    Font(R.font.playfair_italic, FontWeight.Normal, androidx.compose.ui.text.font.FontStyle.Italic),
    Font(R.font.playfair_medium_italic, FontWeight.Medium, androidx.compose.ui.text.font.FontStyle.Italic),
    Font(R.font.playfair_semibold_italic, FontWeight.SemiBold, androidx.compose.ui.text.font.FontStyle.Italic),
)
val Body = FontFamily(
    Font(R.font.dmsans_regular, FontWeight.Normal), Font(R.font.dmsans_medium, FontWeight.Medium),
    Font(R.font.dmsans_semibold, FontWeight.SemiBold), Font(R.font.dmsans_bold, FontWeight.Bold),
)
val Reading = FontFamily(Font(R.font.lora_regular, FontWeight.Normal), Font(R.font.lora_medium, FontWeight.Medium))

private fun TextStyle.b() = copy(fontFamily = Body)

@Composable
fun EscribeTheme(content: @Composable () -> Unit) {
    val t = Typography()
    MaterialTheme(
        colorScheme = if (C.dark) darkColorScheme(primary = C.Lila, onPrimary = C.OnLila, secondary = C.Lila,
            background = C.Bg, surface = C.Bg, onSurface = C.Ink, onBackground = C.Ink, surfaceContainerHigh = C.Card)
        else lightColorScheme(primary = C.Ink, onPrimary = Color.White, secondary = C.Lila,
            background = C.Bg, surface = C.Bg, onSurface = C.Ink, onBackground = C.Ink),
        typography = Typography(
            t.displayLarge.b(), t.displayMedium.b(), t.displaySmall.b(), t.headlineLarge.b(), t.headlineMedium.b(),
            t.headlineSmall.b(), t.titleLarge.b(), t.titleMedium.b(), t.titleSmall.b(), t.bodyLarge.b(),
            t.bodyMedium.b(), t.bodySmall.b(), t.labelLarge.b(), t.labelMedium.b(), t.labelSmall.b(),
        ),
        content = { CompositionLocalProvider(LocalContentColor provides C.Ink, content = content) },
    )
}

@Composable
fun H(text: String, size: Int = 28, color: Color = Color.Unspecified, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, italic: Boolean = false) =
    Text(text, modifier, color = color, fontFamily = Display, fontWeight = FontWeight.Medium, fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
        fontSize = size.sp, lineHeight = (size * 1.2).sp, maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)

@Composable
fun Pill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
         bg: Color = C.Fill, fg: Color = C.OnFill, height: Dp = 54.dp, icon: ImageVector? = null,
         hPad: Dp = 22.dp, textSize: Int = 16) {
    Row(
        modifier.height(height).clip(RoundedCornerShape(50))
            .background(if (enabled) bg else C.Line)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = hPad),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (enabled) fg else C.Muted, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = if (enabled) fg else C.Muted, fontWeight = FontWeight.SemiBold, fontSize = textSize.sp, letterSpacing = 0.2.sp)
    }
}

@Composable
fun GhostPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) =
    Pill(text, onClick, modifier, bg = C.Soft, fg = C.Ink, height = 50.dp, icon = icon)

@Composable
fun RoundIcon(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, bg: Color = if (C.dark) C.Soft else C.Fill, fg: Color = if (C.dark) C.Ink else C.OnFill, size: Dp = 42.dp) =
    Box(modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }

/** Etiqueta pequeña rellena, de esquinas suaves (sin contorno). */
@Composable
fun Tag(text: String, bg: Color = C.Night, fg: Color = Color.White) =
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.8.sp)
    }

/** Pestaña de texto con subrayado (filtros). */
@Composable
fun TextTab(text: String, selected: Boolean, onClick: () -> Unit) =
    Column(Modifier.clickable(onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.Start) {
        Text(text, color = if (selected) C.Ink else C.Muted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 15.sp)
        Spacer(Modifier.height(5.dp))
        Box(Modifier.height(2.dp).width(if (selected) 22.dp else 0.dp).background(C.LilaDeep))
    }

/** Fila de ajustes tipo lista, con flecha. */
@Composable
fun SettingRow(text: String, detail: String? = null, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 15.sp)
        if (detail != null) Text(detail, color = C.Muted, fontSize = 14.sp)
        Icon(androidx.compose.material.icons.Icons.Rounded.KeyboardArrowRight, null, tint = C.Muted)
    }

/** Píldora rellena (sin contorno): oscura si está seleccionada, lavanda suave si no. */
@Composable
fun Chip(text: String, selected: Boolean = false, onClick: (() -> Unit)? = null, dark: Boolean = false) {
    val bg = if (selected) C.Fill else if (dark) C.Ink2 else C.Soft
    val fg = if (selected) C.OnFill else if (dark) Color.White else C.Ink
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) { Text(text, color = fg, fontWeight = FontWeight.Medium, fontSize = 13.sp) }
}
