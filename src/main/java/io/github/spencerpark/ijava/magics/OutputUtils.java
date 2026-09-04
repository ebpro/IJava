package io.github.spencerpark.ijava.magics;

import java.util.Map;
import static io.github.spencerpark.ijava.runtime.Display.display;

public final class OutputUtils {
    private OutputUtils() {
    }

    public static void formatAndDisplay(String content, Map<String, String> opts) {
        boolean raw = opts.getOrDefault("format", "fenced").equals("raw");
        if (raw) {
            display(content, "text/plain");
        } else {
            display("```Java\n" + content + "\n```", "text/markdown");
        }
    }
}
