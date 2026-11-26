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
        val ok = PromptText.parseLocal("""{"title":"Diario","idea":"Encuentras un diario que describe tu semana antes de vivirla."}""", "idea10", emptyList())
        assertNotNull(ok)
        val corta = PromptText.parseLocal("""{"title":"Diario","idea":"Un diario."}""", "idea10", emptyList())
        assertNull(corta)
    }

    @Test fun localRandom() {
        val p = PromptText.parseLocal("""{"words":["paraguas","sandía","elevador"]}""", "random", emptyList())!!
        assertEquals("Paraguas · Sandía · Elevador", p.title)
        assertEquals("random", p.kind)
        assertNull(PromptText.parseLocal("""{"words":["paraguas","paraguas"]}""", "random", emptyList()))
    }

    @Test fun localJaponesaEligeDeLaLista() {
        val opts = Words.candidates(ctx, "japonesa", emptySet(), 12)
        assertTrue(opts.isNotEmpty())
        assertTrue(opts.all { it.kind == "japonesa" && it.language.startsWith("japonés") })
        val p = PromptText.parseLocal("""{"title":"${opts[0].title}"}""", "japonesa", opts)!!
        assertEquals("japonesa", p.kind)
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
