package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class DuplicateMagicsTest {
    private static final String PACKAGE_PATH = "io/github/spencerpark/ijava/magics";
    private static final String PACKAGE_NAME = "io.github.spencerpark.ijava.magics";

    @Test
    public void testNoDuplicateCellMagicNames() throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = cl.getResources(PACKAGE_PATH);

        Map<String, List<String>> namesToClasses = new HashMap<>();

        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            String protocol = url.getProtocol();
            if (protocol.equals("file")) {
                Path dir = Paths.get(url.toURI());
                try (var stream = Files.list(dir)) {
                    List<Path> classFiles = stream.filter(p -> p.toString().endsWith(".class"))
                            .collect(Collectors.toList());
                    for (Path p : classFiles) {
                        String fileName = p.getFileName().toString();
                        String className = fileName.substring(0, fileName.length() - 6);
                        String fqcn = PACKAGE_NAME + "." + className;
                        inspectClassForCellMagic(fqcn, namesToClasses);
                    }
                }
            } else if (protocol.equals("jar")) {
                JarURLConnection conn = (JarURLConnection) url.openConnection();
                try (JarFile jar = conn.getJarFile()) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry e = entries.nextElement();
                        String name = e.getName();
                        if (name.startsWith(PACKAGE_PATH) && name.endsWith(".class")) {
                            String rel = name.substring(PACKAGE_PATH.length() + 1); // skip '/'
                            String className = rel.replace('/', '.').replaceAll("\\.class$", "");
                            String fqcn = PACKAGE_NAME + "." + className;
                            inspectClassForCellMagic(fqcn, namesToClasses);
                        }
                    }
                }
            }
        }

        // Now ensure no duplicates
        List<String> duplicates = namesToClasses.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (!duplicates.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            msg.append("Duplicate @CellMagic names found:\n");
            for (String d : duplicates) {
                msg.append(d).append(" -> ").append(namesToClasses.get(d)).append("\n");
            }
            Assert.fail(msg.toString());
        }
    }

    private void inspectClassForCellMagic(String fqcn, Map<String, List<String>> map) {
        try {
            Class<?> cls = Class.forName(fqcn);
            if (cls.isAnnotationPresent(CellMagic.class)) {
                CellMagic a = cls.getAnnotation(CellMagic.class);
                String name = a.value();
                map.computeIfAbsent(name, k -> new ArrayList<>()).add(fqcn);
            }
            // also inspect methods
            for (var m : cls.getDeclaredMethods()) {
                if (m.isAnnotationPresent(CellMagic.class)) {
                    CellMagic a = m.getAnnotation(CellMagic.class);
                    String name = a.value();
                    map.computeIfAbsent(name, k -> new ArrayList<>()).add(fqcn + "#" + m.getName());
                }
            }
        } catch (ClassNotFoundException e) {
            // ignore
        }
    }
}
