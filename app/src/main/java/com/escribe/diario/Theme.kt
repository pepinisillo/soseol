package com.escribe.diario

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

object C {
    val Bg = Color(0xFFF7F4FA)        // blanco lavanda
    val Ink = Color(0xFF1D1A24)       // casi negro con un toque ciruela
    val Ink2 = Color(0xFF2F2B38)
    val Lila = Color(0xFFC6B3F2)      // morado pastel (acento principal)
    val LilaDeep = Color(0xFF7657BD)  // morado más intenso para texto y detalles
    val LilaSoft = Color(0xFFEDE6FB)
    val Peach = Color(0xFFF5DCC7)
    val Sky = Color(0xFFD5E5EC)
    val Card = Color.White
    val Muted = Color(0xFF7D7889)
    val Line = Color(0xFFE6E0EE)
    val Soft = Color(0xFFECE6F5)     // fondo de botones secundarios (sin contorno)
}

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
        colorScheme = lightColorScheme(primary = C.Ink, onPrimary = Color.White, secondary = C.Lila,
            background = C.Bg, surface = C.Bg, onSurface = C.Ink, onBackground = C.Ink),
        typography = Typography(
            t.displayLarge.b(), t.displayMedium.b(), t.displaySmall.b(), t.headlineLarge.b(), t.headlineMedium.b(),
            t.headlineSmall.b(), t.titleLarge.b(), t.titleMedium.b(), t.titleSmall.b(), t.bodyLarge.b(),
            t.bodyMedium.b(), t.bodySmall.b(), t.labelLarge.b(), t.labelMedium.b(), t.labelSmall.b(),
        ),
        content = content,
    )
}

@Composable
fun H(text: String, size: Int = 28, color: Color = C.Ink, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, italic: Boolean = false) =
    Text(text, modifier, color = color, fontFamily = Display, fontWeight = FontWeight.Medium, fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
        fontSize = size.sp, lineHeight = (size * 1.2).sp, maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)

@Composable
fun Pill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
         bg: Color = C.Ink, fg: Color = Color.White, height: Dp = 54.dp, icon: ImageVector? = null,
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
fun RoundIcon(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, bg: Color = C.Ink, fg: Color = Color.White, size: Dp = 42.dp) =
    Box(modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }

/** Etiqueta pequeña rellena, de esquinas suaves (sin contorno). */
@Composable
fun Tag(text: String, bg: Color = C.Ink, fg: Color = Color.White) =
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
    val bg = if (selected) C.Ink else if (dark) C.Ink2 else C.Soft
    val fg = if (selected || dark) Color.White else C.Ink
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) { Text(text, color = fg, fontWeight = FontWeight.Medium, fontSize = 13.sp) }
}
