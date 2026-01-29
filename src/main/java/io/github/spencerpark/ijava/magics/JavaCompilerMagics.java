package io.github.spencerpark.ijava.magics;

import io.github.classgraph.ClassGraph;
import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import io.github.spencerpark.jupyter.kernel.magic.registry.MagicsArgs;
import lombok.extern.slf4j.Slf4j;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class JavaCompilerMagics {

    private static final Path WORKSPACE_DIR = Path.of(System.getProperty("user.home"), ".jupyter", "java-workspace");
    private static final String SOURCE_DIR = "src/main/java";
    private static final String OUTPUT_DIR = "target/classes";

    private final Consumer<String> addToClasspath;

    public JavaCompilerMagics(Consumer<String> addToClasspath) {
        this.addToClasspath = addToClasspath;
    }

    private void validateClassNameFormat(String className) {
        if (!className.matches("^[a-zA-Z]\\w*(\\.[a-zA-Z]\\w*)*$")) {
            throw new IllegalArgumentException("Invalid class name format: " + className);
        }
    }

    private List<String> buildCompilerOptions(Path outputRoot, boolean debug, boolean nowarn,
                                              String release, boolean enablePreview,
                                              String classpathOverride,
                                              List<String> processors,
                                              List<String> processorOptions) {
        List<String> optionList = new ArrayList<>();

        // classpath
        if (classpathOverride != null && !classpathOverride.isEmpty()) {
            optionList.addAll(Arrays.asList("-cp", classpathOverride));
        } else {
            List<URI> classpath = new ClassGraph().getClasspathURIs();
            optionList.addAll(Arrays.asList("-cp", classpath.stream()
                    .map(URI::toString)
                    .collect(Collectors.joining(File.pathSeparator))));
        }

        optionList.addAll(Arrays.asList("-d", outputRoot.toString()));

        if (release != null && !release.isEmpty()) {
            optionList.addAll(Arrays.asList("--release", release));
        }

        if (enablePreview) optionList.add("--enable-preview");

        // Add debug information if requested
        if (debug) {
            optionList.add("-g");
        }

        // Handle warnings
        if (nowarn) {
            optionList.add("-nowarn");
        } else {
            optionList.addAll(Arrays.asList("-proc:full", "-implicit:class", "-Xlint:all"));
        }

        if (processors != null && !processors.isEmpty()) {
            optionList.addAll(Arrays.asList("-processor", String.join(",", processors)));
        }

        if (processorOptions != null) {
            for (String po : processorOptions) optionList.add("-A" + po);
        }

        return optionList;
    }

    private static class CompilationContext implements AutoCloseable {
        private final Path outputRoot;
        private final StandardJavaFileManager fileManager;

        public CompilationContext(JavaCompiler compiler, Path sourceRoot, Path outputRoot) throws IOException {
            this.outputRoot = outputRoot;
            this.fileManager = compiler.getStandardFileManager(null, null, null);
            Files.createDirectories(outputRoot);
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(outputRoot.toFile()));
        }

        @Override
        public void close() throws IOException {
            fileManager.close();
        }
    }

    @Slf4j
    private static class CompilerDiagnosticListener implements javax.tools.DiagnosticListener<JavaFileObject> {
        private final boolean verbose;
        private final String className;
        private final List<String> errors = new ArrayList<>();

        public CompilerDiagnosticListener(String className, boolean verbose) {
            this.className = className;
            this.verbose = verbose;
        }

        @Override
        public void report(javax.tools.Diagnostic<? extends JavaFileObject> diagnostic) {
            String message = String.format("%s:%d - %s",
                    className,
                    diagnostic.getLineNumber(),
                    diagnostic.getMessage(null));

            if (diagnostic.getKind() == javax.tools.Diagnostic.Kind.ERROR) {
                errors.add(message);
                log.error(message);
            } else if (verbose) {
                log.info(message);
            }
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    private Path prepareSourceFile(String className, String sourceCode) throws IOException {
        // Create necessary directories
        Files.createDirectories(WORKSPACE_DIR.resolve(SOURCE_DIR));
        Files.createDirectories(WORKSPACE_DIR.resolve(OUTPUT_DIR));

        // Convert class name to path format
        String packagePath = className.substring(0, className.lastIndexOf('.'));
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        Path packageDir = WORKSPACE_DIR.resolve(SOURCE_DIR)
                .resolve(packagePath.replace('.', File.separatorChar));

        // Create package directory if it doesn't exist
        Files.createDirectories(packageDir);

        // Create source file
        Path sourceFile = packageDir.resolve(simpleClassName + ".java");

        // Add package declaration if not present
        if (!sourceCode.contains("package " + packagePath)) {
            sourceCode = String.format("package %s;%n%n%s", packagePath, sourceCode);
        }

        // Write source code to file
        Files.writeString(sourceFile, sourceCode);

        if (log.isDebugEnabled()) {
            log.debug("Created source file at: {}", sourceFile);
        }

        return sourceFile;
    }

    private Path writeSourceTo(Path sourceRoot, String className, String sourceCode) throws IOException {
        // className: com.example.Foo
        String packagePath = className.substring(0, className.lastIndexOf('.'));
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        Path packageDir = sourceRoot.resolve(packagePath.replace('.', File.separatorChar));
        Files.createDirectories(packageDir);
        Path sourceFile = packageDir.resolve(simpleClassName + ".java");

        // add package if missing
        boolean hasPackage = sourceCode.contains("package "+packagePath);
        String out = sourceCode;
        if (!hasPackage) out = String.format("package %s;%n%n%s", packagePath, sourceCode);

        Files.writeString(sourceFile, out);
        return sourceFile;
    }

    private void addCompiledClassToClasspath(Path outputRoot, boolean verbose) throws IOException {
        if (!Files.exists(outputRoot)) {
            throw new IOException("Compilation output directory does not exist: " + outputRoot);
        }

        // Add the output directory to the classpath
        String outputPath = outputRoot.toAbsolutePath().toString();
        addToClasspath.accept(outputPath);

        if (verbose) {
            log.info("Added to classpath: {}", outputPath);
        }
    }

    private boolean hasValidFlag(Map<String, List<String>> vals, String key) {
        return vals.containsKey(key) &&
                !vals.get(key).isEmpty() &&
                !vals.get(key).get(0).isEmpty();
    }

    @CellMagic("compile")
    public void compile(List<String> args, String body) throws IOException {
        // If user asked for help, short-circuit before any argument parsing that
        // requires
        // required positional parameters (like className).
        if (args.contains("--help") || args.contains("-h")) {
            System.out.println("""
                    ## %%compile - Compile Java source code and add to classpath

                    **Usage:** `%%compile [--verbose] [--debug] [--nowarn] fully.qualified.ClassName`

                    **Arguments:**
                    - `className` : Fully qualified class name (e.g., com.example.MyClass)

                    **Options:**
                    - `--verbose, -v` : Enable verbose compilation output
                    - `--debug, -d` : Include debug information in compiled classes
                    - `--nowarn, -w` : Suppress compiler warnings
                    - `--help, -h` : Show this help message

                    **Examples:**
                    ```
                    %%compile com.example.Calculator
                    public class Calculator {
                        public int add(int a, int b) { return a + b; }
                    }
                    ```

                    ```
                    %%compile --verbose --debug com.example.MyClass
                    public class MyClass {
                        public void hello() { System.out.println("Hello!"); }
                    }
                    ```

                    **Note:** Package declaration will be added automatically if not present.
                    """);
            return;
        }

        // Simple option parsing (MagicsArg schema doesn't handle repeatable processor-option easily)
        String className = null;
        boolean verbose = false;
        boolean debug = false;
        boolean nowarn = false;
        boolean dryRun = false;
        String release = null;
        boolean enablePreview = false;
        String outputDir = null;
        String classpathOverride = null;
        List<String> processors = new ArrayList<>();
        List<String> processorOptions = new ArrayList<>();
        String processorPath = null;

        for (String a : args) {
            if (a.equals("--help") || a.equals("-h")) continue;
            if (a.equals("--verbose") || a.equals("-v")) { verbose = true; continue; }
            if (a.equals("--debug") || a.equals("-d")) { debug = true; continue; }
            if (a.equals("--dry-run") || a.equals("-n")) { dryRun = true; continue; }
            if (a.equals("--nowarn") || a.equals("-w")) { nowarn = true; continue; }
            if (a.startsWith("--class=")) { className = a.substring(a.indexOf('=')+1); continue; }
            if (a.startsWith("--release=")) { release = a.substring(a.indexOf('=')+1); continue; }
            if (a.equals("--enable-preview")) { enablePreview = true; continue; }
            if (a.startsWith("--output=")) { outputDir = a.substring(a.indexOf('=')+1); continue; }
            if (a.startsWith("--classpath=") || a.startsWith("--cp=")) { int eq=a.indexOf('='); classpathOverride = a.substring(eq+1); continue; }
            if (a.startsWith("--processor=")) { processors.add(a.substring(a.indexOf('=')+1)); continue; }
            if (a.startsWith("--processor-path=")) { processorPath = a.substring(a.indexOf('=')+1); continue; }
            if (a.startsWith("--processor-option=")) { processorOptions.add(a.substring(a.indexOf('=')+1)); continue; }
            // fallback positional className if none of the above
            if (className == null && !a.contains("=")) className = a;
        }

        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("Please specify fully qualified class name via --class=... or as first arg");
        }

        if (verbose) log.info("Compiling {} with debug={} and nowarn={}", className, debug, nowarn);

        validateClassNameFormat(className);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Java compiler not available. Make sure you're using a JDK.");
        }

        Path sourceRoot;
        Path outputRoot;
        if (outputDir != null && !outputDir.isEmpty()) {
            outputRoot = Path.of(outputDir).toAbsolutePath();
            sourceRoot = outputRoot.resolve("src");
            Files.createDirectories(sourceRoot);
            Files.createDirectories(outputRoot);
        } else {
            sourceRoot = WORKSPACE_DIR.resolve(SOURCE_DIR);
            outputRoot = WORKSPACE_DIR.resolve(OUTPUT_DIR);
            Files.createDirectories(sourceRoot);
            Files.createDirectories(outputRoot);
        }

        Path sourceFile = writeSourceTo(sourceRoot, className, body);

        if (verbose) log.info("Source file prepared at: {}", sourceFile);

        if (dryRun) {
            List<String> optsList = buildCompilerOptions(outputRoot, debug, nowarn, release, enablePreview, classpathOverride, processors, processorOptions);
            System.out.println("Dry run: would compile source file: " + sourceFile);
            System.out.println("With javac options: " + String.join(" ", optsList));
            return;
        }

        try (CompilationContext context = new CompilationContext(compiler, sourceRoot, outputRoot)) {
            // configure processor path if present
            if (processorPath != null && !processorPath.isEmpty()) {
                var paths = Arrays.stream(processorPath.split(File.pathSeparator)).map(Path::of).map(Path::toFile).collect(Collectors.toList());
                context.fileManager.setLocation(StandardLocation.ANNOTATION_PROCESSOR_PATH, paths);
            }

            CompilerDiagnosticListener diagnostics = new CompilerDiagnosticListener(className, verbose);

            List<String> opts = buildCompilerOptions(outputRoot, debug, nowarn, release, enablePreview, classpathOverride, processors, processorOptions);

            boolean success = compiler.getTask(
                    null,
                    context.fileManager,
                    diagnostics,
                    opts,
                    null,
                    context.fileManager.getJavaFileObjectsFromFiles(List.of(sourceFile.toFile()))).call();

            if (!success || diagnostics.hasErrors()) {
                throw new IOException("Compilation failed for " + className);
            }

            // Add to classpath
            addCompiledClassToClasspath(outputRoot, verbose);

            if (verbose) log.info("Successfully compiled {} and added to classpath", className);
        }
    }

    // Deprecated alias removed: use %%compile instead
}
