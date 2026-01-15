package io.github.spencerpark.ijava.magics;

import java.util.*;

public final class OptionUtils {
    private OptionUtils() {}

    public static Map<String, String> parseOptions(List<String> args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("--raw")) {
                opts.put("format", "raw");
            } else if (a.equals("--fenced")) {
                opts.put("format", "fenced");
            } else if ((a.equals("--src") || a.equals("--root")) && i + 1 < args.size()) {
                opts.put("src", args.get(i + 1));
                i++; // consume
            } else if (a.startsWith("--src=") || a.startsWith("--root=")) {
                int eq = a.indexOf('=');
                opts.put("src", a.substring(eq + 1));
            } else if (a.contains("=")) {
                int j = a.indexOf('=');
                String k = a.substring(0, j);
                String v = a.substring(j + 1);
                if (k.equals("index")) {
                    opts.put("selectIndex", v);
                } else {
                    opts.put(k, v);
                }
            }
        }
        return opts;
    }

    public static List<String> positionalArgs(List<String> args) {
        return args.stream()
                .filter(a -> !a.equals("--raw") && !a.equals("--fenced") && !a.startsWith("--src") && !a.startsWith("--root") && !a.contains("="))
                .toList();
    }
}
