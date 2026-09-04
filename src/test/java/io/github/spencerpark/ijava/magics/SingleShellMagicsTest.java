package io.github.spencerpark.ijava.magics;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SingleShellMagicsTest {

    private SingleShellMagics singleShellMagics;

    @Before
    public void setUp() throws IOException {
        singleShellMagics = new SingleShellMagics();
    }

    @After
    public void tearDown() {
        if (singleShellMagics != null) {
            singleShellMagics.close();
        }
    }

    @Test
    public void testCommonShell() throws IOException, InterruptedException {
        String result = singleShellMagics.commonshell(Collections.emptyList(), "echo Hello, World!");
        assertNotNull(result);
        result = singleShellMagics.commonshell(Collections.emptyList(), "echo Variable Test");
        assertNotNull(result);
    }

    @Test
    public void testCommonShellWithVariables() throws IOException, InterruptedException {
        String var1 = "Hello";
        String var2 = "World";
        String result = singleShellMagics.commonshell(Collections.emptyList(), "echo " + var1 + ", " + var2 + "!");
        assertNotNull(result);
        result = singleShellMagics.commonshell(Collections.emptyList(), "echo Testing " + var1 + " and " + var2);
        assertNotNull(result);
    }

    @Test
    public void testCommonShellCmd() throws IOException, InterruptedException {
        String result = singleShellMagics.commonshellcmd(List.of("echo", "test"));
        assertNotNull(result);
    }

}
