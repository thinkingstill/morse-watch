package com.wearmos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MorseCodecTest {

    @Test
    fun everyTableCharRoundTrips() {
        for (ch in MorseTable.supported) {
            val code = MorseTable.code(ch)!!
            assertEquals("字符 $ch 的码 $code 反查失败", ch, MorseTable.char(code))
        }
    }

    @Test
    fun classicSampleEncodesCorrectly() {
        assertEquals("... --- ...", MorseCodec.encode("SOS"))
        assertEquals(".... . .-.. .-.. ---", MorseCodec.encode("hello"))
        assertEquals(
            ".... . .-.. .-.. --- / .-- --- .-. .-.. -..",
            MorseCodec.encode("hello world"),
        )
    }

    @Test
    fun decodeToleratesManySeparatorStyles() {
        assertEquals("SOS", MorseCodec.decode("... --- ..."))
        assertEquals("S O S", MorseCodec.decode("... / --- / ..."))
        assertEquals("S O S", MorseCodec.decode("...   ---   ..."))
        assertEquals("S O S", MorseCodec.decode("...|---|..."))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val text = "HELLO WORLD 123"
        assertEquals(text, MorseCodec.decode(MorseCodec.encode(text)))
    }

    @Test
    fun caseInsensitive() {
        assertEquals(MorseCodec.encode("SOS"), MorseCodec.encode("sos"))
        assertEquals("SOS", MorseCodec.decode(MorseCodec.encode("sos")))
    }

    @Test
    fun unknownSequenceYieldsPlaceholder() {
        assertEquals("?", MorseCodec.decode("........"))
    }

    @Test
    fun unsupportedCharsAreListed() {
        assertEquals(listOf('中'), MorseCodec.unsupportedChars("AB中C"))
        assertTrue(MorseCodec.unsupportedChars("AB C 123 / .").isEmpty())
    }

    @Test
    fun decodeCodeHandlesSingleToken() {
        assertEquals('K', MorseCodec.decodeCode(" -.- "))
        assertEquals(null, MorseCodec.decodeCode("........"))
    }
}
