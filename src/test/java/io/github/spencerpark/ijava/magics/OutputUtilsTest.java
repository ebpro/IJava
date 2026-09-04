package io.github.spencerpark.ijava.magics;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

public class OutputUtilsTest {
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
    public void fencedIsDefaultFormat() {
        OutputUtils.formatAndDisplay("int x = 1;", Map.of());
        Assert.assertEquals("```Java\nint x = 1;\n```", String.join("\n", kernel.displayedTexts()));
    }

    @Test
    public void rawDisplaysPlainText() {
        OutputUtils.formatAndDisplay("int x = 1;", Map.of("format", "raw"));
        Assert.assertEquals("int x = 1;", String.join("\n", kernel.displayedTexts()));
    }
}
