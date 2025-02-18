package io.github.spencerpark.ijava.magics;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

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

}
