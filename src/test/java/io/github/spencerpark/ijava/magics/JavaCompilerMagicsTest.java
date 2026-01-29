package io.github.spencerpark.ijava.magics;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class JavaCompilerMagicsTest {

    @Test
    public void testCompileSimpleClassProducesClassFile() throws IOException {
        JavaCompilerMagics magics = new JavaCompilerMagics(path -> {
            // record added classpath (no-op for test)
        });

        Path tmp = Path.of("build", "tmp", "test-compile").toAbsolutePath();
        Files.createDirectories(tmp);

        String className = "com.example.TestHello";
        String source = "public class TestHello { public static String hello() { return \"ok\"; } }";

        List<String> args = List.of("--class=" + className, "--output=" + tmp.toString());

        magics.compile(args, source);

        Path classFile = tmp.resolve("com/example/TestHello.class");
        Assert.assertTrue("Expected compiled class file to exist", Files.exists(classFile));
    }
}
