package io.github.spencerpark.ijava.magics;

import java.sql.*;
import java.util.*;

/**
 * Best-effort inspector that reads JDBC metadata and returns a normalized
 * TableMetadata object usable by magics.
 */
public class DBMetadataInspector {
    public static class ColumnMeta {
        public final String name;
        public final String type;
        public final int size;
        public final boolean nullable;
        public final String defaultValue;
        public final String remarks;
        public ColumnMeta(String name, String type, int size, boolean nullable, String defaultValue, String remarks) {
            this.name = name; this.type = type; this.size = size; this.nullable = nullable; this.defaultValue = defaultValue; this.remarks = remarks;
        }
    }

    public static class IndexMeta {
        public final String name;
        public final boolean unique;
        public final List<String> columns = new ArrayList<>();
        public IndexMeta(String name, boolean unique) { this.name = name; this.unique = unique; }
    }

    public static class FKMeta {
        public final String fkColumn;
        public final String pkTable;
        public final String pkColumn;
        public FKMeta(String fkColumn, String pkTable, String pkColumn) { this.fkColumn = fkColumn; this.pkTable = pkTable; this.pkColumn = pkColumn; }
    }

    public static class TableMetadata {
        public final String schema;
        public final String table;
        public final List<ColumnMeta> columns = new ArrayList<>();
        public final List<String> primaryKeys = new ArrayList<>();
        public final Map<String, IndexMeta> indexes = new LinkedHashMap<>();
        public final List<FKMeta> foreignKeys = new ArrayList<>();
        public final List<String> constraints = new ArrayList<>();
        public final List<String> checks = new ArrayList<>();
        public final List<String> domains = new ArrayList<>();
        public String tableRemarks = null;

        public TableMetadata(String schema, String table) { this.schema = schema; this.table = table; }
    }

    public static TableMetadata inspect(Connection conn, String schema, String table) throws SQLException {
        TableMetadata meta = new TableMetadata(schema, table);
        DatabaseMetaData md = conn.getMetaData();

        // columns
        try (ResultSet cols = md.getColumns(null, schema, table, null)) {
            while (cols.next()) {
                String name = cols.getString("COLUMN_NAME");
                String type = cols.getString("TYPE_NAME");
                int size = 0;
                try { size = cols.getInt("COLUMN_SIZE"); } catch (Exception ignored) {}
                boolean nullable = "YES".equalsIgnoreCase(cols.getString("IS_NULLABLE"));
                String def = cols.getString("COLUMN_DEF");
                String remarks = cols.getString("REMARKS");
                meta.columns.add(new ColumnMeta(name, type, size, nullable, def, remarks));
            }
        }

        // primary keys
        try (ResultSet pk = md.getPrimaryKeys(null, schema, table)) {
            while (pk.next()) meta.primaryKeys.add(pk.getString("COLUMN_NAME"));
        }

        // indexes
        try (ResultSet ix = md.getIndexInfo(null, schema, table, false, false)) {
            while (ix.next()) {
                String iname = ix.getString("INDEX_NAME");
                String col = ix.getString("COLUMN_NAME");
                boolean nonUnique = ix.getBoolean("NON_UNIQUE");
                boolean unique = !nonUnique;
                if (iname == null) continue;
                meta.indexes.computeIfAbsent(iname, k -> new IndexMeta(iname, unique)).columns.add(col);
            }
        }

        // foreign keys
        try (ResultSet fk = md.getImportedKeys(null, schema, table)) {
            while (fk.next()) {
                String pkTable = fk.getString("PKTABLE_NAME");
                String pkCol = fk.getString("PKCOLUMN_NAME");
                String fkCol = fk.getString("FKCOLUMN_NAME");
                meta.foreignKeys.add(new FKMeta(fkCol, pkTable, pkCol));
            }
        }

        // table remarks (best-effort via getTables)
        try (ResultSet t = md.getTables(null, schema, table, null)) {
            if (t.next()) {
                meta.tableRemarks = t.getString("REMARKS");
            }
        } catch (Throwable ignored) { }

        // constraints (information_schema best-effort)
        try {
            String qc;
            PreparedStatement ps;
            if (schema != null) {
                qc = "SELECT constraint_name, constraint_type FROM information_schema.table_constraints WHERE table_schema = ? AND table_name = ?";
                ps = conn.prepareStatement(qc);
                ps.setString(1, schema);
                ps.setString(2, table);
            } else {
                qc = "SELECT constraint_name, constraint_type FROM information_schema.table_constraints WHERE table_name = ?";
                ps = conn.prepareStatement(qc);
                ps.setString(1, table);
            }
            try (ResultSet cr = ps.executeQuery()) {
                while (cr.next()) meta.constraints.add(cr.getString("constraint_name") + ": " + cr.getString("constraint_type"));
            }
        } catch (Throwable ignored) { }

        // check constraints
        try {
            String qc;
            PreparedStatement ps;
            if (schema != null) {
                qc = "SELECT cc.constraint_name, cc.check_clause FROM information_schema.check_constraints cc JOIN information_schema.table_constraints tc ON cc.constraint_name = tc.constraint_name WHERE tc.table_schema = ? AND tc.table_name = ?";
                ps = conn.prepareStatement(qc);
                ps.setString(1, schema);
                ps.setString(2, table);
            } else {
                qc = "SELECT cc.constraint_name, cc.check_clause FROM information_schema.check_constraints cc JOIN information_schema.table_constraints tc ON cc.constraint_name = tc.constraint_name WHERE tc.table_name = ?";
                ps = conn.prepareStatement(qc);
                ps.setString(1, table);
            }
            try (ResultSet cr = ps.executeQuery()) {
                while (cr.next()) meta.checks.add(cr.getString("constraint_name") + ": " + cr.getString("check_clause"));
            }
        } catch (Throwable ignored) { }

        // domains / enums
        try (PreparedStatement ps = conn.prepareStatement(schema != null ?
                "SELECT column_name, domain_name, udt_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?" :
                "SELECT column_name, domain_name, udt_name FROM information_schema.columns WHERE table_name = ?")) {
            if (schema != null) {
                ps.setString(1, schema);
                ps.setString(2, table);
            } else {
                ps.setString(1, table);
            }
            try (ResultSet cr = ps.executeQuery()) {
                while (cr.next()) {
                    String col = cr.getString("column_name");
                    String domain = cr.getString("domain_name");
                    String udt = cr.getString("udt_name");
                    if ((domain != null && !domain.isBlank()) || (udt != null && !udt.isBlank())) {
                        meta.domains.add(col + ": domain=" + (domain == null ? "" : domain) + (udt == null ? "" : (" udt=" + udt)));
                    }
                }
            }
        } catch (Throwable ignored) { }

        return meta;
    }
}
