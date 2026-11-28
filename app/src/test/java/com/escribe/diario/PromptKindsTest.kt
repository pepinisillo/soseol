package com.escribe.diario

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Los tipos de reto que vienen de retro_writer: ideas (frase, 10 y 5 palabras), palabra japonesa y tres palabras. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PromptKindsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun etiquetas() {
        assertEquals("IDEA DEL DÍA", PromptText.label("idea10"))
        assertEquals("TRES PALABRAS", PromptText.label("raras"))
        assertEquals("PALABRA JAPONESA", PromptText.label("japonesa"))
        assertEquals("PALABRA DEL DÍA", PromptText.label("palabra"))
    }

    @Test fun claudeIdea() {
        val p = PromptText.parse("""{"title":"La llave","language":"es","box":"Una llave que nadie reconoce.","note":""}""", "idea5")
        assertNotNull(p); p!!
        assertEquals("", p.language)
        assertEquals("Una llave que nadie reconoce.", p.theme)
        assertNull(PromptText.parse("""{"title":"La llave","box":""}""", "idea"))
    }

    @Test fun claudeTresPalabras() {
        val p = PromptText.parse("""```json
{"title":"Efímero · Duermevela · Alféizar","language":"español","box":"efímero: que dura poco","note":"x"}```""", "raras")!!
        assertEquals("español", p.language)
        assertEquals(PromptText.TRIO_NOTE, p.twist)
        val r = PromptText.parse("""{"title":"Paraguas · Sandía · Elevador","language":"","box":"","note":""}""", "random")!!
        assertEquals("", r.language)
        assertEquals(PromptText.TRIO_NOTE, r.twist)
    }

    @Test fun localIdeaRespetaLongitud() {
        val ok = PromptText.parseLocal("""{"title":"Diario","idea":"Encuentras un diario que describe tu semana antes de vivirla."}""", "idea10")
        assertNotNull(ok)
        val corta = PromptText.parseLocal("""{"title":"Diario","idea":"Un diario."}""", "idea10")
        assertNull(corta)
    }

    @Test fun localRandom() {
        val p = PromptText.parseLocal("""{"words":["paraguas","sandía","elevador"]}""", "random")!!
        assertEquals("Paraguas · Sandía · Elevador", p.title)
        assertEquals("random", p.kind)
        assertNull(PromptText.parseLocal("""{"words":["paraguas","paraguas"]}""", "random"))
    }

    @Test fun localPalabraDeLaMusa() {
        // Con origen «Musa», el modelo del teléfono da la palabra de memoria, con su idioma y significado.
        val p = PromptText.parseLocal("""{"title":"hidamari","language":"japonés · 陽だまり","box":"el rincón donde se junta el sol"}""", "japonesa")!!
        assertEquals("Hidamari", p.title)
        assertEquals("japonés · 陽だまり", p.language)
        assertEquals("japonesa", p.kind)
        assertNull(PromptText.parseLocal("""{"title":"Hidamari","box":""}""", "palabra"))   // sin significado no sirve
    }

    @Test fun localRarasDeLaMusa() {
        val p = PromptText.parseLocal("""{"words":[{"w":"alféizar","m":"el borde de la ventana"},{"w":"duermevela","m":"el sueño ligero"},{"w":"efímero","m":"que dura poco"}]}""", "raras")!!
        assertEquals("Alféizar · Duermevela · Efímero", p.title)
        assertEquals(3, p.theme.lines().size)
        assertEquals(PromptText.TRIO_NOTE, p.twist)
    }

    @Test fun listaNoRepiteHistorias() {
        // Lo que ya está en una historia no vuelve a salir de la lista mientras quede otra opción.
        val todas = Words.all(ctx).filter { it.kind == "tema" }.map { it.title.lowercase() }.toSet()
        val queda = todas.first()
        val p = Words.pick(ctx, "tema", emptySet(), todas - queda)
        assertEquals(queda, p.title.lowercase())
    }

    @Test fun listaTieneTodosLosTipos() {
        for (kind in PromptText.kinds) {
            val p = Words.pick(ctx, kind, emptySet())
            assertEquals(kind, p.kind)
            assertTrue("$kind sin título", p.title.isNotBlank())
        }
        val raras = Words.pick(ctx, "raras", emptySet())
        assertEquals(3, raras.title.split(" · ").size)
        assertEquals(3, raras.theme.lines().size)
        assertEquals("español", raras.language)
    }

    @Test fun lasRarasNoSalenComoPalabraDelDia() {
        val raras = Words.all(ctx).filter { it.kind == "rara" }
        assertTrue(raras.size >= 50)
        val titulosRaras = raras.map { it.title }.toSet()
        // Pide todas las candidatas de «palabra»: ninguna puede ser de las «rara».
        val palabras = Words.candidates(ctx, "palabra", emptySet(), 10_000)
        assertTrue(palabras.none { it.title in titulosRaras })
        // Las «rara» sí entran al reto de tres palabras raras.
        val vistas = (1..200).flatMap { Words.pick(ctx, "raras", emptySet()).title.split(" · ") }.toSet()
        assertTrue(vistas.any { it in titulosRaras })
    }

    @Test fun ideasDeLaListaTienenSuLongitud() {
        for (kind in listOf("idea10", "idea5")) {
            val all = Words.all(ctx).filter { it.kind == kind }
            assertTrue(all.isNotEmpty())
            all.forEach { assertTrue("${it.theme} no cumple $kind", PromptText.fitsLength(it.theme, kind)) }
        }
    }
}
