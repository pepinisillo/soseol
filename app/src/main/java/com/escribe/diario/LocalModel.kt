package com.escribe.diario

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/** Un modelo que corre en el teléfono. [loadSecs] es lo que tarda en despertar, aprox., para animar el progreso. */
data class ModelSpec(val id: String, val name: String, val blurb: String, val file: String, val size: Long, val loadSecs: Float) {
    val url get() = "https://huggingface.co/litert-community/${file.removeSuffix(".litertlm")}-litert-lm/resolve/main/$file"
}

/** Gemma 4 corriendo en el teléfono con LiteRT-LM. Cada modelo se descarga una sola vez. */
object LocalModel {
    val models = listOf(
        ModelSpec("e4b", "Gemma 4 E4B", "Rápida · unos segundos por reto", "gemma-4-E4B-it.litertlm", 3_659_530_240L, 6f),
        ModelSpec("12b", "Gemma 4 12B", "Mejor calidad · más lenta, puede tardar minutos", "gemma-4-12B-it.litertlm", 6_883_278_368L, 60f),
    )
    fun spec(id: String) = models.firstOrNull { it.id == id } ?: models.first()

    private fun dir(ctx: Context) = (ctx.getExternalFilesDir(null) ?: ctx.filesDir).apply { mkdirs() }
    private fun file(ctx: Context, m: ModelSpec) = File(dir(ctx), m.file)
    private fun part(ctx: Context, m: ModelSpec) = File(dir(ctx), "${m.file}.part")

    fun isReady(ctx: Context, m: ModelSpec) = file(ctx, m).length() == m.size
    fun partialBytes(ctx: Context, m: ModelSpec) = part(ctx, m).length()
    fun delete(ctx: Context, m: ModelSpec) { file(ctx, m).delete(); part(ctx, m).delete() }

    fun gb(bytes: Long) = "%.1f".format(bytes / 1e9)

    /** Descarga con reanudación: si se corta, la siguiente vez sigue donde se quedó. */
    suspend fun download(ctx: Context, m: ModelSpec, onProgress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        val part = part(ctx, m)
        var have = part.length()
        if (dir(ctx).usableSpace < m.size - have + 300_000_000L)
            error("Necesitas ${gb(m.size - have + 300_000_000L)} GB libres en el teléfono.")
        val c = (URL(m.url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true; connectTimeout = 20_000; readTimeout = 60_000
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        when (c.responseCode) {
            206 -> {}
            200 -> have = 0
            else -> error("No se pudo descargar (HTTP ${c.responseCode}).")
        }
        onProgress(have)
        FileOutputStream(part, have > 0).use { out ->
            c.inputStream.use { inp ->
                val buf = ByteArray(1 shl 20)
                var last = have
                while (true) {
                    ensureActive()
                    val n = inp.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    have += n
                    if (have - last >= 8_000_000L) { last = have; onProgress(have) }
                }
            }
        }
        if (part.length() != m.size) error("La descarga se cortó. Toca «Continuar descarga» para seguir.")
        part.renameTo(file(ctx, m))
        onProgress(m.size)
    }

    private fun open(ctx: Context, m: ModelSpec): Engine {
        // Contexto corto: los retos son breves y así usa mucha menos memoria (importante para la 12B).
        fun make(b: Backend) = Engine(EngineConfig(modelPath = file(ctx, m).absolutePath, backend = b,
            maxNumTokens = 1024, cacheDir = ctx.cacheDir.path))
            .apply { initialize() }
        // La GPU es mucho más rápida; si este teléfono no la soporta, se usa el CPU.
        return try { make(Backend.GPU()) } catch (t: Throwable) { make(Backend.CPU()) }
    }

    /**
     * Genera texto. El progreso es real: 0–25 % mientras el modelo despierta (tiempo estimado)
     * y 25–98 % según el texto que ya escribió frente al esperado ([expectedChars]).
     */
    suspend fun generate(ctx: Context, m: ModelSpec, prompt: String, expectedChars: Int, onProgress: (Float, String) -> Unit): String =
        withContext(Dispatchers.Default) {
            val engine = coroutineScope {
                val ticker = launch {
                    val t0 = System.currentTimeMillis()
                    while (true) {
                        val secs = (System.currentTimeMillis() - t0) / 1000f
                        onProgress(0.25f * (1f - kotlin.math.exp(-secs / m.loadSecs)), "Despertando a la musa…")
                        delay(200)
                    }
                }
                try { open(ctx, m) } finally { ticker.cancelAndJoin() }
            }
            try {
                ensureActive()   // si cancelaron mientras cargaba, no se pone a escribir
                val conv = engine.createConversation(ConversationConfig(samplerConfig = SamplerConfig(64, 0.95, 1.0, Random.nextInt(1, 1_000_000))))
                try {
                    val sb = StringBuilder()
                    onProgress(0.25f, "Escribiendo tu reto…")
                    conv.sendMessageAsync(prompt).collect { m ->
                        m.contents.contents.filterIsInstance<Content.Text>().forEach { sb.append(it.text) }
                        onProgress(0.25f + 0.73f * (sb.length.toFloat() / expectedChars).coerceAtMost(1f), "Escribiendo tu reto…")
                    }
                    sb.toString()
                } finally { conv.close() }
            } finally { engine.close() }   // libera la memoria; se vuelve a cargar en el siguiente reto
        }
}
