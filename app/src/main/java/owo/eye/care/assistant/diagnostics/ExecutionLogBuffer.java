package owo.eye.care.assistant.diagnostics;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Production log buffer, independent of Android for repeatable JVM tests.
 * Access only from the single log writer. Snapshots never expose mutable state.
 */
public final class ExecutionLogBuffer {
    private final int maxLines;
    private final int maxBytes;
    private final ArrayList<String> lines = new ArrayList<>();

    public ExecutionLogBuffer(int maxLines, int maxBytes) {
        if (maxLines < 1 || maxBytes < 1) throw new IllegalArgumentException("Invalid limits");
        this.maxLines = maxLines;
        this.maxBytes = maxBytes;
    }

    public void add(String line) {
        if (line == null || line.isEmpty()) return;
        lines.add(line);
        Collections.sort(lines);
        while (lines.size() > maxLines) lines.remove(0);
        int bytes = 0;
        for (String item : lines) bytes += byteSize(item);
        while (bytes > maxBytes && !lines.isEmpty()) bytes -= byteSize(lines.remove(0));
    }

    public void addAll(Iterable<String> values) {
        for (String value : values) add(value);
    }

    private static int byteSize(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length + 1;
    }

    public List<String> snapshot(boolean newestFirst) {
        ArrayList<String> copy = new ArrayList<>(lines);
        if (newestFirst) Collections.reverse(copy);
        return copy;
    }

    public boolean isEmpty() { return lines.isEmpty(); }
    public void clear() { lines.clear(); }
}
