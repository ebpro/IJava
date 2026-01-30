package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.ijava.runtime.Display;
import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import io.github.spencerpark.jupyter.kernel.magic.registry.LineMagic;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Magic to display detailed table schema metadata in Jupyter (IJava).
 *
 * Usage:
 * %tableSchema [schema.]table [--ddl] [--sample=N] [--compact]
 * %%tableSchema same options, multiple tables in cell body
 */
public class TableSchemaMagics {

    // --------------------- ARG PARSING ---------------------
    private static class SchemaArgs {
        String tableArg;
        boolean ddl = false;
        Integer sampleRows = null;
        boolean compact = false;
        boolean help = false;
    }

    private SchemaArgs parseArgs(List<String> args) {
        SchemaArgs sa = new SchemaArgs();
        if (args == null)
            return sa;

        for (String a : args) {
            if (a == null)
                continue;
            switch (a) {
                case "--ddl":
                    sa.ddl = true;
                    break;
                case "--compact":
                    sa.compact = true;
                    break;
                case "--help":
                case "-h":
                    sa.help = true;
                    break;
                default:
                    if (a.startsWith("--sample=")) {
                        try {
                            sa.sampleRows = Integer.parseInt(a.substring("--sample=".length()));
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (sa.tableArg == null) {
                        sa.tableArg = a;
                    }
                    break;
            }
        }
        return sa;
    }

    // --------------------- LINE MAGIC ---------------------
    @LineMagic("tableSchema")
    public void tableSchema(List<String> args) {
        SchemaArgs sa = parseArgs(args);
        if (sa.help) {
            printHelp(false);
            return;
        }
        renderTableSchema(sa.tableArg, sa.ddl, sa.sampleRows, sa.compact);
    }

    // --------------------- CELL MAGIC ---------------------
    @CellMagic("tableSchema")
    public void tableSchemaCell(List<String> args, String body) {
        SchemaArgs sa = parseArgs(args);
        if (sa.help) {
            printHelp(true);
            return;
        }
        if (body != null && !body.isBlank()) {
            for (String line : body.split("\\r?\\n")) {
                String tbl = line.strip();
                if (!tbl.isEmpty())
                    renderTableSchema(tbl, sa.ddl, sa.sampleRows, sa.compact);
            }
        } else {
            renderTableSchema(sa.tableArg, sa.ddl, sa.sampleRows, sa.compact);
        }
    }

    private void printHelp(boolean cell) {
        String prefix = cell ? "%%tableSchema" : "%tableSchema";
        System.out.println(prefix + " - Show detailed table schema metadata\n\n" +
                "Usage: " + prefix + " [schema.]table [--ddl] [--sample=N] [--compact] [--help]\n\n" +
                "Options:\n" +
                "  --ddl         Show a minimal CREATE TABLE DDL snippet\n" +
                "  --sample=N    Show up to N sample rows\n" +
                "  --compact     Show compact table summary in relational notation\n" +
                "  --help, -h    Show this help message");
    }

    // --------------------- MAIN RENDER LOGIC ---------------------
    private void renderTableSchema(String tableArg, boolean ddl, Integer sampleRows, boolean compact) {
        if (tableArg == null || tableArg.isBlank()) {
            System.out.println("Usage: %tableSchema [schema.]table [--ddl] [--sample=N] [--compact]");
            return;
        }

        // Split schema.table
        String schema = null;
        String table = tableArg;
        if (tableArg.contains(".")) {
            int idx = tableArg.indexOf('.');
            schema = tableArg.substring(0, idx);
            table = tableArg.substring(idx + 1);
        }

        // Security: only allow alphanumeric + underscore for table
        if (!table.matches("[\\w]+")) {
            Display.display("Invalid table name: " + tableArg, "text/plain");
            return;
        }

        try (Connection conn = obtainConnection()) {
            if (conn == null) {
                Display.display("No JDBC connection available.", "text/plain");
                return;
            }

            // Use DBMetadataInspector to collect a normalized view of the table
            DBMetadataInspector.TableMetadata meta = DBMetadataInspector.inspect(conn, schema, table);

            // --------------------- COLUMNS ---------------------
            List<ColumnInfo> columns = new ArrayList<>();
            for (DBMetadataInspector.ColumnMeta cm : meta.columns) {
                columns.add(new ColumnInfo(cm.name, cm.type, cm.size, cm.nullable, cm.defaultValue, cm.remarks));
            }
            if (columns.isEmpty()) {
                Display.display("Table not found or has no columns: " + tableArg, "text/plain");
                return;
            }

            // --------------------- PRIMARY KEYS ---------------------
            List<String> primaryKeys = new ArrayList<>(meta.primaryKeys);

            // --------------------- INDEXES ---------------------
            Map<String, IndexInfo> indexes = new HashMap<>();
            for (DBMetadataInspector.IndexMeta im : meta.indexes.values()) {
                IndexInfo ii = new IndexInfo(im.name, im.unique);
                ii.columns.addAll(im.columns);
                indexes.put(ii.name, ii);
            }

            // Determine single-column unique indexes for inline marking
            Set<String> uniqueColumns = new HashSet<>();
            for (IndexInfo ii : indexes.values()) {
                if (ii.unique && ii.columns.size() == 1) {
                    uniqueColumns.add(ii.columns.get(0));
                }
            }

            // --------------------- FOREIGN KEYS ---------------------
            Map<String, String> fkMap = new HashMap<>();
            for (DBMetadataInspector.FKMeta f : meta.foreignKeys) {
                fkMap.put(f.fkColumn, f.pkTable + "(" + f.pkColumn + ")");
            }

            // Normalize name sets/maps for case-insensitive matching
            Set<String> pkSetNorm = primaryKeys.stream().map(s -> s == null ? null : s.toUpperCase(Locale.ROOT)).filter(Objects::nonNull).collect(Collectors.toSet());
            Set<String> uniqueColumnsNorm = uniqueColumns.stream().map(s -> s == null ? null : s.toUpperCase(Locale.ROOT)).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<String, String> fkMapNorm = new HashMap<>();
            for (Map.Entry<String, String> e : fkMap.entrySet()) {
                if (e.getKey() != null)
                    fkMapNorm.put(e.getKey().toUpperCase(Locale.ROOT), e.getValue());
            }

            // --------------------- OUTPUT ---------------------
            StringBuilder out = new StringBuilder();
            out.append("# Table ``").append(tableArg).append("``\n\n");

            if (compact) {
                // Construct relational notation
                List<String> columnTokens = new ArrayList<>();
                for (ColumnInfo c : columns) {
                        StringBuilder token = new StringBuilder(c.name);
                        if (pkSetNorm.contains(c.name == null ? null : c.name.toUpperCase(Locale.ROOT)))
                            token.append(" (PK)");
                        if (uniqueColumnsNorm.contains(c.name == null ? null : c.name.toUpperCase(Locale.ROOT)))
                            token.append(" (UNIQUE)");
                        if (fkMapNorm.containsKey(c.name == null ? null : c.name.toUpperCase(Locale.ROOT)))
                            token.append(" → ").append(fkMapNorm.get(c.name.toUpperCase(Locale.ROOT)));
                        columnTokens.add(token.toString());
                }
                out.append(table.toUpperCase()).append("(")
                        .append(String.join(", ", columnTokens))
                        .append(")\n\n");
            } else {
                // Markdown table
                out.append("| Column | Type | Nullable | Default | Remarks |\n");
                out.append("|---|---|:---:|---|---|\n");
                for (ColumnInfo c : columns) {
                    String annotatedName = c.name;
                    if (pkSetNorm.contains(c.name == null ? null : c.name.toUpperCase(Locale.ROOT)))
                        annotatedName += " (PK)";
                    if (uniqueColumnsNorm.contains(c.name == null ? null : c.name.toUpperCase(Locale.ROOT)))
                        annotatedName += " (UNIQUE)";
                    if (fkMapNorm.containsKey(c.name == null ? null : c.name.toUpperCase(Locale.ROOT)))
                        annotatedName += " → " + fkMapNorm.get(c.name.toUpperCase(Locale.ROOT));

                    out.append(String.format("| %s | %s%s | %s | %s | %s |\n",
                            annotatedName,
                            c.type, c.size > 0 ? "(" + c.size + ")" : "",
                            c.nullable ? "" : "NOT NULL",
                            c.defaultValue == null ? "" : c.defaultValue,
                            c.remarks == null ? "" : c.remarks));
                }
                out.append("\n");

                if (!primaryKeys.isEmpty())
                    out.append("**Primary key**: ").append(String.join(", ", primaryKeys)).append("\n\n");

                if (!indexes.isEmpty()) {
                    out.append("**Indexes**:\n");
                    for (IndexInfo i : indexes.values()) {
                        out.append("- ").append(i.name)
                                .append(" (").append(String.join(", ", i.columns)).append(") ")
                                .append(i.unique ? "UNIQUE" : "")
                                .append("\n");
                    }
                    out.append("\n");
                }

                if (!fkMap.isEmpty()) {
                    out.append("**Foreign keys**:\n");
                    fkMap.forEach(
                            (fkCol, ref) -> out.append("- ").append(fkCol).append(" → ").append(ref).append("\n"));
                    out.append("\n");
                }
            }

            // --------------------- DDL ---------------------
            if (ddl) {
                StringBuilder ddlText = new StringBuilder();
                ddlText.append("CREATE TABLE ").append(tableArg).append(" (\n");
                ddlText.append(columns.stream()
                        .map(c -> "    " + c.name + " " + c.type + (c.size > 0 ? "(" + c.size + ")" : "")
                                + (c.nullable ? "" : " NOT NULL")
                                + (c.defaultValue != null ? " DEFAULT " + c.defaultValue : ""))
                        .collect(Collectors.joining(",\n")));
                if (!primaryKeys.isEmpty()) {
                    ddlText.append(",\n    PRIMARY KEY (").append(String.join(", ", primaryKeys)).append(")");
                }
                ddlText.append("\n);");
                out.append("```\n").append(ddlText).append("\n```\n");
            }

            Display.display(out.toString(), "text/markdown");

            // --------------------- SAMPLE ROWS ---------------------
            if (sampleRows != null && sampleRows > 0) {
                try (Statement s = conn.createStatement()) {
                    String sql = "SELECT * FROM " + tableArg + " LIMIT " + sampleRows;
                    try (ResultSet rs = s.executeQuery(sql)) {
                        StringBuilder tbl = new StringBuilder();
                        ResultSetMetaData rm = rs.getMetaData();
                        int nc = rm.getColumnCount();

                        // header
                        tbl.append("|");
                        for (int i = 1; i <= nc; i++)
                            tbl.append(" ").append(rm.getColumnName(i)).append(" |");
                        tbl.append("\n|");
                        for (int i = 1; i <= nc; i++)
                            tbl.append(" --- |");
                        tbl.append("\n");

                        while (rs.next()) {
                            tbl.append("|");
                            for (int i = 1; i <= nc; i++) {
                                Object v = rs.getObject(i);
                                tbl.append(" ").append(v == null ? "NULL" : v.toString()).append(" |");
                            }
                            tbl.append("\n");
                        }
                        Display.display(tbl.toString(), "text/markdown");
                    }
                } catch (Throwable t) {
                    Display.display("Failed to sample rows: " + t.getMessage(), "text/plain");
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // --------------------- HELPERS ---------------------
    private static class ColumnInfo {
        String name, type, defaultValue, remarks;
        boolean nullable;
        int size;

        ColumnInfo(String name, String type, int size, boolean nullable, String defaultValue, String remarks) {
            this.name = name;
            this.type = type;
            this.size = size;
            this.nullable = nullable;
            this.defaultValue = defaultValue;
            this.remarks = remarks;
        }
    }

    private static class IndexInfo {
        String name;
        boolean unique;
        List<String> columns = new ArrayList<>();

        IndexInfo(String name, boolean unique) {
            this.name = name;
            this.unique = unique;
        }
    }

    // --------------------- JDBC CONNECTION ---------------------
    private Connection obtainConnection() throws SQLException {
        String url = System.getProperty("jdbc.url");
        if (url != null && !url.isBlank()) {
            String user = System.getProperty("jdbc.user");
            String pass = System.getProperty("jdbc.password");
            if (user != null)
                return DriverManager.getConnection(url, user, pass == null ? "" : pass);
            return DriverManager.getConnection(url);
        }
        try {
            Class<?> dm = Class.forName("DatabaseManager");
            try {
                java.lang.reflect.Method m = dm.getMethod("getConnection");
                Object conn = m.invoke(null);
                if (conn instanceof Connection)
                    return (Connection) conn;
            } catch (NoSuchMethodException ignored) {
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }
}
