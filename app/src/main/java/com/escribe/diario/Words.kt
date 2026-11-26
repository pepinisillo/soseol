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

    /** Uno al azar del tipo pedido que no se haya usado; si ya se usaron todos, vuelve a empezar. */
    fun pick(ctx: Context, kind: String, used: Set<String>): Prompt = candidates(ctx, kind, used, 1).first()

    /** Varios al azar del tipo pedido, sin repetir los ya usados mientras se pueda. */
    fun candidates(ctx: Context, kind: String, used: Set<String>, n: Int): List<Prompt> = when (kind) {
        // La palabra japonesa sale de las palabras de la lista que son en japonés.
        "japonesa" -> fresh(all(ctx).filter { it.kind == "palabra" && it.language.startsWith("japonés") }, used, n)
            .map { it.copy(kind = "japonesa") }
        // Tres palabras raras en español: se arman con tres palabras reales de la lista, con su significado.
        "raras" -> List(n) {
            val words = fresh(all(ctx).filter { (it.kind == "palabra" || it.kind == "rara") && it.language.startsWith("español") }, used, 3)
            PromptText.trio(words.map { it.title }, words.joinToString("\n") { "${it.title.lowercase()}: ${it.theme}" }, "raras")
        }
        else -> fresh(all(ctx).filter { it.kind == kind }, used, n)
    }

    /** Al azar, sin repetir los ya usados mientras se pueda. */
    private fun fresh(list: List<Prompt>, used: Set<String>, n: Int) =
        list.filter { it.title.lowercase() !in used }.ifEmpty { list }.shuffled().take(n)
}
