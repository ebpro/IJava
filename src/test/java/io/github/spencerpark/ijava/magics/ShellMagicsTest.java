package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.ijava.JavaKernel;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assume.assumeTrue;

public class ShellMagicsTest {

    private final PrintStream oldOut = System.out;
    private final PrintStream oldErr = System.err;

    @After
    public void tearDown() {
        System.setOut(oldOut);
        System.setErr(oldErr);
    }

    @Test
    public void testShellEcho() throws Exception {
        assumeTrue(new File("/bin/sh").canExecute());
        ShellMagics magics = new ShellMagics();

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        System.setOut(new PrintStream(bout));

        List<String> args = Arrays.asList("--shell=/bin/sh");
        magics.shell(args, "echo hello-test-123");

        String out = bout.toString();
        Assert.assertTrue("Expected output to contain echo text", out.contains("hello-test-123"));
    }

    @Test
    public void testShellTimeout() throws Exception {
        assumeTrue(new File("/bin/sh").canExecute());
        ShellMagics magics = new ShellMagics();

        List<String> args = Arrays.asList("--shell=/bin/sh", "--timeout=1");
        try {
            magics.shell(args, "sleep 2");
            Assert.fail("Expected timeout to throw RuntimeException");
        } catch (RuntimeException e) {
            // expected
        }
    }

    @Test
    public void testExecutorShutdownOnKernelShutdown() throws Exception {
        // Ensure executor not shutdown initially
        Assert.assertFalse(ShellMagics.isExecutorShutdown());

        JavaKernel kernel = new JavaKernel();
        kernel.onShutdown(false);

        Assert.assertTrue("Executor should be shutdown after kernel.onShutdown", ShellMagics.isExecutorShutdown());
    }
}
