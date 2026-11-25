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
        if (kind !in PromptText.kinds || title.isEmpty()) return null
        return Prompt(title, language, "", box, f[4], kind)
    }

    /** Uno al azar del tipo pedido que no se haya usado; si ya se usaron todos, vuelve a empezar. */
    fun pick(ctx: Context, kind: String, used: Set<String>): Prompt = candidates(ctx, kind, used, 1).first()

    /** Varios al azar del tipo pedido, sin repetir los ya usados mientras se pueda. */
    fun candidates(ctx: Context, kind: String, used: Set<String>, n: Int): List<Prompt> {
        val list = all(ctx).filter { it.kind == kind }
        return list.filter { it.title.lowercase() !in used }.ifEmpty { list }.shuffled().take(n)
    }
}
