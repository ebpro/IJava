package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;

import java.sql.*;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static io.github.spencerpark.ijava.runtime.Display.display;

public class JavaDBMSMagics {

    private static class Field {
        private final String name;
        private final String size;
        private final String type;
        private final boolean nullable;
        private final boolean autoincrement;
        private Role role = Role.COLUMN;

        private Field(String name, String size, String type, boolean nullable, boolean autoincrement) {
            this.name = name;
            this.size = size;
            this.type = type;
            this.nullable = nullable;
            this.autoincrement = autoincrement;
        }

        public static Field of(String name, String size, String type, boolean nullable, boolean autoincrement) {
            return new Field(name, size, type, nullable, autoincrement);
        }

        public String toString() {
            return String.format("%s %s(%s): %s(%s)", nullable ? "" : "*", role.name, name, type, size);
        }

        public String getName() {
            return this.name;
        }

        public String getSize() {
            return this.size;
        }

        public String getType() {
            return this.type;
        }

        public boolean isNullable() {
            return this.nullable;
        }

        public boolean isAutoincrement() {
            return this.autoincrement;
        }

        public Role getRole() {
            return this.role;
        }

        public void setRole(Role role) {
            this.role = role;
        }

        public enum Role {
            COLUMN("column"),
            PK("primary_key"),
            FK("foreign_key");

            private final String name;

            Role(String name) {
                this.name = name;
            }

            public String getName() {
                return this.name;
            }
        }
    }

    private static class Table {

        private static int nextnum = 1;

        private String name;
        private int id = nextnum++;
        private Map<String, Field> fields = new TreeMap<>();

        public Table(String tableName) {
            this.name = tableName;
        }

        public Map<String, Field> getFields() {
            return fields;
        }

        public String toString() {
            return "table(" + name + ") {\n" +
                    this.getFields().values().stream().filter(f -> f.getRole() == Field.Role.PK).map(Object::toString)
                            .map(s -> "\t" + s).collect(Collectors.joining("\n"))
                    +
                    "\n--\n" +
                    this.getFields().values().stream().filter(f -> f.getRole() != Field.Role.PK).map(Object::toString)
                            .map(s -> "\t" + s).collect(Collectors.joining("\n"))
                    +
                    "\n}\n";
        }
    }

    /**
     * Cell magic to print a schema overview for a given schema name.
     * Usage: applyCellMagic("rdbmsSchema", List.of("schema_name"), "%")
     */
    @CellMagic("rdbmsSchema")
    public void rdbmsSchema(java.util.List<String> args, String body) {
        String schema = args.isEmpty() ? null : args.get(0);

        try (Connection conn = obtainConnection()) {
            if (conn == null) {
                System.out.println("No JDBC connection available. Set system properties 'jdbc.url' (and optionally 'jdbc.user'/'jdbc.password'), or provide a Connection in the kernel environment.");
                return;
            }

            DatabaseMetaData md = conn.getMetaData();
            ResultSet tables = md.getTables(null, schema, "%", new String[]{"TABLE"});
            StringBuilder sb = new StringBuilder();
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                sb.append("Table: ").append(tableName).append("\n");
                ResultSet cols = md.getColumns(null, schema, tableName, "%");
                while (cols.next()) {
                    String colName = cols.getString("COLUMN_NAME");
                    String type = cols.getString("TYPE_NAME");
                    String size = cols.getString("COLUMN_SIZE");
                    String nullable = cols.getInt("NULLABLE") == DatabaseMetaData.columnNullable ? "YES" : "NO";
                    sb.append(String.format("  %s %s(%s) nullable=%s\n", colName, type, size, nullable));
                }
                sb.append("\n");
            }
            display(sb.toString(), "text/plain");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Cell magic to execute a SQL query and render results as an HTML table.
     * Usage: applyCellMagic("sqlAsTable", List.of(), "SELECT ...")
     */
    @CellMagic("sqlAsTable")
    public void sqlAsTable(java.util.List<String> args, String body) {
        String sql = body == null ? "" : body.trim();
        if (sql.isEmpty()) return;

        try (Connection conn = obtainConnection()) {
            if (conn == null) {
                System.out.println("No JDBC connection available. Set system properties 'jdbc.url' (and optionally 'jdbc.user'/'jdbc.password'), or provide a Connection in the kernel environment.");
                return;
            }

            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                StringBuilder html = new StringBuilder();
                html.append("<table border=1 style=\"border-collapse:collapse;\">\n<tr>");
                for (int i = 1; i <= cols; i++) html.append("<th>").append(md.getColumnLabel(i)).append("</th>");
                html.append("</tr>\n");
                while (rs.next()) {
                    html.append("<tr>");
                    for (int i = 1; i <= cols; i++) {
                        Object v = rs.getObject(i);
                        html.append("<td>").append(v == null ? "" : escapeHtml(v.toString())).append("</td>");
                    }
                    html.append("</tr>\n");
                }
                html.append("</table>");
                display(html.toString(), "text/html");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    /**
     * Attempt to obtain a JDBC Connection from several strategies:
     * 1) System properties `jdbc.url` (+ user/password)
     * 2) If a `DatabaseManager` class with `getConnection()` exists in kernel scope, attempt to call it via reflection.
     */
    private Connection obtainConnection() throws SQLException {
        String url = System.getProperty("jdbc.url");
        if (url != null && !url.isBlank()) {
            String user = System.getProperty("jdbc.user");
            String pass = System.getProperty("jdbc.password");
            if (user != null) return DriverManager.getConnection(url, user, pass == null ? "" : pass);
            return DriverManager.getConnection(url);
        }

        // Try reflection for DatabaseManager.getConnection()
        try {
            Class<?> dm = Class.forName("DatabaseManager");
            try {
                java.lang.reflect.Method m = dm.getMethod("getConnection");
                Object conn = m.invoke(null);
                if (conn instanceof Connection) return (Connection) conn;
            } catch (NoSuchMethodException ignored) {
            }
            try {
                java.lang.reflect.Method m2 = dm.getMethod("getEntityManagerFactory");
                Object emf = m2.invoke(null);
                if (emf != null) {
                    // try to obtain a JDBC connection from the EMF
                    try {
                        java.lang.reflect.Method createEM = emf.getClass().getMethod("createEntityManager");
                        Object em = createEM.invoke(emf);
                        java.lang.reflect.Method getConn = em.getClass().getMethod("unwrap", Class.class);
                        Object conn = getConn.invoke(em, java.sql.Connection.class);
                        if (conn instanceof Connection) return (Connection) conn;
                    } catch (NoSuchMethodException ignored2) {
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }
        } catch (ClassNotFoundException | ReflectiveOperationException ignored) {
        }

        return null;
    }

}
