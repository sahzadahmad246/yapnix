package com.yapnix.game.gameplay

import java.text.Normalizer
import java.util.Locale

/** Local gameplay feedback only; this is not authoritative scoring. */
object AnswerMatcher {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ")
        .trim()

    fun matches(recognized: String, acceptedAnswers: List<String>): Boolean {
        val heard = normalize(recognized)
        return heard.isNotEmpty() && acceptedAnswers.any { normalize(it) == heard }
    }
}
