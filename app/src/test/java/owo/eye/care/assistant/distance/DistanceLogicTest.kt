package owo.eye.care.assistant.distance

import org.junit.Assert.*
import org.junit.Test

class DistanceLogicTest {
    @Test fun closeNeedsTwoAndFarNeedsTwo() {
        val gate = DistanceGate()
        assertFalse(gate.update(24))
        assertTrue(gate.update(24))
        assertTrue(gate.update(30))
        assertFalse(gate.update(30))
    }

    @Test fun middleBandKeepsStateButBreaksConsecutiveCounts() {
        val gate = DistanceGate()
        assertFalse(gate.update(24))
        assertFalse(gate.update(25))
        assertFalse(gate.update(24))
        assertTrue(gate.update(24))
        assertTrue(gate.update(30))
        assertTrue(gate.update(29))
        assertTrue(gate.update(30))
        assertFalse(gate.update(30))
    }

    @Test fun lostFaceAndStandbyResetState() {
        val gate = DistanceGate()
        gate.update(20)
        assertTrue(gate.update(20))
        assertFalse(gate.update(0))
        assertFalse(gate.update(20))
        gate.reset()
        assertFalse(gate.update(20))
    }

    @Test fun calibrationNeedsTenAndUsesMedian() {
        val samples = CalibrationSamples()
        for (width in 200..208) assertNull(samples.add("portrait", width))
        assertEquals(204, samples.add("portrait", 209) ?: -1)
        assertEquals(10, samples.count)
    }

    @Test fun directionChangeAndLargeMovementRestartCollection() {
        val samples = CalibrationSamples()
        repeat(5) { samples.add("portrait", 200) }
        assertNull(samples.add("landscape", 200))
        assertEquals(1, samples.count)
        assertNull(samples.add("landscape", 300))
        assertEquals(1, samples.count)
        samples.reset()
        assertEquals(0, samples.count)
    }
}
