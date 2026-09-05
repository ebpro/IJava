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

import lombok.extern.slf4j.Slf4j;
import io.github.spencerpark.ijava.IJava;
import io.github.spencerpark.ijava.JavaKernel;
import io.github.spencerpark.ijava.execution.CodeEvaluator;
import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import io.github.spencerpark.jupyter.kernel.magic.registry.LineMagic;
import io.github.spencerpark.jupyter.kernel.magic.registry.LineMagicFunction;
import io.github.spencerpark.jupyter.kernel.magic.registry.Magics;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.Arrays;
import java.net.URL;
import io.github.spencerpark.ijava.runtime.Display;
import io.github.spencerpark.ijava.utils.FileUtils;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class MagicsTool {
    private static final String HIGHLIGHT_PATTERN = "\u001B[36m%s\u001B[0m";

    private CodeEvaluator evaluator;

    @LineMagic
    public void listLineMagic(List<String> args) {
        Magics magics = JavaKernel.getMagics();
        try {
            System.out.printf("registered line magics: %n\t- %s%n",
                    String.join("\n\t- ", getMagicsName(magics, "lineMagics")));
        } catch (Exception e) {
            System.out.printf("inspect line magics fail: %s%n", e.getMessage());
        }
    }

    @LineMagic
    public void listCellMagic(List<String> args) {
        Magics magics = JavaKernel.getMagics();
        try {
            System.out.printf("registered cell magics: %n\t- %s%n",
                    String.join("\n\t- ", getMagicsName(magics, "cellMagics")));
        } catch (Exception e) {
            System.out.printf("inspect cell magics fail: %s%n", e.getMessage());
        }
    }

    @LineMagic(aliases = { "list" })
    public void listMagic(List<String> args) {
        listLineMagic(Collections.emptyList());
        listCellMagic(Collections.emptyList());
    }

    @LineMagic(value = "cmd")
    public void runCommand(List<String> args) throws IOException {
        if (args.isEmpty())
            return;
        Process proc = Runtime.getRuntime().exec(args.toArray(new String[0]));

        String s;
        try (InputStreamReader inputStreamReader = new InputStreamReader(proc.getInputStream());
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            while ((s = bufferedReader.readLine()) != null) {
                System.out.println(s);
            }
        }
        try (InputStreamReader inputStreamReader = new InputStreamReader(proc.getErrorStream());
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            while ((s = bufferedReader.readLine()) != null) {
                System.err.println(s);
            }
        }
    }

    @LineMagic(value = "reload-class")
    public void reloadClass(List<String> args) {
        if (args.isEmpty()) {
            System.out.println("Usage: %reload-class <FullyQualifiedClassName>");
            return;
        }
        String fqcn = args.get(0);
        try {
            // Best-effort: force class initialization using context classloader
            Class<?> c = Class.forName(fqcn, true, Thread.currentThread().getContextClassLoader());
            System.out.printf("Loaded class %s (loader=%s)%n", c.getName(), c.getClassLoader());
        } catch (Throwable t) {
            System.out.printf("Failed to load/initialize %s: %s%n", fqcn, t.getMessage());
        }
    }

    @LineMagic(value = "class-info")
    public void classInfo(List<String> args) {
        if (args.isEmpty()) {
            System.out.println("Usage: %class-info <FullyQualifiedClassName>");
            return;
        }

        String fqcn = args.get(0);
        try {
            Class<?> c = Class.forName(fqcn, false, Thread.currentThread().getContextClassLoader());

            StringBuilder md = new StringBuilder();
            md.append("**").append(c.getName()).append("**\n\n");
            md.append("- Package: ")
                    .append(c.getPackage() == null ? "(default)" : c.getPackage().getName()).append("\n");
            md.append("- Modifiers: ").append(Modifier.toString(c.getModifiers())).append("\n");
            md.append("- Classloader: ").append(String.valueOf(c.getClassLoader())).append("\n\n");

            Annotation[] ann = c.getAnnotations();
            if (ann != null && ann.length > 0) {
                md.append("**Annotations**\n");
                for (Annotation a : ann)
                    md.append("- ").append(a.toString()).append("\n");
                md.append("\n");
            }

            md.append("**Constructors**\n");
            for (Constructor<?> ctor : c.getDeclaredConstructors()) {
                md.append("- ")
                        .append(Modifier.toString(ctor.getModifiers())).append(" ")
                        .append(ctor.getName()).append("(")
                        .append(Arrays.stream(ctor.getParameterTypes()).map(Class::getSimpleName)
                                .collect(Collectors.joining(", ")))
                        .append(")\n");
            }

            md.append("\n**Fields**\n");
            for (Field f : c.getDeclaredFields()) {
                md.append("- ")
                        .append(Modifier.toString(f.getModifiers())).append(" ")
                        .append(f.getType().getSimpleName()).append(" ")
                        .append(f.getName()).append("\n");
            }

            md.append("\n**Methods**\n");
            for (Method m : c.getDeclaredMethods()) {
                md.append("- ")
                        .append(Modifier.toString(m.getModifiers())).append(" ")
                        .append(m.getReturnType().getSimpleName()).append(" ")
                        .append(m.getName()).append("(")
                        .append(Arrays.stream(m.getParameterTypes()).map(Class::getSimpleName)
                                .collect(Collectors.joining(", ")))
                        .append(")\n");
            }

            Display.display(md.toString(), "text/markdown");
        } catch (Throwable t) {
            System.out.printf("Failed to inspect %s: %s%n", fqcn, t.getMessage());
        }
    }

    @LineMagic(value = "javadoc-html")
    public void javadocHtml(List<String> args) {
        if (args.isEmpty()) {
            System.out.println("Usage: %javadoc-html <FullyQualifiedClassName>");
            return;
        }

        String fqcn = args.get(0);
        try {
            Optional<Path> opt = PathResolver.resolveSourceFileForClass(fqcn, Collections.emptyMap());
            if (opt.isEmpty()) {
                System.out.printf("Source not found for: %s%n", fqcn);
                return;
            }

            Path p = opt.get();
            String src = String.join("\n", Files.readAllLines(p));
            String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
            int idx = src.indexOf("class " + simple);
            if (idx == -1)
                idx = src.indexOf("interface " + simple);
            if (idx == -1)
                idx = src.indexOf("enum " + simple);
            if (idx == -1) {
                System.out.println("No class declaration found in source");
                return;
            }

            int start = src.lastIndexOf("/**", idx);
            if (start == -1) {
                System.out.printf("No javadoc found for %s%n", fqcn);
                return;
            }
            int end = src.indexOf("*/", start);
            if (end == -1)
                end = idx;
            String comment = src.substring(start, end + 2);

            String html = "<div class=\"javadoc\">" + "<pre>" + escapeHtml(comment) + "</pre>" + "</div>";
            Display.display(html, "text/html");
        } catch (Exception e) {
            System.out.printf("Error: %s%n", e.getMessage());
        }
    }

    @LineMagic(value = "where", aliases = { "which" })
    public void where(List<String> args) {
        if (args.isEmpty()) {
            System.out.println("Usage: %where <FullyQualifiedClassName>");
            return;
        }

        String fqcn = args.get(0);
        String resourcePath = fqcn.replace('.', '/') + ".class";
        URL res = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
        if (res != null) {
            System.out.printf("%s -> %s%n", fqcn, res.toString());
        } else {
            try {
                Class<?> c = Class.forName(fqcn, false, Thread.currentThread().getContextClassLoader());
                if (c.getProtectionDomain() != null && c.getProtectionDomain().getCodeSource() != null
                        && c.getProtectionDomain().getCodeSource().getLocation() != null) {
                    System.out.printf("%s -> %s%n", fqcn,
                            c.getProtectionDomain().getCodeSource().getLocation().toString());
                } else {
                    System.out.printf("No location found for %s%n", fqcn);
                }
            } catch (Throwable t) {
                System.out.printf("Class not found in classpath: %s%n", fqcn);
            }
        }

        Optional<Path> src = PathResolver.resolveSourceFileForClass(fqcn, Collections.emptyMap());
        src.ifPresent(path -> System.out.printf("Source: %s%n", path.toAbsolutePath().toString()));
    }

    @LineMagic(value = "read")
    public String readFromFile(List<String> args) throws IOException {
        if (args.isEmpty()) {
            System.out.println("""
                    -h/--help for help.
                    help:
                        example:
                            1. `String content = %read filename` will read file and return for content.
                    """);
            return null;
        }
        return String.join("\n", Files.readAllLines(Path.of(args.get(0))));
    }

    @LineMagic(value = "load")
    public String loadFile(List<String> args) throws IOException {
        if (args.isEmpty()) {
            log.debug("%load called with no args");
            return null;
        }

        String raw = args.get(0);
        try {
            Path p = Path.of(raw);
            if (!Files.exists(p)) {
                // try docs/notebooks relative path
                Path alt = Path.of("docs", "notebooks").resolve(raw);
                if (Files.exists(alt)) {
                    p = alt;
                } else {
                    // try to find matching file in workspace
                    try {
                        Optional<Path> found = Files.walk(Path.of(".")).filter(f -> f.endsWith(raw)).findFirst();
                        if (found.isPresent())
                            p = found.get();
                    } catch (IOException e) {
                        // ignore search errors
                    }
                }
            }

            if (!Files.exists(p)) {
                log.warn("%load: file not found: {} (tried '{}')", raw, p);
                return null;
            }

            return String.join("\n", Files.readAllLines(p));
        } catch (Exception e) {
            log.warn("%load: error loading '{}': {}", raw,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return null;
        }
    }

    @LineMagic(value = "write")
    public void writeToFile(List<String> args) throws IOException {
        if (args.isEmpty()) {
            System.out.println("""
                    -h/--help for help.
                    help:
                        example:
                            1. `%write variable filename` will read variable's write to file.
                            2. `%write variable` will read variable's write to temp file.
                    """);
            return;
        }

        if (evaluator == null)
            getEvaluator();
        Object content;
        try {
            content = evaluator.eval(args.get(0));
        } catch (Exception e) {
            throw new RuntimeException(
                    "eval variable `" + args.get(0) + "` error, variable not found or illegal express!");
        }

        List<String> argsLast = args.size() > 1 ? Collections.singletonList(args.get(1)) : Collections.emptyList();
        writeToFile(argsLast, content.toString());
    }

    @CellMagic(value = "write")
    public void writeToFile(List<String> args, String body) throws IOException {
        String fileName;
        if (args.isEmpty()) {
            Path tempDir = FileUtils.createPrivateTempDir("ijava-jshell-");
            fileName = FileUtils.createPrivateTempFile(tempDir, "jshell-", ".tmp").toAbsolutePath().toString();
        } else {
            fileName = args.get(0);
        }
        File file = new File(fileName);
        if (file.getParentFile() != null && !file.getParentFile().exists() && !file.getParentFile().mkdirs())
            throw new IOException("Cannot create parent folder: " + file.getParentFile());
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(body);
            writer.flush();
        }
        System.out.printf("Write to %s success.%n", String.format(HIGHLIGHT_PATTERN, file.getAbsolutePath()));
    }

    @SuppressWarnings("unchecked")
    private Collection<String> getMagicsName(Magics magics, String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = magics.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Map<String, LineMagicFunction<?>> lineMagics = (Map<String, LineMagicFunction<?>>) field.get(magics);
        return lineMagics.entrySet()
                .stream()
                .collect(Collectors.groupingBy(Map.Entry::getValue,
                        Collectors.mapping(Map.Entry::getKey, Collectors.joining(", "))))
                .values();
    }

    public void getEvaluator() {
        try {
            JavaKernel kernel = IJava.getKernelInstance();
            Field field = kernel.getClass().getDeclaredField("evaluator");
            field.setAccessible(true);
            evaluator = (CodeEvaluator) field.get(kernel);
        } catch (Exception e) {
            throw new RuntimeException("Compiler get JShell evaluator instance error." + e.getMessage());
        }
    }

    private static String escapeHtml(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
