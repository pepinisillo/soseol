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

    /** Pide el reto a Claude (si está activado y hay API key) o al modelo del teléfono. */
    suspend fun generate(ctx: Context, store: Store, kind: String, onProgress: (Float, String) -> Unit): Prompt {
        if (store.claudeOn()) return fromClaude(store, kind, onProgress).also { store.markUsed(it.title) }
        val m = store.model()
        if (!LocalModel.isReady(ctx, m)) throw NoModelException()
        // El modelo local inventa palabras: las tres palabras raras salen directo de la lista (palabras reales con su significado).
        if (kind == "raras") return Words.pick(ctx, kind, store.usedTitles).also { store.markUsed(it.title); onProgress(1f, "¡Listo!") }
        // Para palabra y japonesa, la IA elige entre unas reales de la lista según los gustos; no las inventa.
        val pickWord = kind == "palabra" || kind == "japonesa"
        val options = if (pickWord) Words.candidates(ctx, kind, store.usedTitles, 12) else emptyList()
        repeat(2) {
            val text = LocalModel.generate(ctx, m, PromptText.askLocal(kind, options, store.musaInstructions, store.interests),
                when { pickWord -> 25; kind == "random" -> 40; else -> 90 }, onProgress)
            PromptText.parseLocal(text, kind, options)?.let { store.markUsed(it.title); onProgress(1f, "¡Listo!"); return it }
        }
        // Si no eligió bien una palabra, igual sirve cualquiera de las candidatas.
        if (options.isNotEmpty()) return options.random().also { store.markUsed(it.title) }
        error("La musa no logró formular el reto. Intenta de nuevo.")
    }

    /** Un reto al instante de la lista hecha a mano, sin IA. */
    fun fromList(ctx: Context, store: Store, kind: String = randomKind(store)): Prompt =
        Words.pick(ctx, kind, store.usedTitles).also { store.markUsed(it.title) }

    private suspend fun fromClaude(store: Store, kind: String, onProgress: (Float, String) -> Unit): Prompt = coroutineScope {
        // Claude no informa avance: el anillo avanza por tiempo y se completa al llegar la respuesta.
        val ticker = launch { var p = 0.03f; while (true) { onProgress(p, "Consultando a Claude…"); p += (0.92f - p) * 0.04f; delay(200) } }
        try {
            withContext(Dispatchers.IO) { askClaude(store, kind) }
                ?.also { onProgress(1f, "¡Listo!") }
                ?: error("Claude no respondió. Revisa tu API key o tu conexión.")
        } finally { ticker.cancelAndJoin() }
    }

    private fun askClaude(store: Store, kind: String): Prompt? = runCatching {
        val ask = PromptText.ask(kind, store.musaInstructions, store.interests, LocalDate.now().toString())
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
