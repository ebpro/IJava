package io.github.spencerpark.ijava.magics;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import java.util.stream.Collectors;

import static io.github.spencerpark.ijava.runtime.Display.display;

@Slf4j
public class JavaMagics {

    @CellMagic("javasrcMethodByAnnotationName")
    public void javasrcMethodByAnnotationName(List<String> args, String body) throws IOException {
        Map<String, String> opts = OptionUtils.parseOptions(args);
        if (opts.containsKey("--help") || opts.containsKey("-h")) {
            display("**Usage:** `%%javasrcMethodByAnnotationName <FullyQualifiedClassName> <AnnotationName> [index]`\n\n" +
                    "Extract methods annotated with a given annotation from source file (body should be path to file).", "text/markdown");
            return;
        }
        List<String> pos = OptionUtils.positionalArgs(args);

        if (pos.size() < 2) {
            display("Error: expected usage `%%javasrcMethodByAnnotationName <ClassName> <AnnotationName> [index]`",
                    "text/markdown");
            return;
        }

        String filename = body;
        String className = pos.get(0);
        String simpleClassName = className != null && className.contains(".")
                ? className.substring(className.lastIndexOf('.') + 1)
                : className;
        String annotationName = pos.get(1);
        int index = pos.size() >= 3 ? Integer.parseInt(pos.get(2)) : 0;

        if ((filename == null || filename.isBlank()) && className != null && className.contains(".")) {
            Optional<Path> p = PathResolver.resolveSourceFileForClass(className, opts);
            if (p.isPresent())
                filename = p.get().toString();
        }

        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(Files.readString(Path.of(filename)));
        } catch (IOException e) {
            display("Error: failed to read file `" + filename + "`: " + e.getMessage(), "text/markdown");
            return;
        }

        Optional<com.github.javaparser.ast.body.ClassOrInterfaceDeclaration> clazz = cu.getClassByName(simpleClassName);
        if (clazz.isEmpty()) {
            Optional<?> ifaceCheck = cu.getInterfaceByName(simpleClassName);
            if (ifaceCheck.isPresent()) {
                display("`" + className + "` is an interface in file `" + filename
                        + "`. Use `%%javasrcInterfaceByName` to extract interfaces or supply a class name.",
                        "text/markdown");
            } else {
                display("Class `" + className + "` not found in file `" + filename + "`.", "text/markdown");
                display("Usage: `%%javasrcMethodByAnnotationName <FullyQualifiedClassName> <AnnotationName> [index]`",
                        "text/markdown");
            }
            return;
        }

        List<com.github.javaparser.ast.body.MethodDeclaration> matches = clazz.get().getMethods()
                .stream()
                .filter(m -> m.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals(annotationName)))
                .collect(Collectors.toList());

        if (matches.isEmpty()) {
            display("No methods annotated with `@" + annotationName + "` found in class `" + className + "`.",
                    "text/markdown");
            return;
        }

        if (index < 0 || index >= matches.size()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(matches.size()).append(" matching methods:\n\n");
            for (int i = 0; i < matches.size(); i++) {
                sb.append(i).append(": ").append(matches.get(i).getDeclarationAsString(false, false, false))
                        .append("\n");
            }
            display(sb.toString(),
                    opts.getOrDefault("format", "fenced").equals("raw") ? "text/plain" : "text/markdown");
            return;
        }

        String out = matches.get(index).toString();
        OutputUtils.formatAndDisplay(out, opts);
    }

    private static String renderJavadoc(Javadoc j) {
        if (j == null)
            return "(no javadoc)";
        StringBuilder sb = new StringBuilder();
        try {
            String desc = j.getDescription().toText().trim();
            if (!desc.isEmpty()) {
                sb.append(desc).append("\n\n");
            }

            for (JavadocBlockTag tag : j.getBlockTags()) {
                JavadocBlockTag.Type t = tag.getType();
                String name = tag.getName().orElse("");
                String content = tag.getContent().toText().trim();
                switch (t) {
                    case PARAM:
                        sb.append("- @param ").append(name).append(" — ").append(content).append("\n");
                        break;
                    case RETURN:
                        sb.append("- @return — ").append(content).append("\n");
                        break;
                    case THROWS:
                    case EXCEPTION:
                        sb.append("- @throws ").append(name).append(" — ").append(content).append("\n");
                        break;
                    default:
                        sb.append("- @").append(tag.getTagName());
                        if (!name.isEmpty())
                            sb.append(" ").append(name);
                        if (!content.isEmpty())
                            sb.append(" — ").append(content);
                        sb.append("\n");
                }
            }
        } catch (Exception e) {
            return j.toString();
        }

        String out = sb.toString().trim();
        return out.isEmpty() ? "(no javadoc)" : out;
    }

    @CellMagic("javasrcMethodByName")
    public void javasrcMethodByName(List<String> args, String body) throws IOException {
        // If the user requested help, short-circuit immediately and do not
        // attempt any file reads or parsing of the cell body which may cause
        // spurious build/parse attempts (e.g. when the body is empty or a
        // comment). This ensures `--help` never triggers a build.
        if (args != null && (args.contains("--help") || args.contains("-h"))) {
            String help = "**Usage:** `%%javasrcMethodByName [options] <FullyQualifiedClassName|ClassName> [methodName|index]`\n\n"
                    +
                    "**Options:**\n" +
                    "- `--src <dir>`: source root to resolve FQCN (e.g., `--src=sample_java`)\n" +
                    "- `methodRegex=<regex>`: select methods whose name matches regex\n" +
                    "- `selectIndex=<n>` or positional index: pick one when multiple matches\n" +
                    "- `--raw` / `--fenced`: output format\n\n" +
                    "**Examples:**\n" +
                    "- `%%javasrcMethodByName methodRegex=summary --src=sample_java com.example.OrderExample`\n" +
                    "- `%%javasrcMethodByName com.example.OrderExample myMethod`\n" +
                    "- `%%javasrcMethodByName selectIndex=1 com.example.OrderExample myMethod`\n";
            display(help, "text/markdown");
            return;
        }

        Map<String, String> opts = OptionUtils.parseOptions(args);
        if (opts.containsKey("--help") || opts.containsKey("-h")) {
            display("**Usage:** `%%javasrcMethodByName [options] <FullyQualifiedClassName|ClassName> [methodName|index]`\n\nSee documentation for options like `--src`, `methodRegex`, and `selectIndex`.", "text/markdown");
            return;
        }
        List<String> pos = OptionUtils.positionalArgs(args);

        if (pos.size() < 1 && !opts.containsKey("methodRegex")) {
            display("Error: expected usage `%%javasrcMethodByName <ClassName> <MethodName|index>` or use `methodRegex=...`",
                    "text/markdown");
            return;
        }

        String filename = body;
        String className = pos.size() >= 1 ? pos.get(0) : null;
        String simpleClassName = className != null && className.contains(".")
                ? className.substring(className.lastIndexOf('.') + 1)
                : className;
        String methodName = pos.size() >= 2 ? pos.get(1) : null;
        int index = pos.size() >= 3 ? Integer.parseInt(pos.get(2)) : 0;

        if ((filename == null || filename.isBlank()) && className != null && className.contains(".")) {
            Optional<Path> p = PathResolver.resolveSourceFileForClass(className, opts);
            if (p.isPresent())
                filename = p.get().toString();
        }

        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(Files.readString(Path.of(filename)));
        } catch (IOException e) {
            display("Error: failed to read file `" + filename + "`: " + e.getMessage(), "text/markdown");
            return;
        }

        Optional<com.github.javaparser.ast.body.ClassOrInterfaceDeclaration> clazz = cu.getClassByName(simpleClassName);
        if (clazz.isEmpty()) {
            Optional<?> ifaceCheck = cu.getInterfaceByName(simpleClassName);
            if (ifaceCheck.isPresent()) {
                display("`" + className + "` is an interface in file `" + filename
                        + "`. To extract the interface source use `%%javasrcInterfaceByName`.", "text/markdown");
            } else {
                display("Class `" + className + "` not found in file `" + filename + "`.", "text/markdown");
            }
            return;
        }

        List<com.github.javaparser.ast.body.MethodDeclaration> methods = new ArrayList<>();
        if (opts.containsKey("methodRegex")) {
            Pattern p = Pattern.compile(opts.get("methodRegex"));
            methods = clazz.get().getMethods().stream().filter(m -> p.matcher(m.getNameAsString()).find())
                    .collect(Collectors.toList());
        } else if (methodName != null) {
            methods = clazz.get().getMethodsByName(methodName);
        }

        if (methods.isEmpty()) {
            display("No matching methods found for query in class `" + className + "`.", "text/markdown");
            return;
        }

        if (methods.size() > 1 && !opts.containsKey("selectIndex")) {
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(methods.size()).append(" matching methods:\n\n");
            for (int i = 0; i < methods.size(); i++) {
                sb.append(i).append(": ").append(methods.get(i).getDeclarationAsString(false, false, false))
                        .append("\n");
            }
            display(sb.toString(),
                    opts.getOrDefault("format", "fenced").equals("raw") ? "text/plain" : "text/markdown");
            return;
        }

        int pick = opts.containsKey("selectIndex") ? Integer.parseInt(opts.get("selectIndex")) : index;
        if (pick < 0 || pick >= methods.size()) {
            display("Index out of range. Use the summary list to pick an index.", "text/markdown");
            return;
        }

        String out = methods.get(pick).toString();
        OutputUtils.formatAndDisplay(out, opts);
    }

    @CellMagic("javasrcInterfaceByName")
    public void javasrcInterfaceByName(List<String> args, String body) throws IOException {
        Map<String, String> opts = OptionUtils.parseOptions(args);
        if (opts.containsKey("--help") || opts.containsKey("-h")) {
            display("**Usage:** `%%javasrcInterfaceByName <FullyQualifiedInterfaceName>`\n\nExtract interface source by fully-qualified name. Body may contain file path.", "text/markdown");
            return;
        }
        List<String> pos = OptionUtils.positionalArgs(args);

        if (pos.size() < 1) {
            display("Error: expected usage `%%javasrcInterfaceByName <FullyQualifiedInterfaceName>`", "text/markdown");
            return;
        }

        String fqcn = pos.get(0);
        String filename = body;
        if ((filename == null || filename.isBlank()) && fqcn != null && fqcn.contains(".")) {
            Optional<Path> p = PathResolver.resolveSourceFileForClass(fqcn, opts);
            if (p.isPresent())
                filename = p.get().toString();
        }

        String className = fqcn.substring(fqcn.lastIndexOf('.') + 1);

        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(Files.readString(Path.of(filename)));
        } catch (IOException e) {
            display("Error: failed to read file `" + filename + "`: " + e.getMessage(), "text/markdown");
            return;
        }

        Optional<?> iface = cu.getInterfaceByName(className);
        if (iface.isEmpty()) {
            Optional<com.github.javaparser.ast.body.ClassOrInterfaceDeclaration> clazzCheck = cu
                    .getClassByName(className);
            if (clazzCheck.isPresent()) {
                display("Found class `" + className + "` in file `" + filename
                        + "`. To extract classes use `%%javasrcClassByName`.", "text/markdown");
            } else {
                display("Interface `" + className + "` not found in file `" + filename + "`.", "text/markdown");
            }
            return;
        }

        String out = iface.get().toString();
        OutputUtils.formatAndDisplay(out, opts);
    }

    @CellMagic("javasrcClassByName")
    public void javasrcClassByName(List<String> args, String body) throws IOException {
        Map<String, String> opts = OptionUtils.parseOptions(args);
        if (opts.containsKey("--help") || opts.containsKey("-h")) {
            display("**Usage:** `%%javasrcClassByName <FullyQualifiedClassName>`\n\nExtract entire class source by FQCN. Body may contain file path.", "text/markdown");
            return;
        }
        List<String> pos = OptionUtils.positionalArgs(args);

        if (pos.isEmpty()) {
            display("Error: expected usage `%%javasrcClassByName <FullyQualifiedClassName>`", "text/markdown");
            return;
        }

        String fqcn = pos.get(0);
        String filename = body;
        if ((filename == null || filename.isBlank()) && fqcn != null && fqcn.contains(".")) {
            Optional<Path> p = PathResolver.resolveSourceFileForClass(fqcn, opts);
            if (p.isPresent())
                filename = p.get().toString();
        }

        String className = fqcn.substring(fqcn.lastIndexOf('.') + 1);

        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(Files.readString(Path.of(filename)));
        } catch (IOException e) {
            display("Error: failed to read file `" + filename + "`: " + e.getMessage(), "text/markdown");
            return;
        }

        CompilationUnit lpp = LexicalPreservingPrinter.setup(cu);

        Optional<com.github.javaparser.ast.body.ClassOrInterfaceDeclaration> clazz = lpp.getClassByName(className);
        if (clazz.isEmpty()) {
            Optional<?> ifaceCheck = lpp.getInterfaceByName(className);
            if (ifaceCheck.isPresent()) {
                display("Found interface `" + className + "` in file `" + filename
                        + "`. To extract interfaces use `%%javasrcInterfaceByName`.", "text/markdown");
            } else {
                display("Class `" + className + "` not found in file `" + filename + "`.", "text/markdown");
            }
            return;
        }

        String out = LexicalPreservingPrinter.print(clazz.get());
        OutputUtils.formatAndDisplay(out, opts);
    }

    @CellMagic("javasrcList")
    public void javasrcList(List<String> args, String body) throws IOException {
        Map<String, String> opts = OptionUtils.parseOptions(args);
        if (opts.containsKey("--help") || opts.containsKey("-h")) {
            display("**Usage:** `%%javasrcList <file>`\n\nList classes and methods in a Java file (summary view).", "text/markdown");
            return;
        }
        List<String> pos = OptionUtils.positionalArgs(args);

        String filename = body;
        if ((filename == null || filename.isBlank()) && !pos.isEmpty() && pos.get(0).contains(".")) {
            Optional<Path> p = PathResolver.resolveSourceFileForClass(pos.get(0), opts);
            if (p.isPresent())
                filename = p.get().toString();
        }

        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(Files.readString(Path.of(filename)));
        } catch (IOException e) {
            display("Error: failed to read file `" + filename + "`: " + e.getMessage(), "text/markdown");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Summary of ").append(filename).append("\n\n");
        cu.getTypes().forEach(t -> {
            sb.append(t.getClass().getSimpleName()).append(": ").append(t.getNameAsString()).append("\n");
            t.getMethods()
                    .forEach(m -> sb.append("  - ").append(m.getDeclarationAsString(false, false, false)).append("\n"));
            sb.append("\n");
        });

        display(sb.toString(), "text/markdown");
    }

    @CellMagic("javasrcConstructorByName")
    public void javasrcConstructorByName(List<String> args, String body) throws IOException {
        Map<String, String> opts = OptionUtils.parseOptions(args);
        if (opts.containsKey("--help") || opts.containsKey("-h")) {
            display("**Usage:** `%%javasrcConstructorByName <FullyQualifiedClassName>`\n\nShow constructors for a class. Body may contain file path.", "text/markdown");
            return;
        }
        List<String> pos = OptionUtils.positionalArgs(args);

        if (pos.isEmpty()) {
            display("Error: expected usage `%%javasrcConstructorByName <FullyQualifiedClassName>`", "text/markdown");
            return;
        }

        String fqcn = pos.get(0);
        String filename = body;
        if ((filename == null || filename.isBlank()) && fqcn != null && fqcn.contains(".")) {
            Optional<Path> p = PathResolver.resolveSourceFileForClass(fqcn, opts);
            if (p.isPresent())
                filename = p.get().toString();
        }

        String className = fqcn.substring(fqcn.lastIndexOf('.') + 1);

        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(Files.readString(Path.of(filename)));
        } catch (IOException e) {
            display("Error: failed to read file `" + filename + "`: " + e.getMessage(), "text/markdown");
            return;
        }

        Optional<com.github.javaparser.ast.body.ClassOrInterfaceDeclaration> clazz = cu.getClassByName(className);
        if (clazz.isEmpty()) {
            display("Class `" + fqcn + "` not found in file `" + filename + "`.", "text/markdown");
            return;
        }

        List<com.github.javaparser.ast.body.ConstructorDeclaration> ctors = clazz.get().getConstructors();
        if (ctors.isEmpty()) {
            display("No constructors found for `" + fqcn + "`.", "text/markdown");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ctors.size(); i++) {
            sb.append(i).append(": ").append(ctors.get(i).getDeclarationAsString(false, false, false)).append("\n\n");
            sb.append(ctors.get(i).toString()).append("\n\n");
        }

        OutputUtils.formatAndDisplay(sb.toString(), opts);
    }

    @CellMagic("javasrcFieldByName")
    public void javasrcFieldByName(List<String> args, String body) throws IOException {
        Map<String, String> opts = OptionUtils.parseOptions(args);
        if (opts.containsKey("--help") || opts.containsKey("-h")) {
            display("**Usage:** `%%javasrcFieldByName <FullyQualifiedClassName> [filter] [--full=true]`\n\nList or show fields for a class.", "text/markdown");
            return;
        }
        List<String> pos = OptionUtils.positionalArgs(args);

        if (pos.isEmpty()) {
            display("Error: expected usage `%%javasrcFieldByName <FullyQualifiedClassName>`", "text/markdown");
            return;
        }

        String fqcn = pos.get(0);
        String filename = body;
        if ((filename == null || filename.isBlank()) && fqcn != null && fqcn.contains(".")) {
            Optional<Path> p = PathResolver.resolveSourceFileForClass(fqcn, opts);
            if (p.isPresent())
                filename = p.get().toString();
        }

        String className = fqcn.substring(fqcn.lastIndexOf('.') + 1);

        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(Files.readString(Path.of(filename)));
        } catch (IOException e) {
            display("Error: failed to read file `" + filename + "`: " + e.getMessage(), "text/markdown");
            return;
        }

        Optional<com.github.javaparser.ast.body.ClassOrInterfaceDeclaration> clazz = cu.getClassByName(className);
        if (clazz.isEmpty()) {
            display("Class `" + fqcn + "` not found in file `" + filename + "`.", "text/markdown");
            return;
        }

        String fullOpt = opts.getOrDefault("full", "true");
        boolean full = fullOpt.equalsIgnoreCase("true") || fullOpt.equals("1");
        boolean includeJavadoc = opts.getOrDefault("javadoc", "false").equalsIgnoreCase("true");
        String filter = pos.size() >= 2 ? pos.get(1) : null;
        final java.util.regex.Pattern pattern;
        if (filter != null && !filter.isBlank()) {
            java.util.regex.Pattern tmp;
            try {
                tmp = java.util.regex.Pattern.compile(filter);
            } catch (Exception e) {
                tmp = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(filter));
            }
            pattern = tmp;
        } else {
            pattern = null;
        }

        StringBuilder sb = new StringBuilder();
        // Default: list only field names (no attributes). Use `--full=true` to include
        // types/modifiers/source.
        clazz.get().getFields().forEach(f -> {
            java.util.List<String> matched = new java.util.ArrayList<>();
            for (com.github.javaparser.ast.body.VariableDeclarator v : f.getVariables()) {
                String n = v.getNameAsString();
                if (pattern == null || pattern.matcher(n).find())
                    matched.add(n);
            }

            if (matched.isEmpty())
                return;

            if (full) {
                // show full declaration(s) for the field
                sb.append(f.getVariables().stream()
                        .map(v -> f.getElementType().asString() + " " + v.getNameAsString())
                        .collect(Collectors.joining(", ")))
                        .append(" (modifiers: ")
                        .append(f.getModifiers().stream().map(Object::toString).collect(Collectors.joining(" ")))
                        .append(")\n\n");
                sb.append(f.toString()).append("\n\n");
                if (includeJavadoc) {
                    String j = f.getJavadoc().map(JavaMagics::renderJavadoc).orElse(null);
                    if (j != null && !j.isEmpty())
                        sb.append(j).append("\n\n");
                }
            } else {
                for (String name : matched) {
                    sb.append(name).append("\n");
                }
                sb.append("\n");
            }
        });

        if (sb.isEmpty())
            sb.append("(no matching fields)\n");

        OutputUtils.formatAndDisplay(sb.toString(), opts);
    }

    @CellMagic("javasrcJavadoc")
    public void javasrcJavadoc(List<String> args, String body) throws IOException {
        Map<String, String> opts = OptionUtils.parseOptions(args);
        if (opts.containsKey("--help") || opts.containsKey("-h")) {
            display("**Usage:** `%%javasrcJavadoc <FullyQualifiedClassName> [memberName]`\n\nShow javadoc for class or member.", "text/markdown");
            return;
        }
        List<String> pos = OptionUtils.positionalArgs(args);

        if (pos.isEmpty()) {
            display("Error: expected usage `%%javasrcJavadoc <FullyQualifiedClassName> [memberName]`", "text/markdown");
            return;
        }

        String fqcn = pos.get(0);
        String member = pos.size() >= 2 ? pos.get(1) : null;
        String filename = body;
        if ((filename == null || filename.isBlank()) && fqcn != null && fqcn.contains(".")) {
            Optional<Path> p = PathResolver.resolveSourceFileForClass(fqcn, opts);
            if (p.isPresent())
                filename = p.get().toString();
        }

        String className = fqcn.substring(fqcn.lastIndexOf('.') + 1);

        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(Files.readString(Path.of(filename)));
        } catch (IOException e) {
            display("Error: failed to read file `" + filename + "`: " + e.getMessage(), "text/markdown");
            return;
        }

        Optional<com.github.javaparser.ast.body.ClassOrInterfaceDeclaration> clazz = cu.getClassByName(className);
        if (clazz.isEmpty()) {
            display("Class `" + fqcn + "` not found in file `" + filename + "`.", "text/markdown");
            return;
        }

        if (member == null) {
            String out = clazz.get().getJavadoc().map(JavaMagics::renderJavadoc).orElse("(no javadoc)");
            if (opts.getOrDefault("format", "fenced").equals("raw"))
                OutputUtils.formatAndDisplay(out, opts);
            else
                display(out, "text/markdown");
            return;
        }

        // try methods
        Optional<com.github.javaparser.ast.body.MethodDeclaration> m = clazz.get().getMethodsByName(member).stream()
                .findFirst();
        if (m.isPresent()) {
            String out = m.get().getJavadoc().map(JavaMagics::renderJavadoc).orElse("(no javadoc)");
            if (opts.getOrDefault("format", "fenced").equals("raw"))
                OutputUtils.formatAndDisplay(out, opts);
            else
                display(out, "text/markdown");
            return;
        }

        // try fields
        Optional<com.github.javaparser.ast.body.FieldDeclaration> f = clazz.get().getFieldByName(member);
        if (f.isPresent()) {
            String out = f.get().getJavadoc().map(JavaMagics::renderJavadoc).orElse("(no javadoc)");
            if (opts.getOrDefault("format", "fenced").equals("raw"))
                OutputUtils.formatAndDisplay(out, opts);
            else
                display(out, "text/markdown");
            return;
        }

        display("Member `" + member + "` not found in class `" + fqcn + "`.", "text/markdown");
    }

}
