package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.core.DiagramDescription;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
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

            StringBuilder out = new StringBuilder();
            out.append("@startuml\n");
            out.append("left to right direction\n");
            out.append("skinparam roundcorner 5\n");
            out.append("skinparam shadowing true\n");
            out.append("skinparam handwritten false\n");
            out.append("skinparam class { BackgroundColor #EEEEEE ArrowColor #2688d4 BorderColor #2688d4 }\n");
            out.append("!define primary_key(x) <b><color:#b8861b><&key></color> x</b>\n");
            out.append("!define foreign_key(x) <color:#aaaaaa><&key></color> x\n");
            out.append("!define column(x) <color:#efefef><&media-record></color> x\n");
            out.append("!define table(x) entity x << (T, white) >>\n\n");

            // iterate tables (if body contains specific table names, honor them)
            java.util.List<String> tableNames = new java.util.ArrayList<>();
            if (body != null && !body.trim().isEmpty()) {
                for (String line : body.split("\n")) {
                    String l = line.trim();
                    if (!l.isEmpty()) tableNames.add(l);
                }
            }

            if (tableNames.isEmpty()) {
                try (ResultSet tables = md.getTables(null, schema, "%", new String[]{"TABLE"})) {
                    while (tables.next()) tableNames.add(tables.getString("TABLE_NAME"));
                }
            }

            StringBuilder fkBuilder = new StringBuilder();

            for (String tableName : tableNames) {
                Table table = new Table(tableName);

                // columns
                try (ResultSet columns = md.getColumns(null, schema, tableName, null)) {
                    while (columns.next()) {
                        String columnName = columns.getString("COLUMN_NAME");
                        table.getFields().put(columnName,
                                Field.of(columnName,
                                        columns.getString("COLUMN_SIZE"),
                                        columns.getString("TYPE_NAME"),
                                        columns.getString("IS_NULLABLE").equalsIgnoreCase("YES"),
                                        "YES".equalsIgnoreCase(columns.getString("IS_AUTOINCREMENT"))));
                    }
                }

                // primary keys
                try (ResultSet primaryKeys = md.getPrimaryKeys(null, schema, tableName)) {
                    while (primaryKeys.next()) {
                        String pkCol = primaryKeys.getString("COLUMN_NAME");
                        if (table.getFields().containsKey(pkCol)) table.getFields().get(pkCol).setRole(Field.Role.PK);
                    }
                }

                // foreign keys
                try (ResultSet foreignKeys = md.getImportedKeys(null, schema, tableName)) {
                    while (foreignKeys.next()) {
                        String pkTable = foreignKeys.getString("PKTABLE_NAME");
                        String fkTable = foreignKeys.getString("FKTABLE_NAME");
                        String pkCol = foreignKeys.getString("PKCOLUMN_NAME");
                        String fkCol = foreignKeys.getString("FKCOLUMN_NAME");
                        if (table.getFields().containsKey(fkCol)) table.getFields().get(fkCol).setRole(Field.Role.FK);
                        fkBuilder.append(String.format("%s::%s --> %s::%s\n", fkTable, fkCol, pkTable, pkCol));
                    }
                }

                out.append(table.toString());
            }

            out.append(fkBuilder.toString());
            out.append("@enduml");

            // render via PlantUML as SVG
            SourceStringReader reader = new SourceStringReader(out.toString());
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            DiagramDescription desc = reader.outputImage(os, new FileFormatOption(FileFormat.SVG));
            os.close();
            String svg = new String(os.toByteArray(), Charset.forName("UTF-8"));
            display(svg, "image/svg+xml");

        } catch (Exception e) {
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
            // Attempt to ensure a JDBC driver is loaded. Users can set `jdbc.driver` system property
            // to force a specific driver class, or we try a few common drivers (H2, Postgres, MySQL, HSQLDB, SQLite).
            String driverProp = System.getProperty("jdbc.driver");
            if (driverProp != null && !driverProp.isBlank()) {
                try {
                    Class.forName(driverProp);
                } catch (ClassNotFoundException ignored) {
                }
            } else {
                String[] commonDrivers = new String[]{
                        "org.h2.Driver",
                        "org.postgresql.Driver",
                        "com.mysql.cj.jdbc.Driver",
                        "org.hsqldb.jdbc.JDBCDriver",
                        "org.sqlite.JDBC"
                };
                for (String d : commonDrivers) {
                    try {
                        Class.forName(d);
                    } catch (ClassNotFoundException ignored) {
                    }
                }
            }

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
        } catch (ReflectiveOperationException ignored) {
        }

        return null;
    }

}
