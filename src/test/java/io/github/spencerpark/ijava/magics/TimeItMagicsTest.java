package io.github.spencerpark.ijava.magics;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class TimeItMagicsTest {
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
    public void helpPrintsExampleWithoutEvaluating() throws Exception {
        try (CapturedOutput output = new CapturedOutput()) {
            new TimeItMagics().timeIt(List.of("--help"), "1 + 1");
            Assert.assertTrue(output.text().contains("%%time epochs=3 loops=5"));
            Assert.assertTrue(kernel.evaluated().isEmpty());
        }
    }

    @Test
    public void noArgsUsesDefaultWarmupAndIterations() throws Exception {
        new TimeItMagics().timeIt(List.of(), "int x = 1;");
        Assert.assertEquals(6, kernel.evaluated().size());
        Assert.assertTrue(kernel.evaluated().stream().allMatch("int x = 1;"::equals));
    }

    @Test
    public void parsesWarmupAndIterations() throws Exception {
        try (CapturedOutput output = new CapturedOutput()) {
            new TimeItMagics().timeIt(List.of("warmup=2", "iterations=3"), "int y = 2;");
            Assert.assertEquals(5, kernel.evaluated().size());
            Assert.assertTrue(output.text().contains("samples:"));
            Assert.assertTrue(output.text().contains("min="));
            Assert.assertTrue(output.text().contains("median="));
            Assert.assertTrue(output.text().contains("max="));
        }
    }

    @Test
    public void invalidParametersAreIgnored() throws Exception {
        new TimeItMagics().timeIt(List.of("warmup=not-a-number", "iterations=also-not"), "int z = 3;");
        Assert.assertEquals(6, kernel.evaluated().size());
    }
}
