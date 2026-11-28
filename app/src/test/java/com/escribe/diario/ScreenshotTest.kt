package com.escribe.diario

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Renderiza las pantallas reales a PNG (sin emulador). Datos de ejemplo. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var store: Store
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    // Retos tal como salen de assets/lista.txt: arriba la palabra, la frase resumida o el tema; en el recuadro, lo demás.
    private val prompt = Prompt("Komorebi", "japonés", "", "la luz del sol que se filtra entre las hojas", "", "palabra")
    private val frase = Prompt("Llueven cuerdas", "francés", "", "Il pleut des cordes", "«Llueven cuerdas»: llueve a cántaros", "frase")
    private val tema = Prompt("Viaje en el tiempo", "", "", "ir o volver a otra época", "", "tema")

    private fun cover(name: String): String {
        val out = File(ctx.filesDir, "covers/$name").apply { parentFile!!.mkdirs() }
        javaClass.getResourceAsStream("/covers/$name")!!.use { i -> out.outputStream().use { i.copyTo(it) } }
        return out.absolutePath
    }

    private val sample = """Mara llegó tarde, como siempre, y la luz ya se colaba oblicua entre los pinos del patio. Se quedó quieta en el umbral, con la carta arrugada en la mano.

Había practicado mil formas de decirlo, pero ahora, bajo ese temblor dorado de hojas, todas le parecieron demasiado grandes. Respiró. Dio un paso hacia Ren, que ni siquiera había levantado la vista del libro, y dejó que el sol hablara primero."""

    private fun seed(includeToday: Boolean) {
        ctx.getSharedPreferences("escribe", Context.MODE_PRIVATE).edit().clear().commit()
        C.mode = "claro"; BlockerService.connected = false
        store = Store(ctx)
        store.minWords = 50   // mientras se agregan, para que las historias de ejemplo cuenten como terminadas
        store.savePrompt(prompt)
        val t = LocalDate.now()
        store.addEntry("Meraki", "griego", "Un personaje cocina con todo el cariño.", "x ".repeat(150), null, false, t.minusDays(5), 1)
        store.addEntry("Hiraeth", "galés", "Un regreso a casa que se siente como despedida.", "x ".repeat(210), null, false, t.minusDays(4), 2)
        store.addEntry("Mi OC en la lluvia", "", "", "x ".repeat(120), cover("rain.jpg"), true, t.minusDays(3), 3)
        store.addEntry("Kintsugi", "japonés", "Una amistad rota que se arregla.", "x ".repeat(180), cover("cups.jpg"), false, t.minusDays(2), 4)
        store.addEntry("Saudade", "portugués", "Extrañar un lugar al que no se puede volver.", "x ".repeat(312), cover("sea.jpg"), false, t.minusDays(1), 5)
        if (includeToday) store.addEntry("Komorebi", "japonés", prompt.theme, sample, cover("forest.jpg"), false, t, 6, seconds = 754)
        store.minWords = 200
    }

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { EscribeTheme { Box(Modifier.fillMaxSize().background(C.Bg)) { content() } } }
        rule.waitForIdle()
        captureScreenRoboImage("build/shots/$name.png")
    }

    @Test fun a_home_bloqueado() { seed(false); shot("1_inicio") {
        Box(Modifier.fillMaxSize()) { HomeScreen(store, prompt, false, true, 0, {}, {}, {}, {}); BottomBar(Tab.HOME, Modifier.align(Alignment.BottomCenter)) {} }
    } }

    @Config(qualifiers = "w411dp-h1500dp-xxhdpi")
    @Test fun a1_home_completo() { seed(true); shot("1g_inicio_completo") {
        HomeScreen(store, prompt, false, false, 0, {}, {}, {}, {})
    } }

    @Test fun a2_home_frase() { seed(true); val f = frase; shot("1b_inicio_frase") {
        Box(Modifier.fillMaxSize()) { HomeScreen(store, f, false, false, 0, {}, {}, {}, {}); BottomBar(Tab.HOME, Modifier.align(Alignment.BottomCenter)) {} }
    } }

    @Test fun a3_home_tema() { seed(true); val t = tema; shot("1c_inicio_tema") {
        Box(Modifier.fillMaxSize()) { HomeScreen(store, t, false, false, 0, {}, {}, {}, {}); BottomBar(Tab.HOME, Modifier.align(Alignment.BottomCenter)) {} }
    } }

    @Test fun a4_home_sin_modelo() { seed(true); shot("1d_inicio_sin_modelo") {
        Box(Modifier.fillMaxSize()) { HomeScreen(store, null, false, false, 0, {}, {}, {}, {}); BottomBar(Tab.HOME, Modifier.align(Alignment.BottomCenter)) {} }
    } }

    @Test fun a5_generando() { seed(true); shot("1e_generando") {
        Box(Modifier.fillMaxSize()) {
            HomeScreen(store, null, true, false, 0, {}, {}, {}, {}); BottomBar(Tab.HOME, Modifier.align(Alignment.BottomCenter)) {}
            WorkDialog(Work("Buscando inspiración", "Escribiendo tu reto…", 0.62f)) {}
        }
    } }

    @Test fun a6_descargando() { seed(true); shot("1f_descargando") {
        Box(Modifier.fillMaxSize()) {
            HomeScreen(store, null, true, false, 0, {}, {}, {}, {}); BottomBar(Tab.HOME, Modifier.align(Alignment.BottomCenter)) {}
            WorkDialog(Work("Descargando a tu musa", "Gemma 4 E4B · solo esta vez", 0.41f, "1.5 / 3.7 GB", cancellable = true)) {}
        }
    } }

    @Test fun b_escribir() { seed(false); store.draft = sample; store.draftCover = cover("forest.jpg"); shot("2_escribir") {
        WriteScreen(store, prompt, null, {}, {})
    } }

    @Test fun c_escribir_vacio() { seed(false); store.draft = "Mara llegó tarde, como siempre, y la luz ya se colaba entre los pinos del patio."; shot("3_escribir_sin_minimo") {
        WriteScreen(store, prompt, null, {}, {})
    } }

    @Test fun c2_cerca_del_limite() { seed(false); store.draft = (sample + "\n\n").repeat(7).split(Regex("\\s+")).take(388).joinToString(" "); shot("3b_cerca_del_limite") {
        WriteScreen(store, prompt, null, {}, {})
    } }

    @Test fun d_felicitacion() { seed(true); shot("4_felicitacion") { Celebrate(445, 6) {} } }

    @Test fun e_historial() { seed(true); shot("5_historias") {
        Box(Modifier.fillMaxSize()) { HistoryScreen(store, 0) {}; BottomBar(Tab.HISTORY, Modifier.align(Alignment.BottomCenter)) {} }
    } }

    @Test fun f_leer() { seed(true); shot("6_leer") { ReadScreen(store, 6, {}, {}) } }

    @Test fun a2_tema_propio() { seed(false)
        rule.setContent { EscribeTheme { Box(Modifier.fillMaxSize().background(C.Bg)) { HomeScreen(store, prompt, false, false, 0, {}, {}, {}, {}) } } }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false   // con el diálogo abierto la pantalla nunca queda quieta: el reloj se avanza a mano
        rule.onNodeWithText("Escribir sobre un tema propio").performClick()
        rule.mainClock.advanceTimeBy(1000)
        captureScreenRoboImage("build/shots/1h_tema_propio.png")
    }

    @Config(qualifiers = "w411dp-h1950dp-xxhdpi")
    @Test fun h_tu_musa() { seed(true); store.interests = "fantasía, misterio y un poco de romance"; shot("8_tu_musa") {
        MusaScreen(store) {}
    } }

    @Config(qualifiers = "w411dp-h1500dp-xxhdpi")
    @Test fun g2_ajustes_claude() { seed(true); store.useClaude = true; shot("7b_ajustes_con_claude") {
        SettingsScreen(store) {}
    } }

    @Config(qualifiers = "w411dp-h1500dp-xxhdpi")
    @Test fun g_ajustes() { seed(true); BlockerService.connected = true; shot("7_ajustes") {
        Box(Modifier.fillMaxSize()) { SettingsScreen(store) {}; BottomBar(Tab.SETTINGS, Modifier.align(Alignment.BottomCenter)) {} }
    } }

    // Modo oscuro
    @Test fun o1_inicio_oscuro() { seed(true); C.mode = "oscuro"; shot("9a_oscuro_inicio") {
        Box(Modifier.fillMaxSize()) { HomeScreen(store, prompt, false, false, 0, {}, {}, {}, {}); BottomBar(Tab.HOME, Modifier.align(Alignment.BottomCenter)) {} }
    } }
    @Test fun o2_escribir_oscuro() { seed(false); C.mode = "oscuro"; store.draft = sample; shot("9b_oscuro_escribir") {
        WriteScreen(store, prompt, null, {}, {})
    } }
    @Test fun o3_historias_oscuro() { seed(true); C.mode = "oscuro"; shot("9c_oscuro_historias") {
        Box(Modifier.fillMaxSize()) { HistoryScreen(store, 0) {}; BottomBar(Tab.HISTORY, Modifier.align(Alignment.BottomCenter)) {} }
    } }
    @Test fun o4_leer_oscuro() { seed(true); C.mode = "oscuro"; shot("9d_oscuro_leer") { ReadScreen(store, 6, {}, {}) } }
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi")
    @Test fun o5_ajustes_oscuro() { seed(true); C.mode = "oscuro"; BlockerService.connected = true; shot("9e_oscuro_ajustes") {
        SettingsScreen(store) {}
    } }
}
