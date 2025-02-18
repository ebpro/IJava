package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.core.DiagramDescription;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static io.github.spencerpark.ijava.runtime.Display.display;
import static io.github.spencerpark.ijava.runtime.Magics.cellMagic;

@Slf4j
public class JavaPlantUMLMagics {

    /**
     * Render plantUML from cell
     */
    @CellMagic("plantUML")
    public void plantUML(List<String> args, String body) throws IOException {
        // sets the results mimetype
        if (args.size() > 1)
            throw new IllegalArgumentException("Max one argument : SVG or PNG");
        String fileFormat;
        if (args.isEmpty())
            fileFormat = "SVG";
        else
            fileFormat = args.get(0);

        SourceStringReader reader = new SourceStringReader(body);
        final ByteArrayOutputStream os = new ByteArrayOutputStream();

        try {
            DiagramDescription desc = reader.outputImage(os, new FileFormatOption(FileFormat.valueOf(fileFormat)));
        } catch (IOException e) {
            log.error("Error parsing file", e);
            throw e;
        }
        os.close();
        Object out;
        if (fileFormat.equals("SVG"))
            out = new String(os.toByteArray(), StandardCharsets.UTF_8);
        else
            out = ImageIO.read(new ByteArrayInputStream(os.toByteArray()));

        display(out, fileFormat.equals("SVG") ? "image/svg+xml" : "image/png");
    }

    /**
     * Render plantUML from file
     */
    @CellMagic("plantUMLFile")
    public void plantUMLFile(List<String> args, String body) {
        // sets the results mimetype
        if (args.size() > 1)
            throw new IllegalArgumentException("Max one argument : SVG or PNG");
        String fileFormat;
        if (args.isEmpty())
            fileFormat = "SVG";
        else
            fileFormat = args.get(0);

        List<Object> outList = new ArrayList<>();
        body.lines().forEach(filename -> {
            Object out;
            try {
                out = cellMagic("plantUML", args, Files.readString(Paths.get(filename)));
                // display(out,fileFormat.equals("SVG")?"image/svg+xml":"image/png");
                outList.add(out);
            } catch (java.io.IOException e) {
                log.error("Error parsing file", e);
                throw new RuntimeException(e);
            }
        });

    }

}
