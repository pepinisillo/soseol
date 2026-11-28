package com.escribe.diario

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate


data class Entry(
    val id: Long,
    val date: String,
    val title: String,      // la palabra del día o el tema propio
    val language: String,   // vacío si es tema propio
    val theme: String,
    val body: String,
    val words: Int,
    val cover: String?,     // ruta de la imagen de portada (opcional)
    val custom: Boolean,
    val complete: Boolean = true,   // llegó al mínimo de palabras; solo estas cuentan para el día y la racha
    val seconds: Long = 0,          // tiempo escribiendo: solo cuenta con el cursor en el recuadro
)

fun wordCount(s: String) = s.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

fun truncateWords(s: String, max: Int): String {
    var count = 0; var i = 0
    while (i < s.length) {
        while (i < s.length && s[i].isWhitespace()) i++
        if (i >= s.length) break
        while (i < s.length && !s[i].isWhitespace()) i++
        count++
        if (count == max) return s.substring(0, i)
    }
    return s
}

class Store(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("escribe", Context.MODE_PRIVATE)

    var minWords: Int get() = p.getInt("min", 200); set(v) = p.edit().putInt("min", v).apply()
    var maxWords: Int get() = p.getInt("max", 400); set(v) = p.edit().putInt("max", v).apply()
    var cutoffHour: Int get() = p.getInt("cutoff", 17); set(v) = p.edit().putInt("cutoff", v).apply()
    var blockerOn: Boolean get() = p.getBoolean("blocker", true); set(v) = p.edit().putBoolean("blocker", v).apply()
    var apiKey: String get() = p.getString("apikey", "") ?: ""; set(v) = p.edit().putString("apikey", v.trim()).apply()
    var model: String get() = p.getString("model", "claude-opus-5-5") ?: "claude-opus-5-5"; set(v) = p.edit().putString("model", v.trim()).apply()
    var interests: String get() = p.getString("interests", "") ?: ""; set(v) = p.edit().putString("interests", v).apply()
    var blockedApps: Set<String>
        get() = p.getStringSet("blocked", null) ?: DEFAULT_BLOCKED
        set(v) = p.edit().putStringSet("blocked", v).apply()
    /** Modelo del teléfono elegido en Ajustes ("e4b" o "12b"). */
    var modelId: String get() = p.getString("model_id", "e4b") ?: "e4b"; set(v) = p.edit().putString("model_id", v).apply()
    fun model() = LocalModel.spec(modelId)

    /** Usar Claude en lugar del modelo del teléfono (solo si además hay API key). */
    var useClaude: Boolean get() = p.getBoolean("use_claude", false); set(v) = p.edit().putBoolean("use_claude", v).apply()
    fun claudeOn() = useClaude && apiKey.isNotBlank()
    /** Instrucciones para la musa; vacío = las de fábrica (PromptText.DEFAULT_INSTRUCTIONS). */
    var musaInstructions: String get() = p.getString("musa_instructions", "") ?: ""; set(v) = p.edit().putString("musa_instructions", v).apply()
    /** Tipos de reto apagados en «Tu musa». */
    var kindsOff: Set<String> get() = p.getStringSet("kinds_off", emptySet()) ?: emptySet(); set(v) = p.edit().putStringSet("kinds_off", v).apply()
    /** Tipos que pueden salir; si alguien los apagara todos, se usan todos. */
    fun enabledKinds(): List<String> = PromptText.kinds.filter { it !in kindsOff }.ifEmpty { PromptText.kinds }
    /** De dónde sale cada tipo: "lista" o "musa". */
    fun origin(kind: String): String = p.getString("origin_$kind", null) ?: PromptText.info(kind).origin
    fun setOrigin(kind: String, origin: String) = p.edit().putString("origin_$kind", origin).apply()

    /** Recuadro de escribir oscuro con letra blanca (encendido por defecto). */
    var darkWriter: Boolean get() = p.getBoolean("dark_writer", true); set(v) = p.edit().putBoolean("dark_writer", v).apply()

    /** Palabras y frases ya usadas, para no repetirlas. */
    val usedTitles: Set<String> get() = p.getStringSet("used", emptySet()) ?: emptySet()
    fun markUsed(title: String) = p.edit().putStringSet("used", usedTitles + title.lowercase()).apply()

    var draft: String get() = p.getString("draft", "") ?: ""; set(v) = p.edit().putString("draft", v).apply()
    var draftCover: String? get() = p.getString("draft_cover", null); set(v) = p.edit().putString("draft_cover", v).apply()
    /** Apariencia: «claro», «oscuro» o «sistema». */
    var theme: String get() = p.getString("theme", "claro") ?: "claro"; set(v) = p.edit().putString("theme", v).apply()
    var draftSeconds: Long get() = p.getLong("draft_seconds", 0); set(v) = p.edit().putLong("draft_seconds", v).apply()

    fun savedPrompt(): Prompt? {
        if (p.getString("prompt_date", "") != LocalDate.now().toString()) return null
        return runCatching {
            val o = JSONObject(p.getString("prompt_json", "")!!)
            Prompt(o.getString("word"), o.optString("language"), o.optString("meaning"), o.getString("theme"), o.optString("twist"), o.optString("kind", "palabra"))
        }.getOrNull()
    }

    fun savePrompt(x: Prompt) {
        val o = JSONObject().put("word", x.title).put("kind", x.kind).put("language", x.language).put("meaning", x.meaning)
            .put("theme", x.theme).put("twist", x.twist)
        p.edit().putString("prompt_date", LocalDate.now().toString()).putString("prompt_json", o.toString()).apply()
    }

    fun entries(): List<Entry> = runCatching {
        val a = JSONArray(p.getString("entries", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Entry(
                o.getLong("id"), o.getString("date"), o.getString("title"), o.optString("language"),
                o.optString("theme"), o.getString("body"), o.getInt("words"),
                o.optString("cover").ifBlank { null }, o.optBoolean("custom"), o.optBoolean("complete", true),
                o.optLong("seconds"),
            )
        }
    }.getOrDefault(emptyList()).sortedByDescending { it.id }

    fun entry(id: Long) = entries().firstOrNull { it.id == id }

    private fun write(list: List<Entry>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("date", it.date).put("title", it.title).put("language", it.language)
                .put("theme", it.theme).put("body", it.body).put("words", it.words).put("cover", it.cover ?: "")
                .put("custom", it.custom).put("complete", it.complete).put("seconds", it.seconds))
        }
        p.edit().putString("entries", a.toString()).apply()
    }

    fun addEntry(title: String, language: String, theme: String, body: String, cover: String?, custom: Boolean,
                 date: LocalDate = LocalDate.now(), id: Long = System.currentTimeMillis(), seconds: Long = 0) {
        val words = wordCount(body)
        write(entries() + Entry(id, date.toString(), title, language, theme, body, words, cover, custom, words >= minWords, seconds))
        p.edit().putString("draft", "").remove("draft_cover").remove("draft_seconds").apply()
    }

    /**
     * Guarda los cambios a una historia ya guardada. Si con esto llega al mínimo por primera vez,
     * pasa a contar como escrita hoy (no el día en que se empezó, para no rellenar la racha hacia atrás).
     */
    fun updateEntry(id: Long, body: String, cover: String?, seconds: Long): Entry? {
        val old = entry(id) ?: return null
        val words = wordCount(body)
        val complete = old.complete || words >= minWords
        val e = old.copy(body = body, words = words, cover = cover, complete = complete, seconds = seconds,
            date = if (complete && !old.complete) LocalDate.now().toString() else old.date)
        write(entries().map { if (it.id == id) e else it })
        return e
    }

    /** Autoguardado al editar: guarda texto y portada sin decidir si ya cuenta como terminada (eso pasa al darle Guardar). */
    fun autosaveEntry(id: Long, body: String, cover: String?, seconds: Long) =
        write(entries().map { if (it.id == id) it.copy(body = body, words = wordCount(body), cover = cover, seconds = seconds) else it })

    fun setCover(id: Long, cover: String?) = write(entries().map { if (it.id == id) it.copy(cover = cover) else it })
    fun delete(id: Long) = write(entries().filter { it.id != id })

    /**
     * Lo que ya se usó en alguna historia guardada, en minúsculas: el título completo y, en los retos
     * de tres palabras («A · B · C»), cada palabra por separado. Ningún reto nuevo debe repetirlo.
     */
    fun storyTitles(): Set<String> = entries().flatMap { e -> listOf(e.title) + e.title.split(" · ") }
        .map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    /** Días en que se escribió de verdad: lo guardado sin llegar al mínimo no cuenta ni desbloquea las apps. */
    fun daysWritten(): Set<String> = entries().filter { it.complete }.map { it.date }.toSet()
    fun doneToday(): Boolean = LocalDate.now().toString() in daysWritten()
    fun totalWords(): Int = entries().sumOf { it.words }

    fun streak(): Int {
        val days = daysWritten()
        var d = LocalDate.now(); if (d.toString() !in days) d = d.minusDays(1)
        var n = 0
        while (d.toString() in days) { n++; d = d.minusDays(1) }
        return n
    }

    companion object {
        val DEFAULT_BLOCKED = setOf("com.instagram.android", "com.facebook.katana", "com.twitter.android",
            "com.ss.android.ugc.trill", "com.zhiliaoapp.musically", "com.snapchat.android", "com.reddit.frontpage", "com.tumblr", "com.pinterest")
    }
}
