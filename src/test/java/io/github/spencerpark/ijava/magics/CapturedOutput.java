package io.github.spencerpark.ijava.magics;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public final class CapturedOutput implements AutoCloseable {
    private final PrintStream original;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public CapturedOutput() {
        this.original = System.out;
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
    }

    public String text() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        System.setOut(original);
    }
}
