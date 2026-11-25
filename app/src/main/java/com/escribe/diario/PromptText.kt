package com.escribe.diario

import org.json.JSONObject

/** Reto del día. kind: "palabra", "frase" o "tema". */
data class Prompt(val title: String, val language: String, val meaning: String, val theme: String,
                  val twist: String = "", val kind: String = "palabra")

/** Texto que se le pide al modelo (local o Claude) y lectura de su respuesta. Kotlin puro, sin Android. */
object PromptText {
    val kinds = listOf("palabra", "frase", "tema")

    fun label(kind: String) = when (kind) { "frase" -> "FRASE DEL DÍA"; "tema" -> "TEMA DEL DÍA"; else -> "PALABRA DEL DÍA" }

    fun ask(kind: String, interests: String, minWords: Int, maxWords: Int, date: String): String {
        val likes = likesOf(interests)
        val what = when (kind) {
            "frase" -> "UNA FRASE: una expresión o frase corta, bonita o poética, de un idioma que no sea español (con su " +
                "traducción literal y su sentido), o bien una primera o última línea en español para usar tal cual. " +
                "title = la frase; language = idioma en español y minúsculas (vacío si es una línea en español); " +
                "meaning = traducción y sentido (o \"primera línea\" / \"última línea\"); theme = cómo usarla en una situación concreta."
            "tema" -> "UN TEMA: un tema, tropo o situación para escribir (puede ser un tropo de fanfic, un universo alterno, " +
                "una emoción o un escenario). title = nombre corto y sugerente del tema; language = \"\"; " +
                "meaning = de qué va, en 2 a 5 palabras; theme = la situación concreta para escribir."
            else -> "UNA PALABRA bonita o poética de un idioma que no sea español (varía idioma y estilo, evita las más famosas " +
                "como saudade, hygge, ikigai o komorebi). title = la palabra; language = idioma en español y minúsculas; " +
                "meaning = su significado; theme = una situación concreta para escribir."
        }
        val example = when (kind) {
            "frase" -> "{\"title\":\"Mi manchi\",\"language\":\"italiano\",\"meaning\":\"«me faltas»: así se dice «te extraño»\"," +
                "\"theme\":\"Escribe la nota que alguien deja escondida en la chaqueta del otro antes de un viaje.\",\"twist\":\"La nota se encuentra meses después.\"}"
            "tema" -> "{\"title\":\"Fingir una relación\",\"language\":\"\",\"meaning\":\"fake dating\"," +
                "\"theme\":\"Dos personajes fingen salir en una boda familiar y uno olvida que es fingido.\",\"twist\":\"La abuela lo sabe desde el principio.\"}"
            else -> "{\"title\":\"Mångata\",\"language\":\"sueco\",\"meaning\":\"el reflejo de la luna sobre el agua, como un camino\"," +
                "\"theme\":\"Dos personajes caminan de noche junto a un lago y uno por fin dice algo que callaba.\",\"twist\":\"\"}"
        }
        return "Eres una musa para un hábito diario de escritura breve en español. Dame UN reto para hoy ($date). " +
            "Gustos de la persona: $likes. " + RESPECT + "Hoy el reto es $what " +
            "Debe poder escribirse en $minWords a $maxWords palabras (unos 20 minutos). " +
            "Ajusta la escala a ese espacio: con pocas palabras, un solo momento, imagen o diálogo; con más, " +
            "una escena completa con un pequeño giro. Nunca algo que pida investigar ni una obra larga. " +
            "Reglas: la palabra o frase debe existir de verdad y su traducción debe ser exacta; si no estás segura, elige otra más conocida. " +
            "theme: una sola oración de máximo 25 palabras. twist: máximo 15 palabras, o vacío. meaning: máximo 12 palabras. " +
            "Todo en español salvo la palabra o frase original. " +
            "Ejemplo del formato (no lo repitas, inventa otro distinto): $example " +
            "Responde SOLO con un objeto JSON con esas mismas claves, sin texto antes ni después y sin bloques de código."
    }

    /** Escenarios al azar para que los temas no se repitan (el modelo tiende a irse a la cafetería). */
    val escenarios = listOf("una noche de tormenta", "un tren nocturno", "un festival de verano", "la azotea de un edificio",
        "una biblioteca antigua", "un viaje por carretera", "la playa en invierno", "un mundo de fantasía", "una estación espacial",
        "el último día de clases", "una feria", "un hospital de madrugada", "una cabaña en la montaña", "un concierto",
        "una mudanza", "una boda", "un museo cerrado", "la cocina a medianoche", "un apagón", "un invernadero",
        "un barco", "un pueblo costero", "una tienda de discos", "un parque de diversiones abandonado", "un set de rodaje")

    fun likesOf(interests: String) = interests.ifBlank { "fanfics e historias gay de sus personajes favoritos, romance suave" }

    private const val RESPECT = "Respeta sus gustos: si mencionan romance gay o LGBT, la pareja es del mismo género; " +
        "nunca asumas una pareja hombre-mujer. Si nombran personajes o fandoms, puedes usarlos. "

    /**
     * Mensaje para el modelo del teléfono. Para palabra y frase, la app ya eligió una real ([seed])
     * y el modelo solo inventa la situación y el giro. Para tema, el modelo lo inventa todo.
     */
    fun askLocal(kind: String, seed: Seed?, interests: String, minWords: Int, maxWords: Int): String {
        val base = "Eres una musa para un hábito diario de escritura breve en español. Gustos de la persona: ${likesOf(interests)}. " +
            RESPECT + "El texto debe poder escribirse en $minWords a $maxWords palabras (unos 20 minutos): " +
            "con pocas palabras, un solo momento, imagen o diálogo; con más, una escena completa con un pequeño giro. " +
            "Nada que pida investigar ni una obra larga. "
        return if (seed != null) {
            val what = if (seed.language.isBlank()) "la ${seed.meaning} «${seed.title}» (úsala tal cual)"
                else "la ${if (kind == "frase") "frase" else "palabra"} «${seed.title}» (${seed.language}), que significa: ${seed.meaning}"
            base + "Hoy el reto se basa en $what. Inventa una situación concreta para escribir inspirada en ella. " +
                "theme: una sola oración de máximo 25 palabras. twist: un giro creativo de máximo 15 palabras, o vacío. " +
                "Ejemplo del formato: {\"theme\":\"Dos chicos caminan de noche junto a un lago y uno por fin dice lo que callaba.\",\"twist\":\"\"} " +
                "Responde SOLO con el objeto JSON, sin texto antes ni después y sin bloques de código."
        } else {
            base + "Hoy el reto es UN TEMA: un tropo, universo alterno, emoción o escenario. " +
                "Ambiéntalo en ${escenarios.random()} (no en una cafetería). " +
                "title: nombre corto y sugerente (máximo 5 palabras). meaning: de qué va, en 2 a 5 palabras. " +
                "theme: la situación concreta, una sola oración de máximo 25 palabras. twist: máximo 15 palabras, o vacío. " +
                "Ejemplo del formato (no lo repitas): {\"title\":\"Fingir una relación\",\"meaning\":\"fake dating\"," +
                "\"theme\":\"Dos chicas fingen salir en una boda familiar y una olvida que es fingido.\",\"twist\":\"La abuela lo sabe desde el principio.\"} " +
                "Responde SOLO con el objeto JSON, sin texto antes ni después y sin bloques de código."
        }
    }

    /** Lee la respuesta del modelo local; los datos de la palabra o frase vienen siempre de [seed]. */
    fun parseLocal(text: String, kind: String, seed: Seed?): Prompt? = runCatching {
        val o = JSONObject(cleanJson(text))
        val theme = o.getString("theme").trim()
        require(theme.isNotEmpty())
        val twist = o.optString("twist").trim()
        if (seed != null) Prompt(seed.title, seed.language, seed.meaning, theme, twist, kind)
        else {
            val title = o.getString("title").trim(); require(title.isNotEmpty())
            Prompt(title, "", o.optString("meaning").trim(), theme, twist, "tema")
        }
    }.getOrNull()

    /** Recorta al primer {...} y quita restos sueltos como `,""}` que a veces agrega el modelo. */
    fun cleanJson(text: String): String {
        var j = text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1)
        j = j.replace(Regex(""",\s*""\s*}"""), "}").replace(Regex(""",\s*}"""), "}")
        return j
    }

    /** Extrae el JSON aunque el modelo agregue texto o ``` alrededor. */
    fun parse(text: String, kind: String): Prompt? = runCatching {
        val o = JSONObject(cleanJson(text))
        val title = o.getString("title").trim()
        val theme = o.getString("theme").trim()
        require(title.isNotEmpty() && theme.isNotEmpty())
        var lang = o.optString("language").trim().lowercase()
        if (kind == "tema" || lang in setOf("es", "español", "espanol", "spanish")) lang = ""
        Prompt(title, lang, o.optString("meaning").trim(), theme, o.optString("twist").trim(), kind)
    }.getOrNull()
}
