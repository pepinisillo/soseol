package com.escribe.diario

import android.content.Context

/**
 * Lista de retos hecha a mano (assets/lista.txt): palabras reales, frases y temas.
 * Se usa con el botón «Lista» y para que la IA elija palabras reales (el modelo local las inventa).
 */
object Words {
    private var cache: List<Prompt>? = null

    fun all(ctx: Context): List<Prompt> = cache
        ?: ctx.assets.open("lista.txt").bufferedReader().useLines { lines -> lines.mapNotNull(::parseLine).toList() }
            .also { cache = it }

    /** Una línea «tipo|título|idioma|recuadro|nota» → Prompt. Las vacías y los comentarios (#) dan null. */
    fun parseLine(line: String): Prompt? {
        if (line.isBlank() || line.trimStart().startsWith("#")) return null
        val f = line.split("|").map { it.trim() } + List(5) { "" }
        val (kind, title, language, box) = f
        // «rara» no es un tipo de reto: son palabras extra solo para el reto de tres palabras raras.
        if ((kind !in PromptText.kinds && kind != "rara") || title.isEmpty()) return null
        return Prompt(title, language, "", box, if (PromptText.isTrio(kind)) PromptText.TRIO_NOTE else f[4], kind)
    }

    /**
     * Uno al azar del tipo pedido. Nunca uno de [avoid] (lo que ya está en una historia) mientras quede otro,
     * y de preferencia uno que no se haya mostrado ([used]).
     */
    fun pick(ctx: Context, kind: String, used: Set<String>, avoid: Set<String> = emptySet()): Prompt =
        candidates(ctx, kind, used, 1, avoid).first()

    /** Varios al azar del tipo pedido, con las mismas reglas que [pick]. */
    fun candidates(ctx: Context, kind: String, used: Set<String>, n: Int, avoid: Set<String> = emptySet()): List<Prompt> = when (kind) {
        // La palabra japonesa sale de las palabras de la lista que son en japonés.
        "japonesa" -> fresh(all(ctx).filter { it.kind == "palabra" && it.language.startsWith("japonés") }, used, n, avoid)
            .map { it.copy(kind = "japonesa") }
        // Tres palabras raras en español: se arman con tres palabras reales de la lista, con su significado.
        "raras" -> List(n) {
            val words = fresh(all(ctx).filter { (it.kind == "palabra" || it.kind == "rara") && it.language.startsWith("español") }, used, 3, avoid)
            PromptText.trio(words.map { it.title }, words.joinToString("\n") { "${it.title.lowercase()}: ${it.theme}" }, "raras")
        }
        else -> fresh(all(ctx).filter { it.kind == kind }, used, n, avoid)
    }

    /** Al azar: primero se quitan los que ya están en una historia; luego, mientras se pueda, los ya mostrados. */
    private fun fresh(list: List<Prompt>, used: Set<String>, n: Int, avoid: Set<String>) =
        list.filter { it.title.lowercase() !in avoid }.ifEmpty { list }
            .let { l -> l.filter { it.title.lowercase() !in used }.ifEmpty { l } }.shuffled().take(n)
}
