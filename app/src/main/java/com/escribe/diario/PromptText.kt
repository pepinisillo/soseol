package com.escribe.diario

import org.json.JSONObject

/**
 * Reto del día. kind: "palabra", "frase" o "tema".
 * title va grande arriba; theme es el recuadro (qué significa la palabra, la frase completa o la aclaración del tema);
 * twist es una nota pequeña debajo (la traducción de una frase). meaning solo lo usan retos guardados de versiones viejas.
 */
data class Prompt(val title: String, val language: String, val meaning: String, val theme: String,
                  val twist: String = "", val kind: String = "palabra")

/** Texto que se le pide al modelo (local o Claude) y lectura de su respuesta. Kotlin puro, sin Android. */
object PromptText {
    val kinds = listOf("palabra", "frase", "tema")

    fun label(kind: String) = when (kind) { "frase" -> "FRASE DEL DÍA"; "tema" -> "TEMA DEL DÍA"; else -> "PALABRA DEL DÍA" }

    /**
     * Mensaje para Claude. Solo da la semilla del reto: nada de escenas, personajes ni giros;
     * lo que se escribe lo decide la persona.
     */
    fun ask(kind: String, interests: String, date: String): String {
        val what = when (kind) {
            "frase" -> "UNA FRASE corta para inspirar un texto: puede ser una frase evocadora en español inventada por ti " +
                "(máximo 12 palabras, como «Los últimos minutos del último ciclo»), o un dicho o frase real de otro idioma, " +
                "de preferencia coreano o japonés. title = 2 a 4 palabras en español que la resuman; " +
                "box = la frase completa tal cual (si no usa alfabeto latino, agrega la pronunciación entre paréntesis); " +
                "language = idioma en español y minúsculas, o vacío si es en español; note = traducción si es de otro idioma, o vacío."
            "tema" -> "UN TEMA corto y abierto: un tropo, un universo alterno, una emoción, un lugar o algo cotidiano " +
                "(como «Viaje en el tiempo» o «El vaso de la cafetera ya no sirve»). title = el tema, máximo 6 palabras; " +
                "box = una aclaración de máximo 8 palabras, sin inventar escena ni personajes; language = \"\"; note = \"\"."
            else -> "UNA PALABRA bonita o poco común que exista de verdad: casi siempre de otro idioma, de preferencia coreano o japonés; " +
                "a veces en español (como efímero o duermevela). title = la palabra (en alfabeto latino); " +
                "language = idioma en español y minúsculas, y si usa otra escritura, \" · \" y la palabra original (ej. \"coreano · 설렘\"); " +
                "box = qué significa, máximo 20 palabras; note = \"\"."
        }
        val example = when (kind) {
            "frase" -> "{\"title\":\"La primera nevada\",\"language\":\"coreano\",\"box\":\"첫눈이 오면 만나자 (cheonnuni omyeon mannaja)\",\"note\":\"«Veámonos cuando caiga la primera nevada»\"}"
            "tema" -> "{\"title\":\"Viaje en el tiempo\",\"language\":\"\",\"box\":\"ir o volver a otra época\",\"note\":\"\"}"
            else -> "{\"title\":\"Yunseul\",\"language\":\"coreano · 윤슬\",\"box\":\"el brillo del sol o de la luna sobre el agua que se mueve\",\"note\":\"\"}"
        }
        return "Eres una musa para un hábito diario de escritura breve en español. Dame UN reto para hoy ($date). " +
            "Gustos de la persona: ${likesOf(interests)}. " + RESPECT + "Hoy el reto es $what " +
            "No describas una escena ni des instrucciones: solo la semilla; la persona decide qué escribir. " +
            "La palabra o frase debe existir de verdad y su traducción debe ser exacta; si no estás segura, elige otra más conocida. " +
            "Ejemplo del formato (no lo repitas, inventa otro distinto): $example " +
            "Responde SOLO con un objeto JSON con esas mismas claves, sin texto antes ni después y sin bloques de código."
    }

    /** De qué va el tema que inventa el modelo local, al azar, para que no se repita siempre lo mismo. */
    val temaSeeds = listOf("algo cotidiano que pasa en casa", "un objeto que se rompe o se pierde", "un tropo de fanfic",
        "un universo alterno", "una emoción", "un lugar", "el clima", "una hora del día", "un viaje", "una fecha especial",
        "la escuela o el trabajo", "la comida", "un sonido o un olor", "algo que se espera")

    fun likesOf(interests: String) = interests.ifBlank { "fanfics e historias gay de sus personajes favoritos, romance suave" }

    private const val RESPECT = "Respeta sus gustos: si mencionan romance gay o LGBT, la pareja es del mismo género; " +
        "nunca asumas una pareja hombre-mujer. Si nombran personajes o fandoms, puedes usarlos. "

    /**
     * Mensaje para el modelo del teléfono. Solo da la semilla del reto, nunca una escena ni un giro.
     * Palabra: elige una de [options] (de la lista revisada; el modelo local inventa palabras).
     * Frase y tema: los inventa en español.
     */
    fun askLocal(kind: String, options: List<Prompt>, interests: String): String {
        val base = "Eres una musa para un hábito diario de escritura breve. Gustos de la persona: ${likesOf(interests)}. " + RESPECT +
            "No describas una escena, no inventes personajes ni des instrucciones: solo la semilla; la persona decide qué escribir. "
        val end = " Responde SOLO con el objeto JSON, sin texto antes ni después y sin bloques de código."
        return when (kind) {
            "palabra" -> base + "Elige de esta lista UNA palabra que le pueda inspirar hoy, según sus gustos: " +
                options.joinToString(", ") { it.title } + ". Escríbela exactamente como aparece en la lista. " +
                "Ejemplo del formato: {\"title\":\"${options.first().title}\"}" + end
            "frase" -> base + "Escribe UNA frase corta y evocadora en español para inspirar un texto (máximo 12 palabras). " +
                "title: 2 a 4 palabras que la resuman. Ejemplos del formato (no los repitas): " +
                "{\"title\":\"El último ciclo\",\"phrase\":\"Los últimos minutos del último ciclo.\"} " +
                "{\"title\":\"El café frío\",\"phrase\":\"Esa noche el café se enfrió entre los dos.\"}" + end
            else -> base + "Dame UN tema para escribir, corto y abierto, sobre ${temaSeeds.random()}. " +
                "title: el tema, máximo 6 palabras. line: una aclaración de máximo 8 palabras. Ejemplos del formato (no los repitas): " +
                "{\"title\":\"Viaje en el tiempo\",\"line\":\"ir o volver a otra época\"} " +
                "{\"title\":\"El vaso de la cafetera ya no sirve\",\"line\":\"algo de todos los días se rompe\"}" + end
        }
    }

    /** Lee la respuesta del modelo local. Para palabra, solo acepta una de [options]. */
    fun parseLocal(text: String, kind: String, options: List<Prompt>): Prompt? = runCatching {
        val o = JSONObject(cleanJson(text))
        val title = o.getString("title").trim().trim('«', '»', '"', '.').trim()
        require(title.isNotEmpty())
        when (kind) {
            "palabra" -> options.firstOrNull { it.title.equals(title, ignoreCase = true) }
            "frase" -> o.getString("phrase").trim().takeIf { it.isNotEmpty() }?.let { Prompt(title, "", "", it, "", "frase") }
            else -> Prompt(title, "", "", o.optString("line").trim(), "", "tema")
        }
    }.getOrNull()

    /** Recorta al primer {...} y quita restos sueltos como `,""}` que a veces agrega el modelo. */
    fun cleanJson(text: String): String {
        var j = text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1)
        // La llave va escapada (\}): el regex de Android (ICU) no acepta una } suelta y truena.
        j = j.replace(Regex(""",\s*""\s*\}"""), "}").replace(Regex(""",\s*\}"""), "}")
        return j
    }

    /** Lee la respuesta de Claude; extrae el JSON aunque agregue texto o ``` alrededor. */
    fun parse(text: String, kind: String): Prompt? = runCatching {
        val o = JSONObject(cleanJson(text))
        val title = o.getString("title").trim()
        require(title.isNotEmpty())
        var lang = o.optString("language").trim()
        if (kind == "tema" || lang.lowercase() in setOf("es", "español", "espanol", "spanish")) lang = if (kind == "palabra") "español" else ""
        Prompt(title, lang, "", o.optString("box").trim(), o.optString("note").trim(), kind)
    }.getOrNull()
}
