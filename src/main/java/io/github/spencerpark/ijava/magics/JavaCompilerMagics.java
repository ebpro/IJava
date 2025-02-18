package io.github.spencerpark.ijava.magics;

import io.github.classgraph.ClassGraph;
import io.github.spencerpark.ijava.IJava;
import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import io.github.spencerpark.jupyter.kernel.magic.registry.MagicsArgs;
import lombok.extern.slf4j.Slf4j;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class JavaCompilerMagics {

    @CellMagic("compile")
    public void compile(List<String> args, String body) throws IOException {
        try {
            // We parse the magics arguments
            MagicsArgs schema = MagicsArgs.builder()
                    .required("filePath")
                    // .optional("x")
                    // .keyword("from", MagicsArgs.KeywordSpec.ONCE)
                    // .flag("verbose", 'v',"true")
                    .onlyKnownKeywords()
                    .onlyKnownFlags()
                    .build();
            Map<String, List<String>> params = schema.parse(args);
            // display(params);
            Map<String, List<String>> vals = schema.parse(args);

            // We create the tmp dit if necessary
            // File tempDirectory = Files.createTempDirectory("jupyterJava").toFile();
            File tempDirectory = new File("/tmp/jupyterJava");
            String filename = vals.get("filePath").get(0);
            File file = new File(tempDirectory, filename);
            Files.createDirectories(file.getParentFile().toPath());
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(body);
            writer.close();
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
            Iterable<? extends JavaFileObject> compilationUnits1 = fileManager
                    .getJavaFileObjectsFromFiles(Arrays.asList(new File[]{file}));

            // We use the ClassGraph reflection API
            List<URI> classpath = new ClassGraph().getClasspathURIs();
            List<String> optionList = new ArrayList<String>();
            optionList.addAll(
                    Arrays.asList("-cp", classpath.stream().map(URI::toString).collect(Collectors.joining(":"))));
            optionList.addAll(Arrays.asList("--enable-preview", "--release",
                    System.getProperty("java.version").split("[.]")[0], "-proc:full", "-implicit:class"));
            compiler.getTask(null, fileManager, null, optionList, null, compilationUnits1).call();
            // display("Compilation of "+file);

            IJava.getKernelInstance().addToClasspath(tempDirectory.toString());

        } catch (IOException e) {
            log.error("Error parsing file", e);
            throw e;
        }
    }


}
