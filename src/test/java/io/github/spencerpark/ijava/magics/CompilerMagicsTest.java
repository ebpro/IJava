package io.github.spencerpark.ijava.magics;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class CompilerMagicsTest {

    @Test
    public void helpPrintsUsage() {
        try (CapturedOutput output = new CapturedOutput()) {
            new CompilerMagics(path -> { }).mycompile(List.of("--help"), "");
            Assert.assertTrue(output.text().contains("## %%mycompile"));
            Assert.assertTrue(output.text().contains("Usage:"));
        }
    }

    @Test
    public void emptyArgsThrows() {
        try {
            new CompilerMagics(path -> { }).mycompile(List.of(), "");
            Assert.fail("Expected RuntimeException");
        } catch (RuntimeException expected) {
            Assert.assertTrue(expected.getMessage().contains("Class Canonical Name"));
        }
    }

    @Test
    public void buildCompilerOptionsFallsBackWithoutKernel() {
        CompilerMagics magics = new CompilerMagics(path -> { });
        Assert.assertNotNull(magics.buildCompilerOptions());
    }
}
