package io.github.spencerpark.ijava.magics;

import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.Assert.*;

/**
 * Regression tests for case-tolerant metadata filter resolution
 * (H2 v2 stores unquoted identifiers UPPERCASE; the metadata filters
 * used to be passed verbatim, silently yielding empty metadata).
 */
public class DBMetadataInspectorCaseTest {

    private static Connection h2WithEmployees() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:h2:mem:casetest" + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1");
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE employees (id INT PRIMARY KEY, name VARCHAR(64))");
            st.execute("INSERT INTO employees VALUES (1, 'alice')");
        }
        return conn;
    }

    @Test
    public void lowercaseFilterResolvesToUpperStoredTable() throws Exception {
        try (Connection conn = h2WithEmployees()) {
            DBMetadataInspector.TableMetadata meta = DBMetadataInspector.inspect(conn, null, "employees");
            assertEquals("wrong-case filter must fall back to the UPPER-stored table",
                    2, meta.columns.size());
            assertFalse(meta.primaryKeys.isEmpty());
            // display name stays as given
            assertEquals("employees", meta.table);
        }
    }

    @Test
    public void asGivenMatchWinsAndIsUnchanged() throws Exception {
        try (Connection conn = h2WithEmployees()) {
            DBMetadataInspector.TableMetadata meta = DBMetadataInspector.inspect(conn, null, "EMPLOYEES");
            assertEquals(2, meta.columns.size());
            assertEquals("ID", meta.columns.get(0).name); // ordinal order preserved
            assertEquals("EMPLOYEES", meta.table);
        }
    }

    @Test
    public void missingTableStaysEmpty() throws Exception {
        try (Connection conn = h2WithEmployees()) {
            DBMetadataInspector.TableMetadata meta = DBMetadataInspector.inspect(conn, null, "nosuchtable");
            assertTrue(meta.columns.isEmpty());
        }
    }
}
