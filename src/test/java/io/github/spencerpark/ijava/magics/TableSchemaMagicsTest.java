package io.github.spencerpark.ijava.magics;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class TableSchemaMagicsTest {
    private CapturingKernels.CapturingKernel kernel;
    private String originalJdbcUrl;

    @Before
    public void setUp() throws Exception {
        originalJdbcUrl = System.getProperty("jdbc.url");
        System.clearProperty("jdbc.url");
        kernel = CapturingKernels.install();
        kernel.reset();
    }

    @After
    public void tearDown() throws Exception {
        CapturingKernels.uninstall();
        if (originalJdbcUrl == null)
            System.clearProperty("jdbc.url");
        else
            System.setProperty("jdbc.url", originalJdbcUrl);
    }

    @Test
    public void lineHelpPrintsUsage() {
        try (CapturedOutput output = new CapturedOutput()) {
            new TableSchemaMagics().tableSchema(List.of("--help"));
            Assert.assertTrue(output.text().contains("%tableSchema"));
            Assert.assertTrue(output.text().contains("Usage:"));
        }
    }

    @Test
    public void cellHelpPrintsUsage() {
        try (CapturedOutput output = new CapturedOutput()) {
            new TableSchemaMagics().tableSchemaCell(List.of("-h"), "");
            Assert.assertTrue(output.text().contains("%%tableSchema"));
            Assert.assertTrue(output.text().contains("Usage:"));
        }
    }

    @Test
    public void noTablePrintsUsage() {
        try (CapturedOutput output = new CapturedOutput()) {
            new TableSchemaMagics().tableSchema(List.of());
            Assert.assertTrue(output.text().contains("Usage: %tableSchema"));
        }
    }

    @Test
    public void invalidTableNameDisplaysError() {
        new TableSchemaMagics().tableSchema(List.of("bad-table;drop"));
        Assert.assertTrue(kernel.displayedTexts().stream().anyMatch(text -> text.contains("Invalid table name")));
    }

    @Test
    public void invalidSchemaNameDisplaysError() {
        new TableSchemaMagics().tableSchema(List.of("bad-schema.valid_table"));
        Assert.assertTrue(kernel.displayedTexts().stream().anyMatch(text -> text.contains("Invalid table name")));
    }

    @Test
    public void noConnectionDisplaysMessage() {
        new TableSchemaMagics().tableSchema(List.of("valid_table"));
        Assert.assertTrue(kernel.displayedTexts().stream().anyMatch(text -> text.contains("No JDBC connection available.")));
    }
}
