package io.github.spencerpark.ijava.magics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PathResolver {
    private PathResolver() {}

    public static Optional<Path> resolveSourceFileForClass(String fullyQualifiedClassName, java.util.Map<String, String> opts) {
        String srcBase = opts.getOrDefault("src", null);
        List<String> bases = new ArrayList<>();
        if (srcBase != null && !srcBase.isBlank()) bases.add(srcBase);
        bases.add("src/main/java");
        bases.add("src");
        bases.add("docs/notebooks/sample_java");

        String pkgPath = fullyQualifiedClassName.replace('.', '/');
        String className = fullyQualifiedClassName.substring(fullyQualifiedClassName.lastIndexOf('.') + 1);
        String rel = pkgPath + ".java";

        for (String base : bases) {
            Path p = Paths.get(base).resolve(rel);
            if (Files.exists(p)) return Optional.of(p);
            Path p2 = Paths.get(base).resolve("src/main/java").resolve(rel);
            if (Files.exists(p2)) return Optional.of(p2);
        }

        try {
            final String simple = className + ".java";
            Optional<Path> found = Files.walk(Paths.get(".")).filter(Files::isRegularFile).filter(p -> p.getFileName().toString().equals(simple)).findFirst();
            if (found.isPresent()) return found;
        } catch (IOException ignored) {}

        return Optional.empty();
    }
}
