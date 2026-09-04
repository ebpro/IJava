package io.github.spencerpark.ijava.magics;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClasspathMagicsTest {

    @Test
    public void jarsHelpPrintsUsage() {
        List<String> captured = new ArrayList<>();
        try (CapturedOutput output = new CapturedOutput()) {
            List<String> result = new ClasspathMagics(captured::add).jars(List.of("--help"));
            Assert.assertTrue(output.text().contains("%jars"));
            Assert.assertTrue(result.isEmpty());
            Assert.assertTrue(captured.isEmpty());
        }
    }

    @Test
    public void jarsResolvesMatchingJarFiles() throws Exception {
        Path base = Path.of("build", "tmp", "classpath-magics", "jars-" + UUID.randomUUID());
        Path lib = base.resolve("lib");
        Files.createDirectories(lib);
        Files.writeString(lib.resolve("alpha.jar"), "jar");
        Files.writeString(lib.resolve("beta.jar"), "jar");
        Files.writeString(lib.resolve("notes.txt"), "not a jar");

        List<String> captured = new ArrayList<>();
        String glob = lib.resolve("*.jar").toString().replace('\\', '/');
        List<String> result = new ClasspathMagics(captured::add).jars(List.of(glob));

        List<String> names = result.stream().map(p -> Path.of(p).getFileName().toString()).sorted().toList();
        Assert.assertEquals(List.of("alpha.jar", "beta.jar"), names);
        Assert.assertEquals(result, captured);
    }

    @Test
    public void classpathResolvesDirectoriesAndFiles() throws Exception {
        Path base = Path.of("build", "tmp", "classpath-magics", "classpath-" + UUID.randomUUID());
        Path lib = base.resolve("lib");
        Path classes = base.resolve("classes");
        Files.createDirectories(lib);
        Files.createDirectories(classes);
        Files.writeString(lib.resolve("alpha.jar"), "jar");
        Files.writeString(lib.resolve("beta.jar"), "jar");

        List<String> captured = new ArrayList<>();
        List<String> args = List.of(
                lib.toString().replace('\\', '/'),
                classes.toString().replace('\\', '/'),
                lib.resolve("*.jar").toString().replace('\\', '/'));
        List<String> result = new ClasspathMagics(captured::add).classpath(args);

        List<String> names = result.stream().map(p -> Path.of(p).getFileName().toString()).sorted().toList();
        Assert.assertEquals(List.of("alpha.jar", "beta.jar", "classes", "lib"), names);
        Assert.assertEquals(result, captured);
    }

    @Test
    public void classpathSnapshotReturnsCurrentClassPath() {
        try (CapturedOutput output = new CapturedOutput()) {
            String result = new ClasspathMagics(path -> {
            }).classpathSnapshot(List.of());
            String classPath = System.getProperty("java.class.path");
            Assert.assertFalse(classPath.isBlank());
            Assert.assertTrue(result.contains(classPath.split(File.pathSeparator)[0]));
            Assert.assertEquals(result + System.lineSeparator(), output.text());
        }
    }

    @Test
    public void classpathSnapshotHelpPrintsUsage() {
        try (CapturedOutput output = new CapturedOutput()) {
            String result = new ClasspathMagics(path -> {
            }).classpathSnapshot(List.of("-h"));
            Assert.assertTrue(output.text().contains("%classpath-snapshot"));
            Assert.assertEquals("", result);
        }
    }
}
