package com.example.btkeyboard.bluetooth

import com.example.btkeyboard.model.KeyAction
import com.example.btkeyboard.model.ModifierKey
import com.example.btkeyboard.model.SpecialKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HidReportEncoderTest {

    private val encoder = HidReportEncoder()

    @Test
    fun encodesUppercaseCharacterWithShift() {
        val result = encoder.encode(KeyAction.Text("A"), emptySet())

        assertEquals(0, result.unmappedCount)
        assertEquals(2, result.reports.size)
        assertEquals(HidReportEncoder.REPORT_ID_KEYBOARD, result.reports.first().reportId)
        assertEquals(0x02, result.reports.first().payload[0].toInt())
        assertEquals(0x04, result.reports.first().payload[2].toInt())
        assertEquals(0x00, result.reports.last().payload[2].toInt())
    }

    @Test
    fun encodesMediaKeyAsConsumerReport() {
        val result = encoder.encode(KeyAction.Special(SpecialKey.VOL_UP), emptySet())

        assertEquals(0, result.unmappedCount)
        assertEquals(2, result.reports.size)
        assertTrue(result.reports.all { it.reportId == HidReportEncoder.REPORT_ID_CONSUMER })
        assertEquals(0xE9, result.reports.first().payload[0].toInt() and 0xFF)
    }

    @Test
    fun buildsModifierBitmask() {
        val mask = encoder.modifierMask(setOf(ModifierKey.CTRL, ModifierKey.ALT))

        assertEquals(0x05, mask)
    }

    @Test
    fun encodesMouseMoveReport() {
        val reports = encoder.encodeMouseMove(dx = 12, dy = -4, buttonsMask = 0x01)

        assertEquals(1, reports.size)
        val report = reports.single()
        assertEquals(HidReportEncoder.REPORT_ID_MOUSE, report.reportId)
        assertEquals(0x01, report.payload[0].toInt() and 0xFF)
        assertEquals(12, report.payload[1].toInt())
        assertEquals(-4, report.payload[2].toInt())
        assertEquals(0, report.payload[3].toInt())
    }

    @Test
    fun splitsLargeMouseDeltaIntoMultipleReports() {
        val reports = encoder.encodeMouseMove(dx = 300, dy = 0, buttonsMask = 0x00)

        assertEquals(3, reports.size)
        assertTrue(reports.all { it.reportId == HidReportEncoder.REPORT_ID_MOUSE })
        assertEquals(127, reports[0].payload[1].toInt())
        assertEquals(127, reports[1].payload[1].toInt())
        assertEquals(46, reports[2].payload[1].toInt())
    }

    @Test
    fun encodesMouseScrollWithClamping() {
        val reports = encoder.encodeMouseScroll(wheel = -190, buttonsMask = 0x02)

        assertEquals(2, reports.size)
        assertEquals(0x02, reports[0].payload[0].toInt() and 0xFF)
        assertEquals(-127, reports[0].payload[3].toInt())
        assertEquals(-63, reports[1].payload[3].toInt())
    }
}
