package io.github.spencerpark.ijava.magics;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ClassDiagramMagicsTest {
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
    public void helpPrintsUsage() {
        try (CapturedOutput output = new CapturedOutput()) {
            new ClassDiagramMagics().classDiagram(List.of("--help"));
            Assert.assertTrue(output.text().contains("%classDiagram"));
            Assert.assertTrue(output.text().contains("USAGE:"));
        }
    }

    @Test
    public void unknownClassReportsNoClasses() {
        try (CapturedOutput output = new CapturedOutput()) {
            new ClassDiagramMagics().classDiagram(List.of("definitely.missing.UnknownClass"));
            Assert.assertTrue(output.text().contains("Classpath error:"));
            Assert.assertTrue(output.text().contains("No classes found."));
        }
    }

    @Test
    public void cellUnknownClassReportsNoClasses() {
        try (CapturedOutput output = new CapturedOutput()) {
            new ClassDiagramMagics().classDiagramCell(List.of(), "definitely.missing.UnknownClass");
            Assert.assertTrue(output.text().contains("No classes found."));
        }
    }

    @Test
    public void umlOnlyDisplaysPlantUmlSource() {
        new ClassDiagramMagics().classDiagram(List.of("java.lang.String", "--uml"));
        Assert.assertTrue(kernel.displayedTexts().stream().anyMatch(text -> text.contains("String")));
    }

    @Test
    public void umlOnlyWritesOutputFile() throws Exception {
        Path outFile = Files.createTempDirectory(Path.of("build", "tmp"), "class-diagram")
                .resolve("diagram.uml");
        new ClassDiagramMagics().classDiagram(List.of("java.lang.String", "--uml", "--out=" + outFile));

        Assert.assertTrue(Files.exists(outFile));
        Assert.assertTrue(Files.readString(outFile).contains("String"));
    }
}
