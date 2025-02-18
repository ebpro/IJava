package io.github.spencerpark.ijava.magics;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import io.github.classgraph.ClassGraph;
import io.github.spencerpark.ijava.IJava;
import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import io.github.spencerpark.jupyter.kernel.magic.registry.MagicsArgs;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.core.DiagramDescription;

import javax.imageio.ImageIO;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static io.github.spencerpark.ijava.runtime.Display.display;
import static io.github.spencerpark.ijava.runtime.Magics.cellMagic;

@Slf4j
public class JavaMagics {

    /**
     * %%javasrcMethodByAnnotationName Test POST
     * /src/Test.java
     */
    // IJava.getKernelInstance().getMagics().registerCellMagic("javasrcMethodByAnnotationName",
    // (args, body) -> {
/*     @CellMagic("javasrcMethodByAnnotationName")
    public void javasrcMethodByAnnotationName(List<String> args, String body) {
        String filename = body;
        String className = args.get(0);
        String annotationName = args.get(1);
        int index = args.size() == 3 ? Integer.valueOf(args.get(2)) : 0;
        CompilationUnit cu = StaticJavaParser.parse(Files.readString(Path.of(filename)));
        String out = cu.getClassByName(className).get()
                .getMethods()
                .stream()
                .filter(m -> m.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals(annotationName)))
                .skip(index)
                .findFirst().get().toString();
        out = "```Java\n" + out + "\n```";
        display(out, "text/markdown");
    }*/

    /**
     * %%javasrcMethodByName Test getAll
     * /src/Test.java
     */
    // IJava.getKernelInstance().getMagics().registerCellMagic("javasrcMethodByName",
    // (args, body) -> {
/*    @CellMagic("javasrcMethodByName")
    public void javasrcMethodByName(List<String> args, String body) {
        String filename = body;
        String className = args.get(0);
        String methodName = args.get(1);
        int index = args.size() == 3 ? Integer.valueOf(args.get(2)) : 0;
        CompilationUnit cu = StaticJavaParser.parse(Files.readString(Path.of(filename)));
        String out = cu.getClassByName(className).get()
                .getMethodsByName(methodName)
                .get(index)
                .toString();
        out = "```Java\n" + out + "\n```";
        display(out, "text/markdown");
    }*/

    /**
     * %%javasrcInterfaceByName Test
     * /src/Test.java
     */

    // IJava.getKernelInstance().getMagics().registerCellMagic("javasrcInterfaceByName",
    // (args, body) -> {
/*     @CellMagic("javasrcInterfaceByName")
    public void javasrcInterfaceByName(List<String> args, String body) {
        final String path = args.get(0);
        final String filename = path + "/" + body.replace(".", "/") + ".java";
        final String className = body.substring(body.lastIndexOf('.') + 1);
        CompilationUnit cu = StaticJavaParser.parse(Files.readString(Path.of(filename)));
        String out = cu.getInterfaceByName(className).get()
                .toString();
        // out = "```Java\n"+out+"\n```";
        out = "```{.java fig-cap=\"TEST\",filename=\"" + filename.substring(filename.lastIndexOf('/') + 1) + "\"}\n"
                + out + "\n```";
        display(out, "text/markdown");
    }*/

    /**
     * %%javasrcClassByName Test
     * /src/Test.java
     */

    // IJava.getKernelInstance().getMagics().registerCellMagic("javasrcClassByName",
    // (args, body) -> {
/*     @CellMagic("javasrcClassByName")
    public void javasrcClassByName(List<String> args, String body) {
        String filename = body;
        String className = args.get(0);
        CompilationUnit cu = StaticJavaParser.parse(Files.readString(Path.of(filename)));
        CompilationUnit lpp = LexicalPreservingPrinter.setup(cu);

        String out = LexicalPreservingPrinter.print(lpp.getClassByName(className).get());

        out = "```Java\n" + out + "\n```";
        display(out, "text/markdown");
    }*/

    /**
     * %%javasrcMethodByAnnotationName Test POST
     * /src/Test.java
     */
    @CellMagic("javasrcMethodByAnnotationName")
    public void javasrcMethodByAnnotationName(List<String> args, String body) throws IOException {
        String filename = body;
        String className = args.get(0);
        String annotationName = args.get(1);
        int index = args.size() == 3 ? Integer.valueOf(args.get(2)) : 0;
        CompilationUnit cu = null;
        try {
            cu = StaticJavaParser.parse(Files.readString(Path.of(filename)));
        } catch (IOException e) {
            log.error("Error parsing file", e);
            throw e;
        }
        String out = cu.getClassByName(className).get()
                .getMethods()
                .stream()
                .filter(m -> m.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals(annotationName)))
                .skip(index)
                .findFirst().get().toString();
        out = "```Java\n" + out + "\n```";
        display(out, "text/markdown");
    }

    /**
     * %%javasrcMethodByName Test getAll
     * /src/Test.java
     */
    @CellMagic("javasrcMethodByName")
    public void javasrcMethodByName(List<String> args, String body) throws IOException {
        String filename = body;
        String className = args.get(0);
        String methodName = args.get(1);
        int index = args.size() == 3 ? Integer.valueOf(args.get(2)) : 0;
        CompilationUnit cu = null;
        try {
            cu = StaticJavaParser.parse(Files.readString(Path.of(filename)));
        } catch (IOException e) {
            log.error("Error parsing file", e);
            throw e;
        }
        String out = cu.getClassByName(className).get()
                .getMethodsByName(methodName)
                .get(index)
                .toString();
        out = "```Java\n" + out + "\n```";
        display(out, "text/markdown");
    }

    @CellMagic("javasrcInterfaceByName")
    public void javasrcInterfaceByName(List<String> args, String body) throws IOException {
        final String path = args.get(0);
        final String filename = path + "/" + body.replace(".", "/") + ".java";
        final String className = body.substring(body.lastIndexOf('.') + 1);
        CompilationUnit cu = null;
        try {
            cu = StaticJavaParser.parse(Files.readString(Path.of(filename)));
        } catch (IOException e) {
            log.error("Error parsing file", e);
            throw e;
        }
        String out = cu.getInterfaceByName(className).get()
                .toString();
        out = "```{.java fig-cap=\"TEST\",filename=\"" + filename.substring(filename.lastIndexOf('/') + 1) + "\"}\n"
                + out + "\n```";
        display(out, "text/markdown");
    }

    @CellMagic("javasrcClassByName")
    public void javasrcClassByName(List<String> args, String body) throws IOException {
        String filename = body;
        String className = args.get(0);
        CompilationUnit cu = null;
        try {
            cu = StaticJavaParser.parse(Files.readString(Path.of(filename)));
        } catch (IOException e) {
            log.error("Error parsing file", e);
            throw e;
        }
        CompilationUnit lpp = LexicalPreservingPrinter.setup(cu);

        String out = LexicalPreservingPrinter.print(lpp.getClassByName(className).get());

        out = "```Java\n" + out + "\n```";
        display(out, "text/markdown");
    }




}