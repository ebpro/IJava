package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.ijava.IJava;
import io.github.spencerpark.ijava.JavaKernel;
import io.github.spencerpark.jupyter.kernel.display.DisplayData;
import io.github.spencerpark.jupyter.kernel.display.mime.MIMEType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.fail;

public class JavaDBMSMagicsTest {

    private JavaKernel kernel;
    private java.util.concurrent.atomic.AtomicReference<DisplayData> lastDisplayData = new java.util.concurrent.atomic.AtomicReference<>();
    private java.util.concurrent.atomic.AtomicReference<String> lastDisplayString = new java.util.concurrent.atomic.AtomicReference<>();
    private final List<DisplayData> displayedData = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        // Start an in-memory H2 database and populate some sample data
        System.setProperty("jdbc.url", "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");

        // Ensure driver is available and create schema
        Class.forName("org.h2.Driver");
        try (Connection conn = DriverManager.getConnection(System.getProperty("jdbc.url"))) {
            try (Statement st = conn.createStatement()) {
                st.execute("DROP TABLE IF EXISTS person");
                st.execute("CREATE TABLE person (id INT PRIMARY KEY, name VARCHAR(100))");
                st.execute("INSERT INTO person (id, name) VALUES (1, 'Alice')");
                st.execute("INSERT INTO person (id, name) VALUES (2, 'Bob')");
            }
        }

        // Create a JavaKernel instance subclass that captures displayed data
        kernel = new JavaKernel() {
            @Override
            public void display(DisplayData data) {
                if (data != null) {
                    displayedData.add(data);
                    lastDisplayData.set(data);
                    lastDisplayString.set(String.valueOf(data));
                }
                // do not call super.display to avoid I/O side effects
            }
        };

        Field kernelField = IJava.class.getDeclaredField("kernel");
        kernelField.setAccessible(true);
        kernelField.set(null, kernel);
    }

    @After
    public void tearDown() throws Exception {
        // Clear kernel reference and system properties
        Field kernelField = IJava.class.getDeclaredField("kernel");
        kernelField.setAccessible(true);
        kernelField.set(null, null);

        lastDisplayData.set(null);
        lastDisplayString.set(null);
        displayedData.clear();

        System.clearProperty("jdbc.url");
        System.clearProperty("jdbc.user");
        System.clearProperty("jdbc.password");
    }

    @Test
    public void testSqlAsTableRunsWithoutException() {
        JavaDBMSMagics magics = new JavaDBMSMagics();
        try {
            magics.sqlAsTable(Collections.emptyList(), "SELECT id, name FROM person ORDER BY id");
        } catch (Exception e) {
            fail("sqlAsTable threw: " + e.getMessage());
            return;
        }

        // Verify the captured display contains the table rows
        String rendered = extractRendered(lastDisplayData.get());
        if (rendered == null) {
            fail("No display output captured for sqlAsTable");
            return;
        }
        // Expect HTML table or CSV containing Alice and Bob
        if (!(rendered.contains("Alice") && rendered.contains("Bob") || rendered.contains("<table")
                || rendered.contains("text/csv"))) {
            fail("Unexpected display output for sqlAsTable: " + rendered);
        }
    }

    @Test
    public void testRdbmsSchemaSourceOnlyRunsWithoutException() {
        JavaDBMSMagics magics = new JavaDBMSMagics();
        try {
            magics.rdbmsSchema(Collections.singletonList("sourceOnly"), "");
        } catch (Exception e) {
            fail("rdbmsSchema threw: " + e.getMessage());
            return;
        }

        List<String> renderedOutputs = new ArrayList<>();
        for (DisplayData dd : displayedData) {
            String rendered = extractRendered(dd);
            if (rendered != null) {
                renderedOutputs.add(rendered);
            }
        }
        if (renderedOutputs.isEmpty()) {
            fail("No display output captured for rdbmsSchema");
            return;
        }
        // `showSource` displays the PlantUML source and then the magic may also render/display
        // SVG/PNG output. The assertion must therefore inspect every displayed output, not only
        // the final rendered image.
        boolean hasPlantUmlSource = renderedOutputs.stream()
                .anyMatch(rendered -> rendered.contains("@startuml") || rendered.contains("```plantuml"));
        boolean hasTableMarker = renderedOutputs.stream()
                .anyMatch(rendered -> rendered.contains("table(") || rendered.contains("PERSON")
                        || rendered.contains("person"));
        if (!hasPlantUmlSource || !hasTableMarker) {
            fail("Unexpected display outputs for rdbmsSchema: " + renderedOutputs);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractRendered(DisplayData dd) {
        if (dd == null)
            return null;
        try {
            // Prefer public API: try common MIME types in order of preference
            Object v;
            v = dd.getData(MIMEType.TEXT_HTML);
            if (v != null)
                return String.valueOf(v);
            v = dd.getData(MIMEType.TEXT_MARKDOWN);
            if (v != null)
                return String.valueOf(v);
            v = dd.getData(MIMEType.TEXT_PLAIN);
            if (v != null)
                return String.valueOf(v);
            v = dd.getData(MIMEType.IMAGE_SVG);
            if (v != null)
                return String.valueOf(v);
            // fallback: try any common keys by string
            v = dd.getData(MIMEType.APPLICATION_JSON);
            if (v != null)
                return String.valueOf(v);

            return dd.toString();
        } catch (Throwable t) {
            try {
                System.err.println("[TEST DEBUG] DisplayData class methods:");
                for (java.lang.reflect.Method m : dd.getClass().getMethods())
                    System.err.println("  m: " + m.getName() + " -> " + m.getReturnType());
                System.err.println("[TEST DEBUG] DisplayData class fields:");
                for (java.lang.reflect.Field f : dd.getClass().getDeclaredFields())
                    System.err.println("  f: " + f.getName() + " -> " + f.getType());
            } catch (Throwable ignored) {
            }
            return dd.toString();
        }
    }
}
