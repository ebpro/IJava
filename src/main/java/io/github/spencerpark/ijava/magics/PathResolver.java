package io.github.spencerpark.ijava.magics;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class PathResolver {
    private PathResolver() {
    }

    public static Optional<Path> resolveSourceFileForClass(String fullyQualifiedClassName,
            java.util.Map<String, String> opts) {
        String srcBase = opts.getOrDefault("src", null);
        List<String> bases = new ArrayList<>();
        if (srcBase != null && !srcBase.isBlank())
            bases.add(srcBase);
        bases.add("src/main/java");
        bases.add("src");
        bases.add("docs/notebooks/sample_java");

        String pkgPath = fullyQualifiedClassName.replace('.', '/');
        String className = fullyQualifiedClassName.substring(fullyQualifiedClassName.lastIndexOf('.') + 1);
        String rel = pkgPath + ".java";

        for (String base : bases) {
            Path p = Paths.get(base).resolve(rel);
            if (Files.exists(p))
                return Optional.of(p);
            Path p2 = Paths.get(base).resolve("src/main/java").resolve(rel);
            if (Files.exists(p2))
                return Optional.of(p2);
        }

        try {
            final String simple = className + ".java";
            final Path start = Paths.get(".");
            final List<Path> found = new ArrayList<>();
            final int maxDepth = 5;
            final Set<String> excludedDirectories = Set.of(".git", ".gradle", "build", ".venv", "node_modules",
                    "target", "dist", "out", ".idea", ".vscode");
            Files.walkFileTree(start, EnumSet.noneOf(FileVisitOption.class), maxDepth, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(start) && dir.getFileName() != null
                            && excludedDirectories.contains(dir.getFileName().toString()))
                        return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && file.getFileName().toString().equals(simple)) {
                        found.add(file);
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            if (!found.isEmpty())
                return Optional.of(found.get(0));
        } catch (IOException ignored) {
        }

        return Optional.empty();
    }
}
