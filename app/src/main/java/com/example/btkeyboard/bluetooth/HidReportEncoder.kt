package com.example.btkeyboard.bluetooth

import com.example.btkeyboard.model.KeyAction
import com.example.btkeyboard.model.ModifierKey
import com.example.btkeyboard.model.SpecialKey

class HidReportEncoder(
    private val keyMap: KeyMapUsQwerty = KeyMapUsQwerty(),
) {

    data class EncodedReport(
        val reportId: Int,
        val payload: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EncodedReport

            if (reportId != other.reportId) return false
            if (!payload.contentEquals(other.payload)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = reportId
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    data class EncodeResult(
        val reports: List<EncodedReport>,
        val unmappedCount: Int,
    )

    fun encode(action: KeyAction, activeModifiers: Set<ModifierKey>): EncodeResult {
        val baseMask = modifierMask(activeModifiers)
        return when (action) {
            is KeyAction.ModifierToggle -> EncodeResult(emptyList(), unmappedCount = 0)
            is KeyAction.Special -> encodeSpecial(action.key, baseMask)
            is KeyAction.Text -> encodeText(action.value, baseMask)
        }
    }

    fun encodeMouseMove(
        dx: Int,
        dy: Int,
        buttonsMask: Int,
    ): List<EncodedReport> {
        return encodeMouseRelative(dx = dx, dy = dy, wheel = 0, buttonsMask = buttonsMask)
    }

    fun encodeMouseScroll(
        wheel: Int,
        buttonsMask: Int,
    ): List<EncodedReport> {
        return encodeMouseRelative(dx = 0, dy = 0, wheel = wheel, buttonsMask = buttonsMask)
    }

    fun mouseStateReport(buttonsMask: Int): EncodedReport {
        return EncodedReport(
            reportId = REPORT_ID_MOUSE,
            payload = mouseReport(buttonsMask = buttonsMask, dx = 0, dy = 0, wheel = 0),
        )
    }

    fun keyboardModifierStateReport(modifierMask: Int): EncodedReport {
        return EncodedReport(
            reportId = REPORT_ID_KEYBOARD,
            payload = keyboardReport(modifierMask = modifierMask, usage = 0),
        )
    }

    fun keyboardShortcutReports(
        usage: Int,
        modifierMask: Int,
    ): List<EncodedReport> {
        return listOf(
            EncodedReport(REPORT_ID_KEYBOARD, keyboardReport(modifierMask = modifierMask, usage = usage)),
            EncodedReport(REPORT_ID_KEYBOARD, keyboardReport(modifierMask = 0, usage = 0)),
        )
    }

    private fun encodeText(text: String, baseMask: Int): EncodeResult {
        val reports = mutableListOf<EncodedReport>()
        var unmapped = 0

        text.forEach { char ->
            val direct = keyMap.mapChar(char)
            if (direct != null) {
                reports += keyboardPressRelease(direct.usage, baseMask, direct.needsShift)
                return@forEach
            }

            val fallback = keyMap.transliterateChar(char)
            var emitted = false
            fallback.forEach { fallbackChar ->
                val mapped = keyMap.mapChar(fallbackChar)
                if (mapped != null) {
                    reports += keyboardPressRelease(mapped.usage, baseMask, mapped.needsShift)
                    emitted = true
                }
            }
            if (!emitted) {
                unmapped += 1
            }
        }

        return EncodeResult(reports = reports, unmappedCount = unmapped)
    }

    private fun encodeSpecial(key: SpecialKey, baseMask: Int): EncodeResult {
        val keyboard = keyMap.mapSpecial(key)
        if (keyboard != null) {
            return EncodeResult(
                reports = keyboardPressRelease(keyboard.usage, baseMask, keyboard.needsShift),
                unmappedCount = 0,
            )
        }

        val consumerUsage = keyMap.mapConsumerUsage(key)
        if (consumerUsage != null) {
            return EncodeResult(
                reports = consumerPressRelease(consumerUsage),
                unmappedCount = 0,
            )
        }

        return EncodeResult(emptyList(), unmappedCount = 1)
    }

    private fun keyboardPressRelease(
        usage: Int,
        baseMask: Int,
        addShift: Boolean,
    ): List<EncodedReport> {
        val withShift = if (addShift) {
            baseMask or SHIFT_MASK
        } else {
            baseMask
        }

        val press = keyboardReport(withShift, usage)
        val release = keyboardReport(baseMask, 0)
        return listOf(
            EncodedReport(REPORT_ID_KEYBOARD, press),
            EncodedReport(REPORT_ID_KEYBOARD, release),
        )
    }

    private fun consumerPressRelease(usage: Int): List<EncodedReport> {
        val press = byteArrayOf((usage and 0xFF).toByte(), ((usage shr 8) and 0xFF).toByte())
        val release = byteArrayOf(0x00, 0x00)
        return listOf(
            EncodedReport(REPORT_ID_CONSUMER, press),
            EncodedReport(REPORT_ID_CONSUMER, release),
        )
    }

    private fun keyboardReport(modifierMask: Int, usage: Int): ByteArray {
        return byteArrayOf(
            modifierMask.toByte(),
            0x00,
            usage.toByte(),
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
        )
    }

    private fun encodeMouseRelative(
        dx: Int,
        dy: Int,
        wheel: Int,
        buttonsMask: Int,
    ): List<EncodedReport> {
        if (dx == 0 && dy == 0 && wheel == 0) {
            return emptyList()
        }

        val reports = mutableListOf<EncodedReport>()
        var remainingDx = dx
        var remainingDy = dy
        var remainingWheel = wheel

        while (remainingDx != 0 || remainingDy != 0 || remainingWheel != 0) {
            val stepDx = remainingDx.coerceIn(-MAX_RELATIVE_DELTA, MAX_RELATIVE_DELTA)
            val stepDy = remainingDy.coerceIn(-MAX_RELATIVE_DELTA, MAX_RELATIVE_DELTA)
            val stepWheel = remainingWheel.coerceIn(-MAX_RELATIVE_DELTA, MAX_RELATIVE_DELTA)

            reports += EncodedReport(
                reportId = REPORT_ID_MOUSE,
                payload = mouseReport(
                    buttonsMask = buttonsMask,
                    dx = stepDx,
                    dy = stepDy,
                    wheel = stepWheel,
                ),
            )

            remainingDx -= stepDx
            remainingDy -= stepDy
            remainingWheel -= stepWheel
        }

        return reports
    }

    private fun mouseReport(
        buttonsMask: Int,
        dx: Int,
        dy: Int,
        wheel: Int,
    ): ByteArray {
        return byteArrayOf(
            (buttonsMask and 0x07).toByte(),
            dx.coerceIn(-MAX_RELATIVE_DELTA, MAX_RELATIVE_DELTA).toByte(),
            dy.coerceIn(-MAX_RELATIVE_DELTA, MAX_RELATIVE_DELTA).toByte(),
            wheel.coerceIn(-MAX_RELATIVE_DELTA, MAX_RELATIVE_DELTA).toByte(),
        )
    }

    fun modifierMask(activeModifiers: Set<ModifierKey>): Int {
        var mask = 0
        if (ModifierKey.CTRL in activeModifiers) {
            mask = mask or 0x01
        }
        if (ModifierKey.SHIFT in activeModifiers) {
            mask = mask or SHIFT_MASK
        }
        if (ModifierKey.ALT in activeModifiers) {
            mask = mask or 0x04
        }
        if (ModifierKey.META in activeModifiers) {
            mask = mask or 0x08
        }
        return mask
    }

    companion object {
        const val REPORT_ID_KEYBOARD = 1
        const val REPORT_ID_CONSUMER = 2
        const val REPORT_ID_MOUSE = 3
        private const val SHIFT_MASK = 0x02
        private const val MAX_RELATIVE_DELTA = 127

        // Keyboard + consumer control + mouse descriptors.
        val HID_DESCRIPTOR: ByteArray = intArrayOf(
            0x05, 0x01,
            0x09, 0x06,
            0xA1, 0x01,
            0x85, REPORT_ID_KEYBOARD,
            0x05, 0x07,
            0x19, 0xE0,
            0x29, 0xE7,
            0x15, 0x00,
            0x25, 0x01,
            0x75, 0x01,
            0x95, 0x08,
            0x81, 0x02,
            0x95, 0x01,
            0x75, 0x08,
            0x81, 0x03,
            0x95, 0x06,
            0x75, 0x08,
            0x15, 0x00,
            0x25, 0x65,
            0x05, 0x07,
            0x19, 0x00,
            0x29, 0x65,
            0x81, 0x00,
            0xC0,
            0x05, 0x01,
            0x09, 0x02,
            0xA1, 0x01,
            0x85, REPORT_ID_MOUSE,
            0x09, 0x01,
            0xA1, 0x00,
            0x05, 0x09,
            0x19, 0x01,
            0x29, 0x03,
            0x15, 0x00,
            0x25, 0x01,
            0x95, 0x03,
            0x75, 0x01,
            0x81, 0x02,
            0x95, 0x01,
            0x75, 0x05,
            0x81, 0x03,
            0x05, 0x01,
            0x09, 0x30,
            0x09, 0x31,
            0x09, 0x38,
            0x15, 0x81,
            0x25, 0x7F,
            0x75, 0x08,
            0x95, 0x03,
            0x81, 0x06,
            0xC0,
            0xC0,
            0x05, 0x0C,
            0x09, 0x01,
            0xA1, 0x01,
            0x85, REPORT_ID_CONSUMER,
            0x15, 0x00,
            0x26, 0x9C, 0x02,
            0x19, 0x00,
            0x2A, 0x9C, 0x02,
            0x75, 0x10,
            0x95, 0x01,
            0x81, 0x00,
            0xC0,
        ).map { it.toByte() }.toByteArray()
    }
}
