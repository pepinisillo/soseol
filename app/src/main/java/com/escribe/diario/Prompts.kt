package com.escribe.diario

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

class NoModelException : Exception("Descarga primero a la musa.")

object Prompts {
    val kinds = PromptText.kinds
    /** Un tipo al azar entre los que están encendidos en «Tu musa». */
    fun randomKind(store: Store) = store.enabledKinds().random()

    /** Si este tipo debe pedirse a la musa: su origen es «Musa» y hay una IA lista; si no, sale de la lista. */
    fun usesMusa(ctx: Context, store: Store, kind: String) = store.origin(kind) == "musa" && available(ctx, store)
    fun label(kind: String) = PromptText.label(kind)

    /** ¿Hay alguna IA lista para pedirle un reto? */
    fun available(ctx: Context, store: Store) = store.claudeOn() || LocalModel.isReady(ctx, store.model())

    /** ¿Ya está en una historia? El título completo o, en los retos de tres palabras, alguna de ellas. */
    private fun repeats(p: Prompt, avoid: Set<String>) =
        p.title.lowercase() in avoid || p.title.split(" · ").any { it.trim().lowercase() in avoid }

    /**
     * Pide el reto a Claude (si está activado y hay API key) o al modelo del teléfono. Nunca devuelve algo
     * que ya esté en una historia: si la IA lo repite, se le pide otro (por eso puede tardar el doble).
     */
    suspend fun generate(ctx: Context, store: Store, kind: String, onProgress: (Float, String) -> Unit): Prompt {
        val avoid = store.storyTitles()
        val recent = store.entries().map { it.title }   // los más recientes primero
        if (store.claudeOn()) {
            repeat(2) { attempt ->
                if (attempt > 0) onProgress(0.03f, "Esa ya la usaste; pidiendo otra…")
                val p = fromClaude(store, kind, recent, onProgress)
                if (!repeats(p, avoid)) return p.also { store.markUsed(it.title) }
            }
            error("Claude repitió algo que ya usaste. Intenta de nuevo o usa la lista.")
        }
        val m = store.model()
        if (!LocalModel.isReady(ctx, m)) throw NoModelException()
        // Aquí solo se llega con origen «Musa»: el modelo crea el reto; las palabras las da de memoria (la persona aceptó el aviso).
        repeat(3) {
            val text = LocalModel.generate(ctx, m, PromptText.askLocal(kind, store.musaInstructions, store.interests, recent),
                when (kind) { "random" -> 40; "palabra", "japonesa" -> 120; "raras" -> 200; else -> 90 }, onProgress)
            PromptText.parseLocal(text, kind)?.takeIf { !repeats(it, avoid) }
                ?.let { store.markUsed(it.title); onProgress(1f, "¡Listo!"); return it }
        }
        error("La musa no logró formular el reto. Intenta de nuevo o usa la lista.")
    }

    /** Un reto al instante de la lista hecha a mano, sin IA. */
    fun fromList(ctx: Context, store: Store, kind: String = randomKind(store)): Prompt =
        Words.pick(ctx, kind, store.usedTitles, store.storyTitles()).also { store.markUsed(it.title) }

    private suspend fun fromClaude(store: Store, kind: String, avoid: List<String>, onProgress: (Float, String) -> Unit): Prompt = coroutineScope {
        // Claude no informa avance: el anillo avanza por tiempo y se completa al llegar la respuesta.
        val ticker = launch { var p = 0.03f; while (true) { onProgress(p, "Consultando a Claude…"); p += (0.92f - p) * 0.04f; delay(200) } }
        try {
            withContext(Dispatchers.IO) { askClaude(store, kind, avoid) }
                ?.also { onProgress(1f, "¡Listo!") }
                ?: error("Claude no respondió. Revisa tu API key o tu conexión.")
        } finally { ticker.cancelAndJoin() }
    }

    private fun askClaude(store: Store, kind: String, avoid: List<String>): Prompt? = runCatching {
        val ask = PromptText.ask(kind, store.musaInstructions, store.interests, LocalDate.now().toString(), avoid)
        val body = JSONObject().put("model", store.model).put("max_tokens", 600)
            .put("messages", org.json.JSONArray().put(JSONObject().put("role", "user").put("content", ask)))
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 90000; c.doOutput = true
        c.setRequestProperty("content-type", "application/json")
        c.setRequestProperty("x-api-key", store.apiKey)
        c.setRequestProperty("anthropic-version", "2023-06-01")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
        val text = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("content").getJSONObject(0).getString("text")
        PromptText.parse(text, kind)
    }.getOrNull()
}
