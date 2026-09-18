package io.github.lottopocket.data

import org.junit.Assert.*
import org.junit.Test

class DrawCodecTest {
    private val valid = """{"schemaVersion":1,"draws":[{"round":1,"date":"2002-12-07","numbers":[10,23,29,33,37,40],"bonus":16,"source":"test"}]}"""
    @Test fun readsRealDrawAndPreservesBonus() {
        val draw = DrawCodec.decode(valid).single()
        assertEquals(listOf(10,23,29,33,37,40), draw.numbers)
        assertEquals(16, draw.bonus)
    }
    @Test fun rejectsDuplicateNumbersRatherThanSilentlyDroppingThem() {
        assertThrows(IllegalArgumentException::class.java) { DrawCodec.decode(valid.replace("10,23", "10,10")) }
    }
    @Test fun rejectsBonusAlreadyInMainNumbers() {
        assertThrows(IllegalArgumentException::class.java) { DrawCodec.decode(valid.replace("\"bonus\":16", "\"bonus\":10")) }
    }
    @Test fun rejectsUnknownSchemaAndMalformedDate() {
        assertThrows(IllegalArgumentException::class.java) { DrawCodec.decode(valid.replace("Version\":1", "Version\":2")) }
        assertThrows(IllegalArgumentException::class.java) { DrawCodec.decode(valid.replace("2002-12-07", "2002-13-07")) }
    }
    @Test fun rejectsFractionalRoundAndBonusInsteadOfTruncating() {
        assertThrows(IllegalArgumentException::class.java) { DrawCodec.decode(valid.replace("\"round\":1", "\"round\":1.5")) }
        assertThrows(IllegalArgumentException::class.java) { DrawCodec.decode(valid.replace("\"bonus\":16", "\"bonus\":16.5")) }
    }
    @Test fun rejectsRoundWhichWouldOverflowStatistics() {
        assertThrows(IllegalArgumentException::class.java) { DrawCodec.decode(valid.replace("\"round\":1", "\"round\":2147483647")) }
    }
}
