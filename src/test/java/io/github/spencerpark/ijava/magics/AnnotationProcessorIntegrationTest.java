package io.github.spencerpark.ijava.magics;

import org.junit.Assert;
import org.junit.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class AnnotationProcessorIntegrationTest {

    @Test
    public void testAnnotationProcessorGeneratesClass() throws IOException {
        // prepare temp dirs
        Path base = Path.of("build", "tmp", "ap-integration").toAbsolutePath();
        Path procSrc = base.resolve("proc-src");
        Path procClasses = base.resolve("proc-classes");
        Path out = base.resolve("out");
        Files.createDirectories(procSrc);
        Files.createDirectories(procClasses);
        Files.createDirectories(out);

        // write annotation source
        String annoSrc = "package com.example.ap; public @interface AutoGen {}";
        Path annoFile = procSrc.resolve(Path.of("com", "example", "ap", "AutoGen.java"));
        Files.createDirectories(annoFile.getParent());
        Files.writeString(annoFile, annoSrc);

        // write processor source
        String proc = "package com.example.ap;\n" +
                "import javax.annotation.processing.*;\n" +
                "import javax.lang.model.SourceVersion;\n" +
                "import javax.lang.model.element.*;\n" +
                "import javax.tools.JavaFileObject;\n" +
                "import java.io.Writer;\n" +
                "import java.util.Set;\n" +
                "@SupportedAnnotationTypes(\"com.example.ap.AutoGen\")\n" +
                "@SupportedSourceVersion(SourceVersion.RELEASE_8)\n" +
                "public class AutoGenProcessor extends AbstractProcessor {\n" +
                "  @Override\n" +
                "  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {\n" +
                "    try {\n" +
                "      for (TypeElement t : annotations) {\n" +
                "        for (Element e : roundEnv.getElementsAnnotatedWith(t)) {\n" +
                "          String gen = \"package com.example.gen; public class GeneratedHello { public static String msg() { return \\\"generated\\\"; } }\";\n" +
                "          JavaFileObject jf = processingEnv.getFiler().createSourceFile(\"com.example.gen.GeneratedHello\");\n" +
                "          try (Writer w = jf.openWriter()) { w.write(gen); }\n" +
                "        }\n" +
                "      }\n" +
                "    } catch (Exception ex) { throw new RuntimeException(ex); }\n" +
                "    return true;\n" +
                "  }\n" +
                "}\n";
        Path procFile = procSrc.resolve(Path.of("com", "example", "ap", "AutoGenProcessor.java"));
        Files.writeString(procFile, proc);

        // compile processor & annotation
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system java compiler available for tests");
        }
        var diagnostics = compiler.getTask(null, null, null, List.of("-d", procClasses.toString()), null,
                compiler.getStandardFileManager(null, null, null).getJavaFileObjectsFromFiles(
                        List.of(annoFile.toFile(), procFile.toFile()))).call();
        // simple check: compiled classes exist
        Path procClass = procClasses.resolve(Path.of("com", "example", "ap", "AutoGenProcessor.class"));
        Assert.assertTrue("processor class should be compiled", Files.exists(procClass));

        // Now compile a user source that uses the annotation, via JavaCompilerMagics
        JavaCompilerMagics magics = new JavaCompilerMagics(p -> {});
        String userClass = "@com.example.ap.AutoGen public class UseIt { }";
        List<String> args = List.of("--class=com.example.use.UseIt", "--output=" + out.toString(), "--processor-path=" + procClasses.toString(), "--processor=com.example.ap.AutoGenProcessor", "--classpath=" + procClasses.toString());
        magics.compile(args, userClass);

        Path generatedClass = out.resolve(Path.of("com", "example", "gen", "GeneratedHello.class"));
        Assert.assertTrue("Generated class should exist", Files.exists(generatedClass));
    }
}
