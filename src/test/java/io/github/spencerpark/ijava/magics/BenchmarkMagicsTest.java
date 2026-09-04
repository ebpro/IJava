package io.github.spencerpark.ijava.magics;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class BenchmarkMagicsTest {
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
    public void helpPrintsUsageWithoutEvaluating() throws Exception {
        try (CapturedOutput output = new CapturedOutput()) {
            new BenchmarkMagics().benchmark(List.of("--help"), "");
            Assert.assertTrue(output.text().contains("%%benchmark"));
            Assert.assertTrue(kernel.evaluated().isEmpty());
        }
    }

    @Test
    public void noImplementationsPrintsMessage() throws Exception {
        try (CapturedOutput output = new CapturedOutput()) {
            new BenchmarkMagics().benchmark(List.of(), "");
            Assert.assertTrue(output.text().contains("No implementations provided"));
            Assert.assertTrue(kernel.evaluated().isEmpty());
        }
    }

    @Test
    public void runsWarmupAndIterationsForEachImplementation() throws Exception {
        try (CapturedOutput output = new CapturedOutput()) {
            String body = "int a = 1;\n---\nint b = 2;";
            new BenchmarkMagics().benchmark(List.of("iterations=2", "warmup=1"), body);
            Assert.assertEquals(6, kernel.evaluated().size());
            Assert.assertTrue(output.text().contains("Benchmark results (nanoseconds):"));
            Assert.assertTrue(output.text().contains("Impl 0:"));
            Assert.assertTrue(output.text().contains("Impl 1:"));
        }
    }

    @Test
    public void chartOptionDisplaysSvg() throws Exception {
        new BenchmarkMagics().benchmark(List.of("iterations=1", "warmup=0", "--chart"), "int x = 1;");
        Assert.assertEquals(1, kernel.evaluated().size());
        Assert.assertTrue(kernel.displayedTexts().stream().anyMatch(text -> text.contains("<svg")));
    }

    @Test
    public void invalidIterationsThrows() {
        try {
            new BenchmarkMagics().benchmark(List.of("iterations=not-a-number"), "int x = 1;");
            Assert.fail("Expected NumberFormatException");
        } catch (NumberFormatException expected) {
        } catch (Exception e) {
            Assert.fail("Expected NumberFormatException but got " + e);
        }
    }
}
