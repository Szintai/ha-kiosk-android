package hu.szintai.hakiosk.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakePhraseTest {
    private val phrases = "hey jarvis, hé jarvis, hej jarvis"

    private fun command(text: String): String? =
        WakePhrase.find(text, phrases)?.let { text.substring(it.second).trim(' ', ',', '.') }

    @Test
    fun `extracts the command after the wake phrase`() {
        assertEquals("kapcsold fel a lámpát", command("Hey Jarvis, kapcsold fel a lámpát"))
    }

    @Test
    fun `accent and case insensitive`() {
        assertEquals("hangosítsd fel", command("HÉ JARVIS hangosítsd fel"))
    }

    @Test
    fun `phrase alone yields an empty command`() {
        assertEquals("", command("hej jarvis"))
    }

    @Test
    fun `no wake phrase means no match`() {
        assertNull(WakePhrase.find("kapcsold fel a lámpát", phrases))
    }

    @Test
    fun `phrase in the middle still splits correctly`() {
        assertEquals("lámpa", command("na hey jarvis lámpa"))
    }

    @Test
    fun `blank phrase list never matches`() {
        assertNull(WakePhrase.find("hey jarvis", " , "))
    }
}
