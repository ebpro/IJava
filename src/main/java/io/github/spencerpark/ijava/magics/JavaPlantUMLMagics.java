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
        // args may include a format (SVG/PNG) and/or a flag to show source for
        // debugging.
        boolean showSource = args.stream()
                .anyMatch(a -> a.equalsIgnoreCase("showSource") || a.equalsIgnoreCase("show-source")
                        || a.equals("--show-source") || a.equals("-s") || a.equalsIgnoreCase("source"));
        String fileFormat = args.stream().filter(a -> a.equalsIgnoreCase("SVG") || a.equalsIgnoreCase("PNG"))
                .findFirst().orElse("SVG");

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
        if (fileFormat.equals("SVG")) {
            String svg = new String(os.toByteArray(), StandardCharsets.UTF_8);
            int idx = svg.indexOf("<svg");
            if (idx > 0)
                svg = svg.substring(idx);
            out = svg;
        } else {
            out = ImageIO.read(new ByteArrayInputStream(os.toByteArray()));
        }

        if (showSource) {
            String md = "```plantuml\n" + (body == null ? "" : body) + "\n```";
            display(md, "text/markdown");
        }

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
            try {
                Object out = cellMagic("plantUML", args, Files.readString(Paths.get(filename)));
                // The invoked cell magic may perform its own display and return null; only
                // display non-null results.
                if (out != null) {
                    outList.add(out);
                    display(out, fileFormat.equals("SVG") ? "image/svg+xml" : "image/png");
                }
            } catch (java.io.IOException e) {
                log.error("Error reading PlantUML file", e);
                throw new RuntimeException(e);
            } catch (RuntimeException e) {
                // Bubble up with context to help debugging
                log.error("Error running plantUML magic for file {}", filename, e);
                throw new RuntimeException("Error running plantUML magic for file " + filename + ": " + e.getMessage(),
                        e);
            }
        });
        // if caller expects a combined representation, nothing to return here; outputs
        // have been displayed

    }

}
