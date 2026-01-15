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
import java.util.stream.Collectors;

import static io.github.spencerpark.ijava.runtime.Display.display;

@Slf4j
public class JavaMagics {

    @CellMagic("javasrcMethodByAnnotationName")
    public void javasrcMethodByAnnotationName(List<String> args, String body) throws IOException {
        Map<String, String> opts = OptionUtils.parseOptions(args);
        List<String> pos = OptionUtils.positionalArgs(args);

        if (pos.size() < 2) {
            display("Error: expected usage `%%javasrcMethodByAnnotationName <ClassName> <AnnotationName> [index]`", "text/markdown");
            return;
        }

        String filename = body;
        String className = pos.get(0);
        String simpleClassName = className != null && className.contains(".") ? className.substring(className.lastIndexOf('.') + 1) : className;
        String annotationName = pos.get(1);
        int index = pos.size() >= 3 ? Integer.parseInt(pos.get(2)) : 0;

        if ((filename == null || filename.isBlank()) && className != null && className.contains(".")) {
            Optional<Path> p = PathResolver.resolveSourceFileForClass(className, opts);
            if (p.isPresent()) filename = p.get().toString();
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
            display("Class `" + className + "` not found in file `" + filename + "`.", "text/markdown");
            return;
        }

        List<com.github.javaparser.ast.body.MethodDeclaration> matches = clazz.get().getMethods()
                .stream()
                .filter(m -> m.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals(annotationName)))
                .collect(Collectors.toList());

        if (matches.isEmpty()) {
            display("No methods annotated with `@" + annotationName + "` found in class `" + className + "`.", "text/markdown");
            return;
        }

        if (index < 0 || index >= matches.size()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(matches.size()).append(" matching methods:\n\n");
            for (int i = 0; i < matches.size(); i++) {
                sb.append(i).append(": ").append(matches.get(i).getDeclarationAsString(false, false, false)).append("\n");
            }
            display(sb.toString(), opts.getOrDefault("format", "fenced").equals("raw") ? "text/plain" : "text/markdown");
            return;
        }

        String out = matches.get(index).toString();
        OutputUtils.formatAndDisplay(out, opts);
    }

    @CellMagic("javasrcMethodByName")
    public void javasrcMethodByName(List<String> args, String body) throws IOException {
        // If the user requested help, short-circuit immediately and do not
        // attempt any file reads or parsing of the cell body which may cause
        // spurious build/parse attempts (e.g. when the body is empty or a
        // comment). This ensures `--help` never triggers a build.
        if (args != null && (args.contains("--help") || args.contains("-h"))) {
            String help = "**Usage:** `%%javasrcMethodByName [options] <FullyQualifiedClassName|ClassName> [methodName|index]`\n\n" +
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
        List<String> pos = OptionUtils.positionalArgs(args);

        if (pos.size() < 1 && !opts.containsKey("methodRegex")) {
            display("Error: expected usage `%%javasrcMethodByName <ClassName> <MethodName|index>` or use `methodRegex=...`", "text/markdown");
            return;
        }

        String filename = body;
        String className = pos.size() >= 1 ? pos.get(0) : null;
        String simpleClassName = className != null && className.contains(".") ? className.substring(className.lastIndexOf('.') + 1) : className;
        String methodName = pos.size() >= 2 ? pos.get(1) : null;
        int index = pos.size() >= 3 ? Integer.parseInt(pos.get(2)) : 0;

        if ((filename == null || filename.isBlank()) && className != null && className.contains(".")) {
            Optional<Path> p = PathResolver.resolveSourceFileForClass(className, opts);
            if (p.isPresent()) filename = p.get().toString();
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
            display("Class `" + className + "` not found in file `" + filename + "`.", "text/markdown");
            return;
        }

        List<com.github.javaparser.ast.body.MethodDeclaration> methods = new ArrayList<>();
        if (opts.containsKey("methodRegex")) {
            Pattern p = Pattern.compile(opts.get("methodRegex"));
            methods = clazz.get().getMethods().stream().filter(m -> p.matcher(m.getNameAsString()).find()).collect(Collectors.toList());
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
                sb.append(i).append(": ").append(methods.get(i).getDeclarationAsString(false, false, false)).append("\n");
            }
            display(sb.toString(), opts.getOrDefault("format", "fenced").equals("raw") ? "text/plain" : "text/markdown");
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
        List<String> pos = OptionUtils.positionalArgs(args);

        if (pos.size() < 1) {
            display("Error: expected usage `%%javasrcInterfaceByName <FullyQualifiedInterfaceName>`", "text/markdown");
            return;
        }

        String fqcn = pos.get(0);
        String filename = body;
        if ((filename == null || filename.isBlank()) && fqcn != null && fqcn.contains(".")) {
            Optional<Path> p = PathResolver.resolveSourceFileForClass(fqcn, opts);
            if (p.isPresent()) filename = p.get().toString();
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
            display("Interface `" + className + "` not found in file `" + filename + "`.", "text/markdown");
            return;
        }

        String out = iface.get().toString();
        OutputUtils.formatAndDisplay(out, opts);
    }

    @CellMagic("javasrcClassByName")
    public void javasrcClassByName(List<String> args, String body) throws IOException {
        Map<String, String> opts = OptionUtils.parseOptions(args);
        List<String> pos = OptionUtils.positionalArgs(args);

        if (pos.isEmpty()) {
            display("Error: expected usage `%%javasrcClassByName <FullyQualifiedClassName>`", "text/markdown");
            return;
        }

        String fqcn = pos.get(0);
        String filename = body;
        if ((filename == null || filename.isBlank()) && fqcn != null && fqcn.contains(".")) {
            Optional<Path> p = PathResolver.resolveSourceFileForClass(fqcn, opts);
            if (p.isPresent()) filename = p.get().toString();
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
            display("Class `" + className + "` not found in file `" + filename + "`.", "text/markdown");
            return;
        }

        String out = LexicalPreservingPrinter.print(clazz.get());
        OutputUtils.formatAndDisplay(out, opts);
    }

    @CellMagic("javasrcList")
    public void javasrcList(List<String> args, String body) throws IOException {
        Map<String, String> opts = OptionUtils.parseOptions(args);
        List<String> pos = OptionUtils.positionalArgs(args);

        String filename = body;
        if ((filename == null || filename.isBlank()) && !pos.isEmpty() && pos.get(0).contains(".")) {
            Optional<Path> p = PathResolver.resolveSourceFileForClass(pos.get(0), opts);
            if (p.isPresent()) filename = p.get().toString();
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
            t.getMethods().forEach(m -> sb.append("  - ").append(m.getDeclarationAsString(false, false, false)).append("\n"));
            sb.append("\n");
        });

        display(sb.toString(), "text/markdown");
    }

}