package eu.kanade.tachiyomi.ui.reader.translate

/** Cheap script classification of recognised text, used to choose the ML Kit recogniser. */
enum class Script { LATIN, JAPANESE, KOREAN, CHINESE, OTHER }

object ScriptDetector {

    /**
     * Returns the dominant script among the letters of [text],
     * Kana wins over Han because Japanese text mixes both.
     */
    fun detect(text: String): Script {
        var latin = 0
        var kana = 0
        var hangul = 0
        var han = 0
        var other = 0
        text.codePoints().forEach { cp ->
            when {
                cp in 0x41..0x5A || cp in 0x61..0x7A || cp in 0xC0..0x24F -> latin++
                cp in 0x3040..0x30FF || cp in 0x31F0..0x31FF || cp in 0xFF66..0xFF9F -> kana++
                cp in 0xAC00..0xD7AF || cp in 0x1100..0x11FF || cp in 0x3130..0x318F -> hangul++
                cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF -> han++
                Character.isLetter(cp) -> other++
            }
        }
        val total = latin + kana + hangul + han + other
        if (total == 0) return Script.OTHER
        return when {
            kana > 0 && kana + han >= total / 2 -> Script.JAPANESE
            hangul >= total / 3 && hangul >= han -> Script.KOREAN
            han >= total / 2 -> Script.CHINESE
            latin >= total / 2 -> Script.LATIN
            else -> Script.OTHER
        }
    }

    /** Script that a recogniser for the given source language code should read. */
    fun forLanguage(code: String): Script = when (code) {
        "ja" -> Script.JAPANESE
        "ko" -> Script.KOREAN
        "zh" -> Script.CHINESE
        else -> Script.LATIN
    }
}
