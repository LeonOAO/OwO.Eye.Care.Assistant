package owo.eye.care.assistant.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReminderModeTest {
    @Test public void oldSettingsRetainMeaning() {
        assertTrue(ReminderMode.usesPin(0));
        assertFalse(ReminderMode.usesVoice(0));
        assertFalse(ReminderMode.usesBanner(0));
        assertTrue(ReminderMode.usesVoice(1));
        assertTrue(ReminderMode.usesBanner(1));
        assertFalse(ReminderMode.usesPin(1));
    }
    @Test public void independentOutputs() {
        assertTrue(ReminderMode.usesVoice(ReminderMode.VOICE));
        assertFalse(ReminderMode.usesBanner(ReminderMode.VOICE));
        assertFalse(ReminderMode.usesVoice(ReminderMode.BANNER));
        assertTrue(ReminderMode.usesBanner(ReminderMode.BANNER));
    }
    @Test public void selectionOrderAndFallback() {
        assertArrayEquals(new int[] {2, 3, 0, 1}, ReminderMode.selectionOrder());
        assertEquals(0, ReminderMode.normalize(-1));
        assertEquals(0, ReminderMode.normalize(4));
        assertEquals("語音", ReminderMode.label(2));
        assertEquals("上橫幅", ReminderMode.label(3));
    }
}
