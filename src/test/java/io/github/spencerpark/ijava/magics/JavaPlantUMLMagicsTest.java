package io.github.spencerpark.ijava.magics;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class JavaPlantUMLMagicsTest {
    private CapturingKernels.CapturingKernel kernel;

    @Before
    public void setUp() throws Exception {
        kernel = CapturingKernels.install();
        kernel.reset();
    }

    @After
    public void tearDown() throws Exception {
        CapturingKernels.uninstall();
    }

    @Test
    public void plantUMLHelpDisplaysMarkdown() throws Exception {
        new JavaPlantUMLMagics().plantUML(List.of("--help"), "");
        Assert.assertTrue(kernel.displayedTexts().stream().anyMatch(text -> text.contains("## %%plantUML")));
    }

    @Test
    public void plantUMLFileHelpPrintsToStdOut() {
        try (CapturedOutput output = new CapturedOutput()) {
            new JavaPlantUMLMagics().plantUMLFile(List.of("-h"), "");
            Assert.assertTrue(output.text().contains("## %%plantUMLFile"));
        }
    }

    @Test
    public void plantUMLFileRejectsMultipleArgs() {
        try {
            new JavaPlantUMLMagics().plantUMLFile(List.of("SVG", "PNG"), "");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void plantUMLFileEmptyBodyReturnsQuietly() {
        try (CapturedOutput output = new CapturedOutput()) {
            new JavaPlantUMLMagics().plantUMLFile(List.of(), "");
            Assert.assertTrue(output.text().isEmpty());
            Assert.assertTrue(kernel.displays().isEmpty());
        }
    }

    @Test
    public void plantUMLFileMissingFileThrows() {
        try {
            new JavaPlantUMLMagics().plantUMLFile(List.of(), "build/tmp/missing-plantuml-file.puml");
            Assert.fail("Expected RuntimeException");
        } catch (RuntimeException expected) {
        }
    }
}
