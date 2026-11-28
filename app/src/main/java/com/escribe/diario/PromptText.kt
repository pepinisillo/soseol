package com.escribe.diario

import org.json.JSONObject

/**
 * Reto del día. kind: "palabra", "frase", "tema", "idea", "idea10", "idea5", "japonesa", "raras" o "random".
 * title va grande arriba; theme es el recuadro (qué significa la palabra, la frase completa o la aclaración del tema);
 * twist es una nota pequeña debajo (la traducción de una frase). meaning solo lo usan retos guardados de versiones viejas.
 */
data class Prompt(val title: String, val language: String, val meaning: String, val theme: String,
                  val twist: String = "", val kind: String = "palabra")

/** Texto que se le pide al modelo (local o Claude) y lectura de su respuesta. Kotlin puro, sin Android. */
object PromptText {
    val kinds = listOf("palabra", "frase", "tema", "idea", "idea10", "idea5", "japonesa", "raras", "random")

    /** Tipos que son una idea de escritura (premisa); cuántas palabras debe tener, o null si es libre (una frase). */
    val ideaWords = mapOf("idea" to null, "idea10" to 10, "idea5" to 5)
    fun isIdea(kind: String) = kind in ideaWords
    /** Tipos de «tres palabras que deben aparecer en la historia». */
    fun isTrio(kind: String) = kind == "raras" || kind == "random"
    const val TRIO_NOTE = "Las tres deben aparecer en tu historia."

    /** Nombre, descripción corta y origen de fábrica de cada tipo (para la pantalla «Tu musa»). */
    data class KindInfo(val id: String, val name: String, val desc: String, val origin: String, val isNew: Boolean = false)
    val kindInfo = listOf(
        KindInfo("palabra", "Palabra", "Una palabra bonita de otro idioma o poco común, con su significado.", "lista"),
        KindInfo("japonesa", "Palabra japonesa", "Una palabra en japonés y qué significa.", "lista"),
        KindInfo("frase", "Frase", "Una frase corta para inspirarte; si es de otro idioma, con su traducción.", "lista"),
        KindInfo("tema", "Tema", "Un tropo, universo alterno o situación para escribir.", "musa"),
        KindInfo("idea", "Idea", "Una premisa para empezar, en una sola frase.", "musa"),
        KindInfo("idea10", "Idea en 10 palabras", "Una premisa en exactamente 10 palabras.", "musa"),
        KindInfo("idea5", "Idea en 5 palabras", "Una premisa en exactamente 5 palabras.", "musa"),
        KindInfo("raras", "Tres palabras raras", "Tres palabras poco comunes del español que tienes que usar en tu texto.", "lista", isNew = true),
        KindInfo("random", "Tres palabras al azar", "Tres palabras cualquiera que tienes que usar en tu texto.", "musa", isNew = true),
    )
    fun info(kind: String) = kindInfo.first { it.id == kind }

    /** Instrucciones de fábrica: neutrales, para quien sea. Se pueden cambiar en «Tu musa». */
    const val DEFAULT_INSTRUCTIONS = "Eres una musa para un hábito diario de escritura breve en español. " +
        "Propón retos concretos, cálidos y fáciles de empezar. Sigue los gustos al pie de la letra y no asumas nada que no digan."
    const val NO_LIKES = "un poco de todo (aventura, misterio, humor, fantasía, romance…)"

    /**
     * Inicio de todo mensaje a la musa: las instrucciones (editables) y los gustos.
     * Es la única parte que la persona puede cambiar; lo demás lo pone la app para poder leer la respuesta.
     */
    fun header(instructions: String, interests: String): String {
        val ins = instructions.trim().ifEmpty { DEFAULT_INSTRUCTIONS }.let { if (it.last() in ".!?…") it else "$it." }
        val likes = interests.trim().trimEnd('.')
        return "$ins " + (if (likes.isEmpty()) "La persona no dijo sus gustos: propón $NO_LIKES. " else "Gustos de la persona: $likes. ")
    }

    /** Ejemplo completo de lo que recibe la musa (un reto de tema), para mostrarlo en vivo en «Tu musa». */
    fun preview(instructions: String, interests: String) =
        header(instructions, interests) to askLocalTail("tema", "un lugar")

    fun label(kind: String) = when {
        kind == "frase" -> "FRASE DEL DÍA"
        kind == "tema" -> "TEMA DEL DÍA"
        isIdea(kind) -> "IDEA DEL DÍA"
        isTrio(kind) -> "TRES PALABRAS"
        kind == "japonesa" -> "PALABRA JAPONESA"
        else -> "PALABRA DEL DÍA"
    }

    /** "una frase" / "exactamente 10 palabras" para el texto que se le pide al modelo. */
    private fun ideaLength(kind: String) = ideaWords[kind]?.let { "exactamente $it palabras" } ?: "una sola frase (máximo 25 palabras)"

    /**
     * Mensaje para Claude. Solo da la semilla del reto: nada de escenas, personajes ni giros;
     * lo que se escribe lo decide la persona.
     */
    /** Frase para que la IA no repita lo que ya está en una historia (los más recientes, para no alargar el mensaje). */
    fun avoidLine(avoid: List<String>) =
        if (avoid.isEmpty()) "" else "No repitas nada que ya haya usado en sus historias: ${avoid.take(40).joinToString(", ")}. "

    fun ask(kind: String, instructions: String, interests: String, date: String, avoid: List<String> = emptyList()): String {
        val what = when (kind) {
            "frase" -> "UNA FRASE corta para inspirar un texto: puede ser una frase evocadora en español inventada por ti " +
                "(máximo 12 palabras, como «Los últimos minutos del último ciclo»), o un dicho o frase real de otro idioma " +
                "(si sus gustos mencionan idiomas, prefiérelos). title = 2 a 4 palabras en español que la resuman; " +
                "box = la frase completa tal cual (si no usa alfabeto latino, agrega la pronunciación entre paréntesis); " +
                "language = idioma en español y minúsculas, o vacío si es en español; note = traducción si es de otro idioma, o vacío."
            "idea", "idea10", "idea5" -> "UNA IDEA DE ESCRITURA en ${ideaLength(kind)}: una premisa breve y abierta para un texto corto. " +
                "title = 2 a 4 palabras que la resuman; box = la idea completa en ${ideaLength(kind)}; language = \"\"; note = \"\"."
            "japonesa" -> "UNA PALABRA JAPONESA bonita o poco común que exista de verdad. title = la palabra en rōmaji; " +
                "language = \"japonés · \" y la palabra en su escritura original (ej. \"japonés · 木漏れ日\"); " +
                "box = qué significa, máximo 20 palabras; note = \"\"."
            "raras" -> "TRES PALABRAS POCO COMUNES EN ESPAÑOL que existan de verdad en el diccionario (como efímero, duermevela o alféizar) " +
                "y que deban aparecer obligatoriamente en la historia. title = las tres palabras separadas por \" · \"; " +
                "box = qué significa cada una, en una línea corta por palabra (palabra: significado); language = \"español\"; note = \"\"."
            "random" -> "TRES PALABRAS AL AZAR en español, comunes y sin relación entre sí (como paraguas, sandía y elevador), " +
                "que deban aparecer obligatoriamente en la historia. title = las tres palabras separadas por \" · \"; " +
                "box = \"\"; language = \"\"; note = \"\"."
            "tema" -> "UN TEMA corto y abierto: un tropo, un universo alterno, una emoción, un lugar o algo cotidiano " +
                "(como «Viaje en el tiempo» o «El vaso de la cafetera ya no sirve»). title = el tema, máximo 6 palabras; " +
                "box = una aclaración de máximo 8 palabras, sin inventar escena ni personajes; language = \"\"; note = \"\"."
            else -> "UNA PALABRA bonita o poco común que exista de verdad: casi siempre de otro idioma (si sus gustos mencionan idiomas, prefiérelos); " +
                "a veces en español (como efímero o duermevela). title = la palabra (en alfabeto latino); " +
                "language = idioma en español y minúsculas, y si usa otra escritura, \" · \" y la palabra original (ej. \"coreano · 설렘\"); " +
                "box = qué significa, máximo 20 palabras; note = \"\"."
        }
        val example = when (kind) {
            "frase" -> "{\"title\":\"La primera nevada\",\"language\":\"coreano\",\"box\":\"첫눈이 오면 만나자 (cheonnuni omyeon mannaja)\",\"note\":\"«Veámonos cuando caiga la primera nevada»\"}"
            "tema" -> "{\"title\":\"Viaje en el tiempo\",\"language\":\"\",\"box\":\"ir o volver a otra época\",\"note\":\"\"}"
            "idea" -> "{\"title\":\"La carta sin enviar\",\"language\":\"\",\"box\":\"Alguien encuentra la carta que nunca se atrevió a mandar.\",\"note\":\"\"}"
            "idea10" -> "{\"title\":\"El último tren\",\"language\":\"\",\"box\":\"Dos desconocidos pierden el último tren y caminan hasta amanecer.\",\"note\":\"\"}"
            "idea5" -> "{\"title\":\"La llave\",\"language\":\"\",\"box\":\"Una llave que nadie reconoce.\",\"note\":\"\"}"
            "japonesa" -> "{\"title\":\"Komorebi\",\"language\":\"japonés · 木漏れ日\",\"box\":\"la luz del sol que se filtra entre las hojas\",\"note\":\"\"}"
            "raras" -> "{\"title\":\"Efímero · Duermevela · Alféizar\",\"language\":\"español\",\"box\":\"efímero: que dura muy poco\\nduermevela: el sueño ligero, entre dormido y despierto\\nalféizar: el borde de la ventana\",\"note\":\"\"}"
            "random" -> "{\"title\":\"Paraguas · Sandía · Elevador\",\"language\":\"\",\"box\":\"\",\"note\":\"\"}"
            else -> "{\"title\":\"Yunseul\",\"language\":\"coreano · 윤슬\",\"box\":\"el brillo del sol o de la luna sobre el agua que se mueve\",\"note\":\"\"}"
        }
        return header(instructions, interests) + "Dame UN reto para hoy ($date). Hoy el reto es $what " + avoidLine(avoid) +
            (if (isIdea(kind)) "La idea es solo un punto de partida: no cuentes la historia completa ni des el final. "
             else "No describas una escena ni des instrucciones: solo la semilla; la persona decide qué escribir. ") +
            "La palabra o frase debe existir de verdad y su traducción debe ser exacta; si no estás segura, elige otra más conocida. " +
            "Ejemplo del formato (no lo repitas, inventa otro distinto): $example " +
            "Responde SOLO con un objeto JSON con esas mismas claves, sin texto antes ni después y sin bloques de código."
    }

    /** De qué va el tema que inventa el modelo local, al azar, para que no se repita siempre lo mismo. */
    val temaSeeds = listOf("algo cotidiano que pasa en casa", "un objeto que se rompe o se pierde", "un tropo de historias",
        "un universo alterno", "una emoción", "un lugar", "el clima", "una hora del día", "un viaje", "una fecha especial",
        "la escuela o el trabajo", "la comida", "un sonido o un olor", "algo que se espera")

    /**
     * Mensaje para el modelo del teléfono. Solo da la semilla del reto, nunca una escena ni un giro.
     * Se usa únicamente con origen «Musa»: frases, temas e ideas los crea; las palabras las da de memoria.
     */
    fun askLocal(kind: String, instructions: String, interests: String, avoid: List<String> = emptyList()): String =
        header(instructions, interests) + avoidLine(avoid) + askLocalTail(kind, temaSeeds.random())

    /** La parte fija del mensaje al modelo local: qué tipo de reto pedir y en qué formato contestar. */
    private fun askLocalTail(kind: String, about: String): String {
        val base = "No describas una escena, no inventes personajes ni des instrucciones: solo la semilla; la persona decide qué escribir. "
        val end = " Responde SOLO con el objeto JSON, sin texto antes ni después y sin bloques de código."
        return when (kind) {
            // Solo se llega aquí si la persona eligió «Musa» para palabras (con aviso): el modelo las da de memoria y puede confundirse.
            "palabra", "japonesa" -> base + (if (kind == "japonesa") "Dame UNA palabra japonesa bonita o poco común que exista de verdad. "
                    else "Dame UNA palabra bonita o poco común que exista de verdad: casi siempre de otro idioma " +
                        "(si sus gustos mencionan idiomas, prefiérelos); a veces en español. ") +
                "La palabra debe existir de verdad y su significado debe ser exacto; si no estás segura, elige otra más conocida. " +
                "title: la palabra en alfabeto latino. language: el idioma en español y minúsculas; si usa otra escritura, " +
                "\" · \" y la palabra original. box: qué significa, máximo 20 palabras. Ejemplo del formato (no lo repitas): " +
                (if (kind == "japonesa") "{\"title\":\"Hidamari\",\"language\":\"japonés · 陽だまり\",\"box\":\"el rincón donde se junta el sol\"}"
                 else "{\"title\":\"Yunseul\",\"language\":\"coreano · 윤슬\",\"box\":\"el brillo del sol o de la luna sobre el agua que se mueve\"}") + end
            "raras" -> base + "Dame TRES palabras poco comunes en español que existan de verdad en el diccionario y que deban " +
                "aparecer en la historia, cada una con su significado en pocas palabras. Ejemplo del formato (no lo repitas): " +
                "{\"words\":[{\"w\":\"alféizar\",\"m\":\"el borde de la ventana\"},{\"w\":\"duermevela\",\"m\":\"el sueño ligero\"}," +
                "{\"w\":\"efímero\",\"m\":\"que dura muy poco\"}]}" + end
            "frase" -> base + "Escribe UNA frase corta y evocadora en español para inspirar un texto (máximo 12 palabras). " +
                "title: 2 a 4 palabras que la resuman. Ejemplos del formato (no los repitas): " +
                "{\"title\":\"El último ciclo\",\"phrase\":\"Los últimos minutos del último ciclo.\"} " +
                "{\"title\":\"El café frío\",\"phrase\":\"Esa noche el café se enfrió entre los dos.\"}" + end
            "idea", "idea10", "idea5" -> "No cuentes la historia completa ni des el final: solo el punto de partida; la persona decide qué escribir. " +
                "Escribe UNA idea de escritura en español en ${ideaLength(kind)}: una premisa breve y abierta, sobre $about. " +
                "title: 2 a 4 palabras que la resuman. Ejemplos del formato (no los repitas): " +
                "{\"title\":\"La carta sin enviar\",\"idea\":\"Alguien encuentra la carta que nunca se atrevió a mandar.\"} " +
                "{\"title\":\"La llave\",\"idea\":\"Una llave que nadie reconoce.\"}" + end
            "random" -> base + "Dame TRES palabras al azar en español, comunes y sin relación entre sí, que deban aparecer en la historia. " +
                "Ejemplo del formato (no lo repitas): {\"words\":[\"paraguas\",\"sandía\",\"elevador\"]}" + end
            else -> base + "Dame UN tema para escribir, corto y abierto, sobre $about. " +
                "title: el tema, máximo 6 palabras. line: una aclaración de máximo 8 palabras. Ejemplos del formato (no los repitas): " +
                "{\"title\":\"Viaje en el tiempo\",\"line\":\"ir o volver a otra época\"} " +
                "{\"title\":\"El vaso de la cafetera ya no sirve\",\"line\":\"algo de todos los días se rompe\"}" + end
        }
    }

    /** Lee la respuesta del modelo local. Si no viene en el formato pedido, devuelve null. */
    fun parseLocal(text: String, kind: String): Prompt? = runCatching {
        val o = JSONObject(cleanJson(text))
        if (kind == "raras") {
            val a = o.getJSONArray("words")
            val pairs = (0 until a.length()).map { a.getJSONObject(it) }
                .map { it.getString("w").trim().trim('«', '»', '"', '.').trim() to it.optString("m").trim() }
                .filter { it.first.isNotEmpty() }.distinctBy { it.first.lowercase() }
            require(pairs.size >= 3)
            val three = pairs.take(3)
            return@runCatching trio(three.map { it.first.replaceFirstChar(Char::uppercase) },
                three.joinToString("\n") { "${it.first.lowercase()}: ${it.second}" }, "raras")
        }
        if (kind == "random") {
            val a = o.getJSONArray("words")
            val words = (0 until a.length()).map { a.getString(it).trim().trim('«', '»', '"', '.').trim() }.filter { it.isNotEmpty() }.distinct()
            require(words.size >= 3)
            return@runCatching trio(words.take(3), "", "random")
        }
        val title = o.getString("title").trim().trim('«', '»', '"', '.').trim()
        require(title.isNotEmpty())
        when (kind) {
            // La palabra que dio la musa, con su idioma y su significado.
            "palabra", "japonesa" -> o.optString("box").trim().takeIf { it.isNotEmpty() }?.let { box ->
                    val lang = o.optString("language").trim().ifEmpty { if (kind == "japonesa") "japonés" else "" }
                    Prompt(title.replaceFirstChar(Char::uppercase), lang, "", box, "", kind)
                }
            "idea", "idea10", "idea5" -> o.getString("idea").trim().takeIf { it.isNotEmpty() && fitsLength(it, kind) }
                ?.let { Prompt(title, "", "", it, "", kind) }
            "frase" -> o.getString("phrase").trim().takeIf { it.isNotEmpty() }?.let { Prompt(title, "", "", it, "", "frase") }
            else -> Prompt(title, "", "", o.optString("line").trim(), "", "tema")
        }
    }.getOrNull()

    /** Las ideas de 10 y 5 palabras deben acercarse a ese número (el modelo local cuenta mal; se le da un margen). */
    fun fitsLength(idea: String, kind: String): Boolean {
        val n = ideaWords[kind] ?: return true
        return idea.split(Regex("\\s+")).count { it.any(Char::isLetterOrDigit) } in (n - 2)..(n + 2)
    }

    /** Reto de tres palabras: título «A · B · C», significados (si hay) en el recuadro y la nota de que deben aparecer. */
    fun trio(words: List<String>, meanings: String, kind: String) =
        Prompt(words.joinToString(" · ") { it.replaceFirstChar(Char::uppercase) }, if (kind == "raras") "español" else "", "",
            meanings, TRIO_NOTE, kind)

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
        if (kind == "tema" || isIdea(kind) || isTrio(kind) || lang.lowercase() in setOf("es", "español", "espanol", "spanish"))
            lang = if (kind == "palabra" || kind == "raras") "español" else ""
        val box = o.optString("box").trim()
        if (isIdea(kind)) require(box.isNotEmpty())
        Prompt(title, lang, "", box, if (isTrio(kind)) TRIO_NOTE else o.optString("note").trim(), kind)
    }.getOrNull()
}
