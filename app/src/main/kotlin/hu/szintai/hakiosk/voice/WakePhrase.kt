package hu.szintai.hakiosk.voice

import java.text.Normalizer

/** Wake phrase matching for continuous listening. Pure Kotlin, unit-tested. */
object WakePhrase {

    /**
     * Finds the earliest of the [phrases] (comma separated) in [text], ignoring case, accents and
     * punctuation - the Hungarian recogniser may write "hey jarvis" as "hé jarvis".
     * @return (start, end) indices into [text], or null when none is present.
     */
    fun find(text: String, phrases: String): Pair<Int, Int>? {
        val haystack = text.simplified()
        return phrases.split(',')
            .map { it.simplified().trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { phrase -> haystack.indexOf(phrase).takeIf { it >= 0 }?.let { it to it + phrase.length } }
            .minByOrNull { it.first }
    }

    /** Lower-case, accent-free, letters/digits only; keeps the length so indices map back 1:1. */
    internal fun String.simplified(): String = buildString(length) {
        for (c in this@simplified) {
            val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: c
            append(if (base.isLetterOrDigit()) base.lowercaseChar() else ' ')
        }
    }
}
