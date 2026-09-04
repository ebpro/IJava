package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.ijava.JavaKernel;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class PrinterMagicsTest {
    private boolean originalPrintWithVarName;

    @Before
    public void setUp() {
        originalPrintWithVarName = JavaKernel.printWithVarName;
    }

    @After
    public void tearDown() {
        JavaKernel.printWithVarName = originalPrintWithVarName;
    }

    @Test
    public void noArgsTogglesAndReportsState() {
        try (CapturedOutput output = new CapturedOutput()) {
            new PrinterMagics().printWithName(List.of());
            Assert.assertEquals(!originalPrintWithVarName, JavaKernel.printWithVarName);
            Assert.assertTrue(output.text().contains("printWithVarName=" + !originalPrintWithVarName));
            Assert.assertTrue(output.text().contains("-h for help."));
        }
    }

    @Test
    public void helpPrintsSwitchMessage() {
        try (CapturedOutput output = new CapturedOutput()) {
            new PrinterMagics().printWithName(List.of("--help"));
            Assert.assertEquals(originalPrintWithVarName, JavaKernel.printWithVarName);
            Assert.assertTrue(output.text().contains("run %printWithName to switch"));
        }
    }
}
