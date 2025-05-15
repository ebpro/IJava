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

    private List<String> buildCompilerOptions(Path outputRoot, boolean debug, boolean nowarn) {
        List<String> optionList = new ArrayList<>();
        List<URI> classpath = new ClassGraph().getClasspathURIs();

        optionList.addAll(Arrays.asList(
                "-cp", classpath.stream()
                        .map(URI::toString)
                        .collect(Collectors.joining(File.pathSeparator)),
                "-d", outputRoot.toString()));

        // Add Java version specific options
        String javaVersion = System.getProperty("java.version").split("[.]")[0];
        if (Integer.parseInt(javaVersion) >= 11) {
            optionList.addAll(Arrays.asList(
                    "--enable-preview",
                    "--release", javaVersion));
        }

        // Add debug information if requested
        if (debug) {
            optionList.add("-g");
        }

        // Handle warnings
        if (nowarn) {
            optionList.add("-nowarn");
        } else {
            optionList.addAll(Arrays.asList(
                    "-proc:full",
                    "-implicit:class",
                    "-Xlint:all"));
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
        MagicsArgs schema = MagicsArgs.builder()
                .required("className")
                .flag("verbose", 'v', "Enable verbose output")
                .flag("debug", 'd', "Add debug information")
                .flag("nowarn", 'w', "Suppress warnings")
                .onlyKnownKeywords()
                .onlyKnownFlags()
                .build();
        Map<String, List<String>> vals = schema.parse(args);
        boolean verbose = hasValidFlag(vals, "verbose");
        boolean debug = hasValidFlag(vals, "debug");
        boolean nowarn = hasValidFlag(vals, "nowarn");
        String className = vals.get("className").get(0);

        if (verbose) {
            log.info("Compiling {} with debug={} and nowarn={}", className, debug, nowarn);
        }

        validateClassNameFormat(className);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Java compiler not available. Make sure you're using a JDK.");
        }

        try (CompilationContext context = new CompilationContext(compiler,
                WORKSPACE_DIR.resolve(SOURCE_DIR),
                WORKSPACE_DIR.resolve(OUTPUT_DIR))) {
            // Setup source file
            Path sourceFile = prepareSourceFile(className, body);
            if (verbose) {
                log.info("Source file prepared at: {}", sourceFile);
            }

            // Compile
            CompilerDiagnosticListener diagnostics = new CompilerDiagnosticListener(className, verbose);
            boolean success = compiler.getTask(
                    null,
                    context.fileManager,
                    diagnostics,
                    buildCompilerOptions(context.outputRoot, debug, nowarn),
                    null,
                    context.fileManager.getJavaFileObjects(sourceFile.toFile())).call();

            if (!success || diagnostics.hasErrors()) {
                throw new IOException("Compilation failed for " + className);
            }

            // Add to classpath
            addCompiledClassToClasspath(context.outputRoot, verbose);

            if (verbose) {
                log.info("Successfully compiled {} and added to classpath", className);
            }
        }
    }
}