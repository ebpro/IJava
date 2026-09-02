/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2025 ebpro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.jupyter.kernel.magic.registry.LineMagic;
import io.github.spencerpark.jupyter.kernel.util.GlobFinder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

public class ClasspathMagics {
    private final Consumer<String> addToClasspath;

    public ClasspathMagics(Consumer<String> addToClasspath) {
        this.addToClasspath = addToClasspath;
    }

    @LineMagic
    public List<String> jars(List<String> args) {
        if (args == null) args = List.of();
        if (args.contains("--help") || args.contains("-h")) {
            System.out.println("## %jars - Add jar files to classpath\n\n" +
                    "Usage: %jars [--help] <glob-pattern>...\n\n" +
                    "Adds matching jar files to the kernel classpath and returns their paths.");
            return List.of();
        }
        List<String> jars = args.stream()
                .map(GlobFinder::new)
                .flatMap(g -> {
                    try {
                        return StreamSupport.stream(g.computeMatchingFiles().spliterator(), false);
                    } catch (IOException e) {
                        throw new RuntimeException("Exception resolving jar glob", e);
                    }
                })
                .map(p -> p.toAbsolutePath().toString())
                .toList();

        jars.forEach(this.addToClasspath);

        return jars;
    }

    @LineMagic
    public List<String> classpath(List<String> args) {
        if (args == null) args = List.of();
        if (args.contains("--help") || args.contains("-h")) {
            System.out.println("## %classpath - Add paths to classpath\n\nUsage: %classpath [--help] <glob-pattern>...\n\nAdds matching paths to the kernel classpath and returns their paths.");
            return List.of();
        }
        List<String> paths = args.stream()
                .map(GlobFinder::new)
                .flatMap(g -> {
                    try {
                        return StreamSupport.stream(g.computeMatchingPaths().spliterator(), false);
                    } catch (IOException e) {
                        throw new RuntimeException("Exception resolving jar glob", e);
                    }
                })
                .map(p -> p.toAbsolutePath().toString())
                .toList();

        paths.forEach(this.addToClasspath);

        return paths;
    }

    @LineMagic(value = "classpath-snapshot")
    public String classpathSnapshot(List<String> args) {
        if (args == null) args = List.of();
        if (args.contains("--help") || args.contains("-h")) {
            System.out.println("## %classpath-snapshot - Show current classpath\n\nUsage: %classpath-snapshot [--help]\n\nPrints the current java.class.path entries and returns them as a string.");
            return "";
        }
        String cp = System.getProperty("java.class.path");
        String[] parts = cp.split(File.pathSeparator);
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append(p);
            try {
                Path path = Path.of(p);
                if (Files.exists(path)) {
                    sb.append(" (lastModified=").append(Files.getLastModifiedTime(path)).append(")");
                }
            } catch (Exception ignored) {
            }
            sb.append("\n");
        }
        String out = sb.toString();
        System.out.println(out);
        return out;
    }
}
