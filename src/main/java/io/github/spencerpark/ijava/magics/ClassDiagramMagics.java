package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.ijava.runtime.Display;
import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import io.github.spencerpark.jupyter.kernel.magic.registry.LineMagic;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public class ClassDiagramMagics {

    private static class Options {
        boolean svg;
        boolean png;
        boolean umlOnly;
        boolean includeNonPublic;
        boolean includeAncestors;
        boolean interfacesOnly;
        boolean classesOnly;
        boolean excludeInherited; // <-- nouveau
        int max = 50;
        int depth = 3;
        String targetClass;
        String targetPackage;
        String includeRegex;
        String excludeRegex;
        String outFile;
    }

    @LineMagic("classDiagram")
    public void classDiagram(List<String> args) {
        String body = args == null ? "" : String.join(" ", args);
        String[] parts = body.trim().isEmpty() ? new String[0] : body.trim().split("\\s+");

        if (hasHelp(parts)) {
            printHelp();
            return;
        }

        Options o = parse(parts);
        o.max = Math.max(1, Math.min(o.max, 500));

        List<Class<?>> classes = loadClasses(o);
        if (classes.isEmpty()) {
            System.out.println("No classes found.");
            return;
        }

        if (o.includeAncestors) {
            classes = expandAncestors(classes, o.depth, o.max);
        }

        // ---------- Generate PlantUML ----------
        String plantuml = PlantUmlGenerator.generate(
                classes,
                true, // includeFields
                true, // includeMethods
                true, // includeConstructors
                true, // includeInterfaces
                o.includeNonPublic,
                o.excludeInherited // <-- nouveau param
        );

        if (o.umlOnly) {
            output(plantuml, "text/plain", o.outFile);
            return;
        }

        render(plantuml, o);
    }

    // ---------- Parsing ----------
    private Options parse(String[] parts) {
        Options o = new Options();
        List<String> others = new ArrayList<>();

        for (String p : parts) {
            switch (p) {
                case "--svg" -> o.svg = true;
                case "--png" -> o.png = true;
                case "--uml" -> o.umlOnly = true;
                case "--non-public" -> o.includeNonPublic = true;
                case "--ancestors" -> o.includeAncestors = true;
                case "--interfaces-only" -> o.interfacesOnly = true;
                case "--classes-only" -> o.classesOnly = true;
                case "--exclude-inherited" -> o.excludeInherited = true;
                default -> {
                    if (p.startsWith("--max="))
                        o.max = parseInt(p, 6, 50);
                    else if (p.startsWith("--depth="))
                        o.depth = parseInt(p, 8, 3);
                    else if (p.startsWith("--package="))
                        o.targetPackage = p.substring(10);
                    else if (p.startsWith("--include="))
                        o.includeRegex = p.substring(10);
                    else if (p.startsWith("--exclude="))
                        o.excludeRegex = p.substring(10);
                    else if (p.startsWith("--out="))
                        o.outFile = p.substring(6);
                    else if (!p.startsWith("--"))
                        others.add(p);
                }
            }
        }

        if (!others.isEmpty())
            o.targetClass = others.get(0);
        return o;
    }

    private int parseInt(String p, int start, int def) {
        try {
            return Integer.parseInt(p.substring(start));
        } catch (Exception e) {
            return def;
        }
    }

    private boolean hasHelp(String[] parts) {
        for (String p : parts)
            if (p.equalsIgnoreCase("--help") || p.equalsIgnoreCase("-h"))
                return true;
        return false;
    }

    private void printHelp() {
        System.out.println("""
                %classDiagram - Generate UML class diagrams using PlantUML

                USAGE:
                  %classDiagram <fully.qualified.ClassName> [options]
                  %classDiagram --package=<pkg> [options]

                TARGET SELECTION:
                  <ClassName>            Generate diagram for a single class.
                  --package=<pkg>        Scan a package and include multiple classes.

                OUTPUT FORMAT (choose one):
                  --svg                  Render diagram as SVG image (default).
                  --png                  Render diagram as PNG image.
                  --uml                  Output raw PlantUML text only (no rendering).

                SCOPE / SIZE CONTROL:
                  --max=<N>              Maximum classes when scanning a package (default 50).

                VISIBILITY / DETAIL:
                  --non-public           Include non-public fields, methods, constructors.

                HIERARCHY / ANCESTORS:
                  --ancestors            Include superclasses and interfaces.
                  --depth=<N>            Ancestor depth when --ancestors is used (default 3).

                TYPE FILTERS:
                  --interfaces-only      Include only interfaces.
                  --classes-only         Include only classes (exclude interfaces).

                METHOD FILTER:
                  --exclude-inherited    Exclude inherited methods and constructors.

                NAME FILTERS (regex, package scan only):
                  --include=<regex>      Only include class names that match.
                  --exclude=<regex>      Exclude class names that match.

                FILE OUTPUT:
                  --out=<file>           Save output to file (.svg, .png, .uml).

                HELP:
                  --help, -h             Show this help message.

                EXAMPLES:
                  %classDiagram java.util.ArrayList
                  %classDiagram --package=java.util --max=80 --svg
                  %classDiagram com.myapp.Service --ancestors --depth=2
                  %classDiagram --package=com.myapp --include=.*Service --png
                  %classDiagram java.util.List --uml --out=list.uml
                """);
    }

    // ---------- Class Loading ----------
    private List<Class<?>> loadClasses(Options o) {
        List<Class<?>> result = new ArrayList<>();
        Pattern include = o.includeRegex == null ? null : Pattern.compile(o.includeRegex);
        Pattern exclude = o.excludeRegex == null ? null : Pattern.compile(o.excludeRegex);

        try {
            if (o.targetClass != null) {
                result.add(Class.forName(o.targetClass));
            } else if (o.targetPackage != null) {
                try (ScanResult scan = new ClassGraph()
                        .acceptPackages(o.targetPackage)
                        .enableClassInfo()
                        .scan()) {

                    scan.getAllClasses().stream().limit(o.max).forEach(ci -> {
                        String name = ci.getName();
                        if (include != null && !include.matcher(name).find())
                            return;
                        if (exclude != null && exclude.matcher(name).find())
                            return;

                        try {
                            Class<?> c = Class.forName(name);
                            if (o.interfacesOnly && !c.isInterface())
                                return;
                            if (o.classesOnly && c.isInterface())
                                return;
                            result.add(c);
                        } catch (Throwable ignored) {
                        }
                    });
                }
            }
        } catch (Throwable t) {
            System.out.println("Classpath error: " + t.getMessage());
        }

        return result;
    }

    // ---------- Ancestors ----------
    private List<Class<?>> expandAncestors(List<Class<?>> base, int depth, int max) {
        Set<Class<?>> set = new LinkedHashSet<>(base);
        Queue<Class<?>> q = new ArrayDeque<>(base);
        int d = 0;

        while (!q.isEmpty() && set.size() < max && d < depth) {
            int size = q.size();
            while (size-- > 0) {
                Class<?> c = q.poll();
                if (c == null)
                    continue;

                Class<?> s = c.getSuperclass();
                if (s != null && s != Object.class && set.add(s))
                    q.add(s);

                for (Class<?> i : c.getInterfaces())
                    if (set.add(i))
                        q.add(i);
            }
            d++;
        }

        return new ArrayList<>(set);
    }

    // ---------- Rendering ----------
    private void render(String plantuml, Options o) {
        try {
            net.sourceforge.plantuml.SourceStringReader reader = new net.sourceforge.plantuml.SourceStringReader(
                    plantuml);

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            net.sourceforge.plantuml.FileFormat fmt = o.png ? net.sourceforge.plantuml.FileFormat.PNG
                    : net.sourceforge.plantuml.FileFormat.SVG;

            net.sourceforge.plantuml.FileFormatOption opt = new net.sourceforge.plantuml.FileFormatOption(fmt);
            reader.outputImage(os, opt);
            byte[] data = os.toByteArray();

            if (o.outFile != null) {
                try (FileOutputStream fos = new FileOutputStream(o.outFile)) {
                    fos.write(data);
                }
            }

            if (o.png) {
                Display.display(data, "image/png");
            } else {
                Display.display(new String(data, StandardCharsets.UTF_8), "image/svg+xml");
            }
        } catch (Throwable t) {
            System.out.println("Render failed: " + t.getMessage());
            Display.display(plantuml, "text/plain");
        }
    }

    private void output(String text, String mime, String out) {
        try {
            if (out != null) {
                try (FileWriter fw = new FileWriter(out)) {
                    fw.write(text);
                }
            }
        } catch (IOException ignored) {
        }
        Display.display(text, mime);
    }

    // ---------- Cell Magic ----------
    @CellMagic("classDiagram")
    public void classDiagramCell(List<String> args, String body) {
        List<String> all = new ArrayList<>();
        if (args != null)
            all.addAll(args);
        if (body != null && !body.isBlank())
            all.add(body);
        classDiagram(all);
    }
}
