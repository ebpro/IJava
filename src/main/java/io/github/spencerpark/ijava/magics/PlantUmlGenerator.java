package io.github.spencerpark.ijava.magics;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates PlantUML diagrams from a list of Java classes and interfaces.
 */
public final class PlantUmlGenerator {

    private PlantUmlGenerator() {
    }

    /**
     * Generate PlantUML text for the given classes.
     *
     * @param classes             List of classes/interfaces to include
     * @param includeFields       Include fields
     * @param includeMethods      Include methods
     * @param includeConstructors Include constructors
     * @param includeInterfaces   Include interface relationships
     * @param includeNonPublic    Include non-public members
     * @param excludeInherited    Exclude inherited methods/constructors
     * @return PlantUML diagram text
     */
    public static String generate(
            List<Class<?>> classes,
            boolean includeFields,
            boolean includeMethods,
            boolean includeConstructors,
            boolean includeInterfaces,
            boolean includeNonPublic,
            boolean excludeInherited) {

        Set<String> names = classes.stream()
                .map(c -> sanitize(c.getSimpleName()))
                .collect(Collectors.toCollection(HashSet::new));

        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("skinparam classAttributeIconSize 0\n");

        for (Class<?> c : classes) {
            String name = sanitize(c.getSimpleName());

            if (c.isInterface())
                sb.append("interface ").append(name).append(" {\n");
            else
                sb.append("class ").append(name).append(" {\n");

            // ---------- Fields ----------
            if (includeFields) {
                for (Field f : c.getDeclaredFields()) {
                    if (!includeNonPublic && !Modifier.isPublic(f.getModifiers()))
                        continue;
                    String staticMark = Modifier.isStatic(f.getModifiers()) ? " {static}" : "";
                    sb.append("  ").append(simpleVisibility(f.getModifiers()))
                            .append(f.getName())
                            .append(" : ").append(sanitizeType(f.getType()))
                            .append(staticMark).append("\n");
                }
            }

            // ---------- Constructors ----------
            if (includeConstructors && !c.isInterface()) {
                for (Constructor<?> ctr : c.getDeclaredConstructors()) {
                    if (!includeNonPublic && !Modifier.isPublic(ctr.getModifiers()))
                        continue;
                    if (excludeInherited && ctr.getDeclaringClass() != c)
                        continue;

                    String params = Arrays.stream(ctr.getParameterTypes())
                            .map(PlantUmlGenerator::sanitizeType)
                            .collect(Collectors.joining(", "));
                    sb.append("  ").append(simpleVisibility(ctr.getModifiers()))
                            .append(c.getSimpleName())
                            .append("(").append(params).append(")")
                            .append("\n");
                }
            }

            // ---------- Methods ----------
            if (includeMethods) {
                Set<Method> allMethods = new LinkedHashSet<>();
                allMethods.addAll(Arrays.asList(c.getDeclaredMethods()));
                allMethods.addAll(Arrays.asList(c.getMethods())); // includes inherited + default

                for (Method m : allMethods) {
                    if (!includeNonPublic && !Modifier.isPublic(m.getModifiers()))
                        continue;
                    if (excludeInherited && m.getDeclaringClass() != c)
                        continue;
                    if (m.isSynthetic() || m.isBridge())
                        continue;

                    String staticMark = Modifier.isStatic(m.getModifiers()) ? " {static}" : "";
                    String params = Arrays.stream(m.getParameterTypes())
                            .map(PlantUmlGenerator::sanitizeType)
                            .collect(Collectors.joining(", "));

                    sb.append("  ").append(simpleVisibility(m.getModifiers()))
                            .append(m.getName())
                            .append("(").append(params).append(")")
                            .append(" : ").append(sanitizeType(m.getReturnType()))
                            .append(staticMark)
                            .append("\n");
                }
            }

            sb.append("}\n");
        }

        // ---------- Relationships ----------
        for (Class<?> c : classes) {
            String cname = sanitize(c.getSimpleName());

            // Superclass
            Class<?> sup = c.getSuperclass();
            if (sup != null && sup != Object.class && names.contains(sanitize(sup.getSimpleName()))) {
                sb.append(cname).append(" --|> ").append(sanitize(sup.getSimpleName())).append("\n");
            }

            // Interfaces
            if (includeInterfaces) {
                for (Class<?> itf : c.getInterfaces()) {
                    if (!names.contains(sanitize(itf.getSimpleName())))
                        continue;

                    // Interface -> Interface : héritage d’interface (--|>)
                    // Classe -> Interface : implémentation (..|>)
                    if (c.isInterface())
                        sb.append(cname).append(" --|> ").append(sanitize(itf.getSimpleName())).append("\n");
                    else
                        sb.append(cname).append(" ..|> ").append(sanitize(itf.getSimpleName())).append("\n");
                }
            }
        }

        // ---------- Associations via fields ----------
        for (Class<?> c : classes) {
            String cname = sanitize(c.getSimpleName());
            for (Field f : c.getDeclaredFields()) {
                Class<?> t = f.getType();
                String tname = sanitize(t.getSimpleName());
                if (names.contains(tname)) {
                    sb.append(cname).append(" --> ").append(tname).append(" : ").append(f.getName()).append("\n");
                }
            }
        }

        sb.append("@enduml\n");
        return sb.toString();
    }

    // ---------- Helper Methods ----------
    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9_\\$]", "_");
    }

    private static String sanitizeType(Class<?> c) {
        if (c.isArray())
            return sanitizeType(c.getComponentType()) + "[]";
        return sanitize(c.getSimpleName());
    }

    private static String simpleVisibility(int mods) {
        if (Modifier.isPublic(mods))
            return "+ ";
        if (Modifier.isProtected(mods))
            return "# ";
        if (Modifier.isPrivate(mods))
            return "- ";
        return "~ ";
    }
}
