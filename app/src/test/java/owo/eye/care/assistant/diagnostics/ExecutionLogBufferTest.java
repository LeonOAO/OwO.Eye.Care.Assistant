package owo.eye.care.assistant.diagnostics;

import org.junit.Test;
import static org.junit.Assert.*;

public class ExecutionLogBufferTest {
    @Test public void retainsNewestAndReturnsCopies() {
        ExecutionLogBuffer buffer = new ExecutionLogBuffer(2, 1024);
        buffer.add("2026-10-12 10:00:03 [event] third");
        buffer.add("2026-10-12 10:00:01 [event] first");
        buffer.add("2026-10-12 10:00:02 [event] second");
        assertEquals(2, buffer.snapshot(false).size());
        assertTrue(buffer.snapshot(true).get(0).contains("third"));
        buffer.snapshot(false).clear();
        assertEquals(2, buffer.snapshot(false).size());
    }
    @Test public void utf8ByteLimitAndClear() {
        ExecutionLogBuffer buffer = new ExecutionLogBuffer(300, 7);
        buffer.add("中文");
        assertEquals(1, buffer.snapshot(false).size());
        buffer.add("測試");
        assertEquals(1, buffer.snapshot(false).size());
        buffer.clear();
        assertTrue(buffer.isEmpty());
    }
    @Test public void rejectsInvalidLimits() {
        try { new ExecutionLogBuffer(0, 10); fail("Expected exception"); }
        catch (IllegalArgumentException expected) { }
    }
}
