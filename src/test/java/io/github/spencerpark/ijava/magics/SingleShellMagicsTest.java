package io.github.spencerpark.ijava.magics;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SingleShellMagicsTest {

    private SingleShellMagics singleShellMagics;

    @Before
    public void setUp() {
        singleShellMagics = new SingleShellMagics();
    }

    @After
    public void tearDown() {
        singleShellMagics.close();
    }

    @Test
    public void testShell() throws IOException {
        singleShellMagics.shell(Collections.emptyList(), "echo Hello, World!");
        singleShellMagics.shell(Collections.emptyList(), "echo Variable Test");
    }

    @Test
    public void testShellWithVariables() throws IOException {
        String var1 = "Hello";
        String var2 = "World";
        singleShellMagics.shell(Collections.emptyList(), "echo " + var1 + ", " + var2 + "!");
        singleShellMagics.shell(Collections.emptyList(), "echo Testing " + var1 + " and " + var2);
    }

}