package io.github.spencerpark.ijava.magics;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class MagicsToolTest {

    @Test
    public void readFromFileNoArgsPrintsHelp() throws Exception {
        try (CapturedOutput output = new CapturedOutput()) {
            String result = new MagicsTool().readFromFile(List.of());
            Assert.assertTrue(output.text().contains("%read"));
            Assert.assertNull(result);
        }
    }

    @Test
    public void readFromFileReturnsFileContent() throws Exception {
        Path file = Path.of("build", "tmp", "magics-tool", "read-" + UUID.randomUUID() + ".txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "line one\nline two");

        String result = new MagicsTool().readFromFile(List.of(file.toString()));
        Assert.assertEquals("line one\nline two", result);
    }

    @Test(expected = NoSuchFileException.class)
    public void readFromFileMissingFileThrows() throws Exception {
        Path missing = Path.of("build", "tmp", "magics-tool", "missing-" + UUID.randomUUID() + ".txt");
        new MagicsTool().readFromFile(List.of(missing.toString()));
    }

    @Test
    public void loadFileNoArgsReturnsNull() throws Exception {
        Assert.assertNull(new MagicsTool().loadFile(List.of()));
    }

    @Test
    public void loadFileReturnsFileContent() throws Exception {
        Path file = Path.of("build", "tmp", "magics-tool", "load-" + UUID.randomUUID() + ".jsh");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "int a = 1;\nint b = 2;");

        String result = new MagicsTool().loadFile(List.of(file.toString()));
        Assert.assertEquals("int a = 1;\nint b = 2;", result);
    }

    @Test
    public void writeCellMagicWritesBodyToFile() throws Exception {
        Path file = Path.of("build", "tmp", "magics-tool", "write-" + UUID.randomUUID() + ".txt");
        try (CapturedOutput output = new CapturedOutput()) {
            new MagicsTool().writeToFile(List.of(file.toString()), "written body");
            Assert.assertTrue(output.text().contains("Write to"));
            Assert.assertTrue(output.text().contains("success."));
        }
        Assert.assertEquals("written body", Files.readString(file));
    }

    @Test
    public void writeLineMagicNoArgsPrintsHelp() throws Exception {
        try (CapturedOutput output = new CapturedOutput()) {
            new MagicsTool().writeToFile(List.of());
            Assert.assertTrue(output.text().contains("%write"));
        }
    }

    @Test
    public void reloadClassNoArgsPrintsUsage() {
        try (CapturedOutput output = new CapturedOutput()) {
            new MagicsTool().reloadClass(List.of());
            Assert.assertTrue(output.text().contains("Usage: %reload-class"));
        }
    }

    @Test
    public void reloadClassMissingClassReportsFailure() {
        String missingClass = "Missing" + UUID.randomUUID().toString().replace("-", "");
        try (CapturedOutput output = new CapturedOutput()) {
            new MagicsTool().reloadClass(List.of(missingClass));
            Assert.assertTrue(output.text().contains("Failed to load/initialize"));
        }
    }

    @Test
    public void classInfoNoArgsPrintsUsage() {
        try (CapturedOutput output = new CapturedOutput()) {
            new MagicsTool().classInfo(List.of());
            Assert.assertTrue(output.text().contains("Usage: %class-info"));
        }
    }

    @Test
    public void classInfoMissingClassReportsFailure() {
        String missingClass = "Missing" + UUID.randomUUID().toString().replace("-", "");
        try (CapturedOutput output = new CapturedOutput()) {
            new MagicsTool().classInfo(List.of(missingClass));
            Assert.assertTrue(output.text().contains("Failed to inspect"));
        }
    }

    @Test
    public void whereNoArgsPrintsUsage() {
        try (CapturedOutput output = new CapturedOutput()) {
            new MagicsTool().where(List.of());
            Assert.assertTrue(output.text().contains("Usage: %where"));
        }
    }

    @Test
    public void listMagicPrintsHeaders() {
        try (CapturedOutput output = new CapturedOutput()) {
            new MagicsTool().listMagic(List.of());
            Assert.assertTrue(output.text().contains("registered line magics"));
            Assert.assertTrue(output.text().contains("registered cell magics"));
        }
    }

    @Test
    public void runCommandNoArgsReturnsQuietly() throws Exception {
        try (CapturedOutput output = new CapturedOutput()) {
            new MagicsTool().runCommand(List.of());
            Assert.assertTrue(output.text().isEmpty());
        }
    }
}
