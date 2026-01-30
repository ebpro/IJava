package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.core.DiagramDescription;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
            String t = type == null ? "" : type;
            String s = size == null ? "" : size;
            String sizePart = (s == null || s.isEmpty()) ? "" : "(" + s + ")";
            switch (this.role) {
                case PK:
                    return String.format("primary_key(%s) : %s%s", quoteIdentifier(name), t, sizePart);
                case FK:
                    return String.format("foreign_key(%s) : %s%s", quoteIdentifier(name), t, sizePart);
                default:
                    return String.format("column(%s) : %s%s", quoteIdentifier(name), t, sizePart);
            }
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
            this.name = sanitizeTableName(tableName);
        }

        public Map<String, Field> getFields() {
            return fields;
        }

        public String toString() {
            return "table(" + quoteIdentifier(name) + ") {\n" +
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

    private static String sanitizeTableName(String name) {
        if (name == null)
            return "UNKNOWN";
        String t = name.trim();
        if (t.isEmpty())
            return "UNKNOWN";
        if (t.startsWith("//"))
            t = t.substring(2).trim();
        if (t.startsWith("#"))
            t = t.substring(1).trim();
        if (t.startsWith("--"))
            t = t.substring(2).trim();
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
            t = t.substring(1, t.length() - 1).trim();
        }
        if (t.isEmpty())
            return "UNKNOWN";
        return t;
    }

    private static String quoteIdentifier(String s) {
        if (s == null)
            return "UNKNOWN";
        String t = s.trim();
        if (t.matches("[A-Za-z0-9_]+"))
            return t;
        String esc = t.replace("\"", "\\\"");
        return "\"" + esc + "\"";
    }

    /**
     * Cell magic to print a schema overview for a given schema name.
     * Usage: applyCellMagic("rdbmsSchema", List.of("schema_name"), "%")
     */
    @CellMagic("rdbmsSchema")
    public void rdbmsSchema(java.util.List<String> args, String body) {
        if (args != null) {
            for (String a : args) {
                if (a != null && (a.equals("--help") || a.equals("-h"))) {
                    System.out.println("## %%rdbmsSchema - Render DB schema as PlantUML\n\n" +
                            "Usage: %%rdbmsSchema [--help] [<schema>] [SVG|PNG] [--show-source] [include=<regex>] [exclude=<regex>]\n\n" +
                            "Provide table names in the cell body (one per line) to limit the diagram.");
                    return;
                }
            }
        }
        // args may contain: [<schema>] [SVG|PNG] [showSource|-s] [handwritten]
        // [include=<regex>] [exclude=<regex>] [scale=<n>]
        String schema = null;
        boolean showSource = false;
        String fileFormat = "SVG";
        boolean handwritten = false;
        String includeRegex = null;
        String excludeRegex = null;
        String scale = null;
        for (String a : args) {
            if (a == null)
                continue;
            String aa = a.trim();
            if (aa.equalsIgnoreCase("SVG") || aa.equalsIgnoreCase("PNG")) {
                fileFormat = aa.toUpperCase();
                continue;
            }
            if (aa.equalsIgnoreCase("showSource") || aa.equalsIgnoreCase("show-source") || aa.equals("--show-source")
                    || aa.equals("-s") || aa.equalsIgnoreCase("source")) {
                showSource = true;
                continue;
            }
            if (aa.equalsIgnoreCase("handwritten") || aa.equalsIgnoreCase("--handwritten")
                    || aa.equalsIgnoreCase("handwritten:true")) {
                handwritten = true;
                continue;
            }
            if (aa.startsWith("include=")) {
                includeRegex = aa.substring("include=".length());
                continue;
            }
            if (aa.startsWith("exclude=")) {
                excludeRegex = aa.substring("exclude=".length());
                continue;
            }
            if (aa.startsWith("scale=")) {
                scale = aa.substring("scale=".length());
                continue;
            }
            if (schema == null)
                schema = aa;
        }

        try (Connection conn = obtainConnection()) {
            if (conn == null) {
                System.out.println(
                        "No JDBC connection available. Set system properties 'jdbc.url' (and optionally 'jdbc.user'/'jdbc.password'), or provide a Connection in the kernel environment.");
                return;
            }

            DatabaseMetaData md = conn.getMetaData();

            StringBuilder out = new StringBuilder();
            out.append("@startuml\n");
            out.append("left to right direction\n");
            out.append("skinparam roundcorner 5\n");
            out.append("skinparam shadowing true\n");
            // Handwritten mode is opt-in; default is not handwritten
            out.append("skinparam entity {\n");
            out.append("    BackgroundColor #EEEEEE\n");
            out.append("    ArrowColor #2688d4\n");
            out.append("    BorderColor #2688d4\n");
            out.append("}\n");
            // Avoid using PlantUML icon tokens (<&...>) which may trigger the 'handwritten'
            // option.
            // Use simple textual markers instead so diagrams render without requiring
            // '!option handwritten true'.
            out.append("!define primary_key(x) <b><color:#b8861b>PK</color> x</b>\n");
            out.append("!define foreign_key(x) <color:#aaaaaa>FK</color> x\n");
            out.append("!define column(x) <color:#efefef>*</color> x\n");
            out.append("!define table(x) entity x << (T, white) >>\n\n");
            if (handwritten)
                out.append("!option handwritten true\n");
            if (scale != null && !scale.isBlank())
                out.append("scale " + scale + "\n");

            // iterate tables (if body contains specific table names, honor them)
            java.util.List<String> tableNames = new java.util.ArrayList<>();
            if (body != null && !body.trim().isEmpty()) {
                for (String line : body.split("\n")) {
                    String l = line.trim();
                    if (l.isEmpty())
                        continue;
                    // ignore common comment markers so comments aren't treated as table names
                    if (l.startsWith("//") || l.startsWith("#") || l.startsWith("--"))
                        continue;
                    // skip SQL statements (SELECT/CREATE/INSERT/UPDATE/DELETE/etc.) if the user
                    // pasted SQL into the cell — rdbmsSchema expects table names, not queries.
                    if (l.matches("(?i)^(SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|TRUNCATE|WITH)\\b.*"))
                        continue;
                    // strip surrounding quotes and trailing semicolons
                    if ((l.startsWith("\"") && l.endsWith("\"")) || (l.startsWith("'") && l.endsWith("'"))) {
                        l = l.substring(1, l.length() - 1).trim();
                    }
                    if (l.endsWith(";"))
                        l = l.substring(0, l.length() - 1).trim();
                    if (!l.isEmpty())
                        tableNames.add(l);
                }
            }

            if (tableNames.isEmpty()) {
                try (ResultSet tables = md.getTables(null, schema, "%", new String[] { "TABLE" })) {
                    while (tables.next())
                        tableNames.add(tables.getString("TABLE_NAME"));
                }
            }

            // apply include/exclude filters if provided
            if (includeRegex != null || excludeRegex != null) {
                java.util.Iterator<String> it = tableNames.iterator();
                while (it.hasNext()) {
                    String tn = it.next();
                    if (includeRegex != null && !tn.matches(includeRegex)) {
                        it.remove();
                        continue;
                    }
                    if (excludeRegex != null && tn.matches(excludeRegex)) {
                        it.remove();
                    }
                }
            }

            StringBuilder fkBuilder = new StringBuilder();

            for (String tableName : tableNames) {
                Table table = new Table(tableName);

                // use inspector to collect metadata for this table
                DBMetadataInspector.TableMetadata meta = DBMetadataInspector.inspect(conn, schema, tableName);

                // populate fields
                for (DBMetadataInspector.ColumnMeta cm : meta.columns) {
                    Field f = Field.of(cm.name, cm.size > 0 ? String.valueOf(cm.size) : null, cm.type, cm.nullable, false);
                    table.getFields().put(cm.name, f);
                }

                // mark PKs
                for (String pk : meta.primaryKeys) {
                    if (table.getFields().containsKey(pk))
                        table.getFields().get(pk).setRole(Field.Role.PK);
                }

                // mark FKs and produce relationships
                for (DBMetadataInspector.FKMeta fk : meta.foreignKeys) {
                    String fkCol = fk.fkColumn;
                    String pkTable = fk.pkTable;
                    String pkCol = fk.pkColumn;
                    if (table.getFields().containsKey(fkCol))
                        table.getFields().get(fkCol).setRole(Field.Role.FK);

                    // multiplicities: estimate from nullable & PK membership
                    String fkMin = "0";
                    String fkMax = "*";
                    if (table.getFields().containsKey(fkCol)) {
                        Field fkField = table.getFields().get(fkCol);
                        fkMin = fkField.isNullable() ? "0" : "1";
                        if (fkField.getRole() == Field.Role.PK)
                            fkMax = "1";
                    }
                    String pkMultiplicity = "1";
                    String fkMultiplicity = fkMin + ".." + fkMax;

                    fkBuilder.append(String.format("%s \"%s\" --> \"%s\" %s : %s -> %s\n",
                            quoteIdentifier(tableName), fkMultiplicity, pkMultiplicity, quoteIdentifier(pkTable),
                            quoteIdentifier(fkCol), quoteIdentifier(pkCol)));
                }

                out.append(table.toString());
            }

            out.append(fkBuilder.toString());
            out.append("@enduml");

            // Optionally display the generated PlantUML source for debugging
            if (showSource) {
                display("```plantuml\n" + out.toString() + "\n```", "text/markdown");
            }

            // render via PlantUML with requested format
            SourceStringReader reader = new SourceStringReader(out.toString());
            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            DiagramDescription desc = reader.outputImage(os, new FileFormatOption(FileFormat.valueOf(fileFormat)));
            os.close();
            Object output;
            if (fileFormat.equals("SVG"))
                output = new String(os.toByteArray(), Charset.forName("UTF-8"));
            else
                output = ImageIO.read(new ByteArrayInputStream(os.toByteArray()));

            display(output, fileFormat.equals("SVG") ? "image/svg+xml" : "image/png");

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
        if (sql.isEmpty())
            return;

        if (args != null) {
            for (String a : args) {
                if (a != null && (a.equals("--help") || a.equals("-h"))) {
                    System.out.println("## %%sqlAsTable - Run SQL and render first SELECT result as table\n\n" +
                            "Usage: %%sqlAsTable [--help] [format=HTML|CSV] [max=<n>] [showQuery]\n\n" +
                            "The cell body may contain DDL/DML statements followed by a SELECT; the first SELECT is rendered.");
                    return;
                }
            }
        }

        // parse args: format=HTML|CSV, max=<n>, showQuery
        String format = "HTML";
        int maxRows = 1000;
        boolean showQuery = false;
        for (String a : args) {
            if (a == null)
                continue;
            String aa = a.trim();
            if (aa.equalsIgnoreCase("CSV") || aa.equalsIgnoreCase("HTML")) {
                format = aa.toUpperCase();
                continue;
            }
            if (aa.startsWith("format=")) {
                format = aa.substring("format=".length()).toUpperCase();
                continue;
            }
            if (aa.startsWith("max=")) {
                try {
                    maxRows = Integer.parseInt(aa.substring("max=".length()));
                } catch (NumberFormatException ignored) {
                }
                continue;
            }
            if (aa.equalsIgnoreCase("showQuery") || aa.equalsIgnoreCase("--show-query")) {
                showQuery = true;
                continue;
            }
        }

        try (Connection conn = obtainConnection()) {
            if (conn == null) {
                System.out.println(
                        "No JDBC connection available. Set system properties 'jdbc.url' (and optionally 'jdbc.user'/'jdbc.password'), or provide a Connection in the kernel environment.");
                return;
            }

            // Normalize SQL for databases like H2 which expect LIMIT before OFFSET.
            String normalizedSql = sql;
            Pattern p = Pattern.compile("(?i)\\bOFFSET\\s+(\\d+)\\s+LIMIT\\s+(\\d+)");
            Matcher m = p.matcher(normalizedSql);
            if (m.find()) {
                normalizedSql = m.replaceAll("LIMIT $2 OFFSET $1");
                display("Note: rewrote SQL 'OFFSET ... LIMIT' to 'LIMIT ... OFFSET' for compatibility",
                        "text/markdown");
            }

            try (Statement st = conn.createStatement()) {
                // Split the provided SQL into individual statements. The magic allows a
                // cell to contain DDL/DML statements followed by a final SELECT whose
                // results are rendered as a table. We therefore execute non-query
                // statements first (using executeUpdate) and then execute the first
                // SELECT we encounter with executeQuery to render its ResultSet.
                java.util.List<String> statements = new java.util.ArrayList<>();
                StringBuilder cur = new StringBuilder();
                for (String line : normalizedSql.split("\n")) {
                    String t = line.trim();
                    if (t.isEmpty())
                        continue;
                    if (t.startsWith("--") || t.startsWith("//") || t.startsWith("#"))
                        continue;
                    // Accumulate lines. If a semicolon terminates the statement, split.
                    cur.append(line).append('\n');
                    if (t.endsWith(";")) {
                        String stmt = cur.toString().trim();
                        // strip trailing semicolon
                        if (stmt.endsWith(";") )
                            stmt = stmt.substring(0, stmt.length() - 1).trim();
                        if (!stmt.isEmpty())
                            statements.add(stmt);
                        cur.setLength(0);
                    }
                }
                if (cur.length() > 0) {
                    String stmt = cur.toString().trim();
                    if (!stmt.isEmpty())
                        statements.add(stmt);
                }

                // If we found no statements via semicolons, try a simple heuristic:
                // split on blank-line or detect statement-starting keywords on new lines.
                if (statements.isEmpty()) {
                    statements = new java.util.ArrayList<>();
                    cur.setLength(0);
                    for (String line : normalizedSql.split("\n")) {
                        String t = line.trim();
                        if (t.isEmpty()) {
                            if (cur.length() > 0) {
                                statements.add(cur.toString().trim());
                                cur.setLength(0);
                            }
                            continue;
                        }
                        // if line looks like the start of a statement and we have accumulated content,
                        // treat it as a new statement boundary
                        if (cur.length() > 0 && t.matches("(?i)^(CREATE|INSERT|UPDATE|DELETE|SELECT|ALTER|DROP|TRUNCATE|MERGE|REPLACE)\\b.*")) {
                            statements.add(cur.toString().trim());
                            cur.setLength(0);
                        }
                        cur.append(line).append('\n');
                    }
                    if (cur.length() > 0)
                        statements.add(cur.toString().trim());
                }

                ResultSet rs = null;
                ResultSetMetaData md = null;
                int cols = 0;

                boolean rendered = false;
                for (String stmt : statements) {
                    String sTrim = stmt.trim();
                    if (sTrim.isEmpty())
                        continue;
                    // If this is a SELECT (or starts with WITH), executeQuery and render
                    if (sTrim.matches("(?i)^(SELECT|WITH)\\b.*")) {
                        rs = st.executeQuery(sTrim);
                        md = rs.getMetaData();
                        cols = md.getColumnCount();

                        if (showQuery)
                            display("````sql\n" + sTrim + "\n````", "text/markdown");

                        // build CSV
                        if ("CSV".equalsIgnoreCase(format)) {
                            StringBuilder csv = new StringBuilder();
                            for (int i = 1; i <= cols; i++) {
                                if (i > 1)
                                    csv.append(',');
                                csv.append(escapeCsv(md.getColumnLabel(i)));
                            }
                            csv.append('\n');
                            int rowCount = 0;
                            while (rs.next() && rowCount < maxRows) {
                                rowCount++;
                                for (int i = 1; i <= cols; i++) {
                                    if (i > 1)
                                        csv.append(',');
                                    Object v = rs.getObject(i);
                                    csv.append(escapeCsv(v == null ? "" : v.toString()));
                                }
                                csv.append('\n');
                            }
                            if (rs.next())
                                csv.append("# TRUNCATED: more rows available\n");
                            display(csv.toString(), "text/csv");
                            rendered = true;
                            rs.close();
                            break;
                        }

                        // default: HTML
                        StringBuilder html = new StringBuilder();
                        html.append("<table border=1 style=\"border-collapse:collapse; width:100%;\">\n<thead><tr>");
                        for (int i = 1; i <= cols; i++)
                            html.append("<th style=\"text-align:left; padding:4px;\">")
                                    .append(escapeHtml(md.getColumnLabel(i))).append("</th>");
                        html.append("</tr></thead>\n<tbody>\n");
                        int rowCount = 0;
                        while (rs.next() && rowCount < maxRows) {
                            rowCount++;
                            html.append("<tr>");
                            for (int i = 1; i <= cols; i++) {
                                Object v = rs.getObject(i);
                                html.append("<td style=\"padding:4px;\">")
                                        .append(v == null ? "" : escapeHtml(v.toString())).append("</td>");
                            }
                            html.append("</tr>\n");
                        }
                        html.append("</tbody></table>");
                        if (rs.next())
                            html.append("<div style=\"color:gray;font-size:smaller;\">Results truncated (showing first "
                                    + maxRows + " rows)</div>");
                        display(html.toString(), "text/html");
                        rendered = true;
                        rs.close();
                        break;
                    } else {
                        // Non-query statement: use executeUpdate where appropriate, otherwise execute
                        try {
                            int count = st.executeUpdate(sTrim);
                            // optionally display the update count for DML statements
                            if (!sTrim.matches("(?i)^(CREATE|DROP|ALTER|TRUNCATE)\\b.*")) {
                                display("Updated " + count + " rows", "text/markdown");
                            }
                        } catch (SQLException ex) {
                            // fallback to execute() for statements that may not be supported by executeUpdate
                            boolean hasResultSet = st.execute(sTrim);
                            if (hasResultSet) {
                                rs = st.getResultSet();
                                md = rs.getMetaData();
                                cols = md.getColumnCount();
                                // render first result set as above (CSV/HTML)
                                if ("CSV".equalsIgnoreCase(format)) {
                                    StringBuilder csv = new StringBuilder();
                                    for (int i = 1; i <= cols; i++) {
                                        if (i > 1)
                                            csv.append(',');
                                        csv.append(escapeCsv(md.getColumnLabel(i)));
                                    }
                                    csv.append('\n');
                                    int rowCount = 0;
                                    while (rs.next() && rowCount < maxRows) {
                                        rowCount++;
                                        for (int i = 1; i <= cols; i++) {
                                            if (i > 1)
                                                csv.append(',');
                                            Object v = rs.getObject(i);
                                            csv.append(escapeCsv(v == null ? "" : v.toString()));
                                        }
                                        csv.append('\n');
                                    }
                                    if (rs.next())
                                        csv.append("# TRUNCATED: more rows available\n");
                                    display(csv.toString(), "text/csv");
                                    rendered = true;
                                    rs.close();
                                    break;
                                } else {
                                    StringBuilder html = new StringBuilder();
                                    html.append("<table border=1 style=\"border-collapse:collapse; width:100%;\">\n<thead><tr>");
                                    for (int i = 1; i <= cols; i++)
                                        html.append("<th style=\"text-align:left; padding:4px;\">")
                                                .append(escapeHtml(md.getColumnLabel(i))).append("</th>");
                                    html.append("</tr></thead>\n<tbody>\n");
                                    int rowCount = 0;
                                    while (rs.next() && rowCount < maxRows) {
                                        rowCount++;
                                        html.append("<tr>");
                                        for (int i = 1; i <= cols; i++) {
                                            Object v = rs.getObject(i);
                                            html.append("<td style=\"padding:4px;\">")
                                                    .append(v == null ? "" : escapeHtml(v.toString()))
                                                    .append("</td>");
                                        }
                                        html.append("</tr>\n");
                                    }
                                    html.append("</tbody></table>");
                                    if (rs.next())
                                        html.append("<div style=\"color:gray;font-size:smaller;\">Results truncated (showing first "
                                                + maxRows + " rows)</div>");
                                    display(html.toString(), "text/html");
                                    rendered = true;
                                    rs.close();
                                    break;
                                }
                            }
                        }
                    }
                }

                if (!rendered) {
                    // If nothing produced a result set, optionally inform the user.
                    display("Statements executed", "text/markdown");
                }
                return;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'",
                "&#39;");
    }

    private static String escapeCsv(String s) {
        String v = s.replace("\"", "\"\"");
        if (v.contains(",") || v.contains("\n") || v.contains("\r") || v.contains("\"")) {
            return "\"" + v + "\"";
        }
        return v;
    }

    /**
     * Attempt to obtain a JDBC Connection from several strategies:
     * 1) System properties `jdbc.url` (+ user/password)
     * 2) If a `DatabaseManager` class with `getConnection()` exists in kernel
     * scope, attempt to call it via reflection.
     */
    private Connection obtainConnection() throws SQLException {
        String url = System.getProperty("jdbc.url");
        if (url != null && !url.isBlank()) {
            // Ensure a driver will be attempted to be registered later if none are present.

            // Check if any registered driver already accepts this URL
            boolean accepts = false;
            for (Driver d : java.util.Collections.list(DriverManager.getDrivers())) {
                try {
                    if (d.acceptsURL(url)) {
                        accepts = true;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }

            if (!accepts) {
                // Attempt to load driver class from various classloaders and register a proxy
                // driver
                String driverProp = System.getProperty("jdbc.driver");
                String[] candidateDrivers;
                if (driverProp != null && !driverProp.isBlank())
                    candidateDrivers = new String[] { driverProp };
                else
                    candidateDrivers = new String[] { "org.h2.Driver", "org.postgresql.Driver",
                            "com.mysql.cj.jdbc.Driver", "org.hsqldb.jdbc.JDBCDriver", "org.sqlite.JDBC" };

                ClassLoader[] loaders = new ClassLoader[] {
                        Thread.currentThread().getContextClassLoader(),
                        ClassLoader.getSystemClassLoader(),
                        this.getClass().getClassLoader(),
                        io.github.spencerpark.ijava.IJava.class.getClassLoader()
                };

                for (String drv : candidateDrivers) {
                    for (ClassLoader loader : loaders) {
                        if (loader == null)
                            continue;
                        try {
                            Class<?> drvClass = Class.forName(drv, true, loader);
                            Object drvInstance = drvClass.getDeclaredConstructor().newInstance();

                            java.sql.Driver proxy = (java.sql.Driver) java.lang.reflect.Proxy.newProxyInstance(
                                    java.sql.Driver.class.getClassLoader(),
                                    new Class[] { java.sql.Driver.class },
                                    (proxyObj, method, args) -> method.invoke(drvInstance, args));

                            DriverManager.registerDriver(proxy);
                            // if it accepts the URL now, break out
                            if (proxy.acceptsURL(url)) {
                                accepts = true;
                                break;
                            }
                        } catch (ClassNotFoundException ignored) {
                        } catch (ReflectiveOperationException | java.sql.SQLException e) {
                            // continue to next loader/driver
                        }
                    }
                    if (accepts)
                        break;
                }
            }

            String user = System.getProperty("jdbc.user");
            String pass = System.getProperty("jdbc.password");
            if (user != null)
                return DriverManager.getConnection(url, user, pass == null ? "" : pass);
            return DriverManager.getConnection(url);
        }

        // Try reflection for DatabaseManager.getConnection()
        try {
            Class<?> dm = Class.forName("DatabaseManager");
            try {
                java.lang.reflect.Method m = dm.getMethod("getConnection");
                Object conn = m.invoke(null);
                if (conn instanceof Connection)
                    return (Connection) conn;
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
                        if (conn instanceof Connection)
                            return (Connection) conn;
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
