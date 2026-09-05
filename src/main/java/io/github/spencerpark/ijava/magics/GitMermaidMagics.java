package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.ijava.runtime.Display;
import io.github.spencerpark.ijava.utils.FileUtils;
import io.github.spencerpark.jupyter.kernel.magic.registry.LineMagic;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Magic to generate Mermaid diagrams from the current Git repository.
 *
 * Usage examples:
 * %git-graph-mermaid --max=50 --branch=main
 * %git-graph-mermaid --max=100 --render
 */
public class GitMermaidMagics {

    @LineMagic("git-graph-mermaid")
    public void gitGraphMermaid(List<String> args) {
        int max = 50;
        String branch = null;
        boolean render = false;
        String format = "flowchart"; // or "gitGraph"
        String repo = null; // optional --repo=path

        if (args != null) {
            for (String a : args) {
                if (a == null) continue;
                if (a.startsWith("--max=")) {
                    try { max = Integer.parseInt(a.substring("--max=".length())); } catch (NumberFormatException ignored) {}
                } else if (a.startsWith("--branch=")) {
                    branch = a.substring("--branch=".length());
                } else if (a.equals("--render")) {
                    render = true;
                } else if (a.startsWith("--format=")) {
                    format = a.substring("--format=".length());
                } else if (a.startsWith("--repo=")) {
                    repo = a.substring("--repo=".length());
                }
            }
        }

        // Build git log command
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        if (repo != null) { cmd.add("-C"); cmd.add(repo); }
        cmd.add("log");
        cmd.add("--pretty=format:%H%x01%h%x01%P%x01%D%x01%s");
        cmd.add("-n"); cmd.add(Integer.toString(max));
        if (branch != null) cmd.add(branch);

        String out;
        try {
            out = runCommand(cmd);
        } catch (IOException | InterruptedException e) {
            Display.display("Failed to run git: " + e.getMessage(), "text/plain");
            return;
        }

        if (out == null || out.isBlank()) {
            Display.display("No commits found or not a git repository.", "text/plain");
            return;
        }

        // Parse lines: fullhashshorthashparentsdecorationssubject
        Map<String,String> labelByShort = new LinkedHashMap<>();
        Map<String,Set<String>> edges = new LinkedHashMap<>();
        Map<String,List<String>> decoByShort = new HashMap<>();

        for (String line : out.split("\n")) {
            String[] parts = line.split("\u0001", 5);
            if (parts.length < 5) continue;
            String full = parts[0].trim();
            String shortH = parts[1].trim();
            String parents = parts[2].trim();
            String decos = parts[3].trim();
            String subj = parts[4].trim();

            String label = shortH + " ";
            if (!subj.isEmpty()) label += subj;
            labelByShort.put(shortH, label);

            if (!edges.containsKey(shortH)) edges.put(shortH, new LinkedHashSet<>());
            if (!parents.isEmpty()) {
                for (String p : parents.split(" ")) {
                    if (p.isBlank()) continue;
                    String ps = p.substring(0, Math.min(7, p.length()));
                    edges.get(shortH).add(ps);
                }
            }

            // parse decorations to detect branch names
            if (!decos.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (String tok : decos.split(",")) {
                    tok = tok.trim();
                    if (tok.isEmpty()) continue;
                    // examples: "HEAD -> main" or "origin/main" or "tag: v1.0"
                    if (tok.contains("->")) {
                        String[] sp = tok.split("->");
                        String candidate = sp[1].trim();
                        if (!candidate.startsWith("tag:")) names.add(candidate.replaceAll("^origin/", ""));
                    } else if (tok.startsWith("tag:")) {
                        // skip tags
                    } else {
                        names.add(tok.replaceAll("^origin/", ""));
                    }
                }
                if (!names.isEmpty()) decoByShort.put(shortH, names);
            }
        }

        // Build Mermaid content depending on format
        StringBuilder m = new StringBuilder();
        if ("gitGraph".equalsIgnoreCase(format)) {
            m.append("```{mermaid}\\n");
            m.append("gitGraph\\n");

            Set<String> declaredBranches = new HashSet<>();
            String currentBranch = null;
            Map<String,String> commitPrimaryBranch = new HashMap<>();

            for (Map.Entry<String,String> e : labelByShort.entrySet()) {
                String shortH = e.getKey();
                String label = escapeForMermaid(e.getValue());

                List<String> branches = decoByShort.get(shortH);
                String primary = null;
                if (branches != null && !branches.isEmpty()) {
                    // choose first decoration as primary branch
                    primary = branches.get(0);
                    // declare any unseen branches
                    for (String b : branches) {
                        if (!declaredBranches.contains(b)) {
                            m.append("  branch ").append(safeName(b)).append("\\n");
                            declaredBranches.add(b);
                        }
                    }
                }

                // checkout primary if needed
                if (primary != null) {
                    if (!Objects.equals(currentBranch, primary)) {
                        m.append("  checkout ").append(safeName(primary)).append("\\n");
                        currentBranch = primary;
                    }
                    commitPrimaryBranch.put(shortH, primary);
                } else {
                    // ensure there is a branch to commit to
                    if (currentBranch == null) {
                        // create an anonymous branch
                        String anon = "main";
                        if (!declaredBranches.contains(anon)) {
                            m.append("  branch ").append(anon).append("\\n");
                            declaredBranches.add(anon);
                        }
                        m.append("  checkout ").append(anon).append("\\n");
                        currentBranch = anon;
                    }
                }

                m.append("  commit id: \"").append(shortH).append(" ").append(label).append("\"\\n");

                // handle merges: if multiple parents, try to emit merge commands
                Set<String> parents = edges.getOrDefault(shortH, Collections.emptySet());
                if (parents.size() > 1) {
                    // skip the first parent (assumed main); for each additional try to find branch name
                    boolean first = true;
                    for (String p : parents) {
                        if (first) { first = false; continue; }
                        String pbranch = commitPrimaryBranch.get(p);
                        if (pbranch == null) {
                            List<String> pb = decoByShort.get(p);
                            if (pb != null && !pb.isEmpty()) pbranch = pb.get(0);
                        }
                        if (pbranch != null) {
                            m.append("  merge ").append(safeName(pbranch)).append("\\n");
                        }
                    }
                }
            }

            m.append("```\\n");
        } else {
            m.append("```{mermaid}\\n");
            m.append("flowchart TD\\n");

            // Nodes
            for (Map.Entry<String,String> e : labelByShort.entrySet()) {
                String id = "c" + e.getKey();
                String label = escapeForMermaid(e.getValue());
                m.append(id).append("[\\\"").append(label).append("\\\"]\\n");
            }

            // Edges
            for (Map.Entry<String,Set<String>> e : edges.entrySet()) {
                String fromId = "c" + e.getKey();
                for (String p : e.getValue()) {
                    String toId = "c" + p;
                    m.append(fromId).append(" --> ").append(toId).append("\\n");
                }
            }

            m.append("```\\n");
        }

        String mermaidBlock = m.toString();

        if (!render) {
            Display.display(mermaidBlock, "text/markdown");
            return;
        }

        // Attempt to render via mmdc if requested
        try {
            Path tempDir = FileUtils.createPrivateTempDir("ijava-git-graph-");
            File tmpMmd = FileUtils.createPrivateTempFile(tempDir, "git-graph-", ".mmd").toFile();
            File outSvg = FileUtils.createPrivateTempFile(tempDir, "git-graph-", ".svg").toFile();
            try {
                // write mermaid source without fences
                StringBuilder raw = new StringBuilder();
                raw.append("flowchart TD\n");
                for (Map.Entry<String,String> e : labelByShort.entrySet()) {
                    raw.append("c").append(e.getKey()).append("[\"").append(escapeForMermaid(e.getValue())).append("\"]\n");
                }
                for (Map.Entry<String,Set<String>> e : edges.entrySet()) {
                    for (String p : e.getValue()) {
                        raw.append("c").append(e.getKey()).append(" --> c").append(p).append("\n");
                    }
                }
                Files.writeString(tmpMmd.toPath(), raw.toString());

                List<String> renderCmd = new ArrayList<>();
                renderCmd.add("mmdc");
                renderCmd.add("-i"); renderCmd.add(tmpMmd.getAbsolutePath());
                renderCmd.add("-o"); renderCmd.add(outSvg.getAbsolutePath());

                runCommand(renderCmd);
                if (outSvg.exists()) {
                    String svg = Files.readString(outSvg.toPath());
                    Display.display(svg, "image/svg+xml");
                    return;
                } else {
                    Display.display(mermaidBlock + "\n\n(Note: failed to render with mmdc; ensure mermaid-cli is installed)", "text/markdown");
                    return;
                }
            } finally {
                FileUtils.deleteRecursively(tempDir);
            }
        } catch (Throwable t) {
            Display.display(mermaidBlock + "\n\n(Note: rendering failed: " + t.getMessage() + ")", "text/markdown");
        }
    }

    private static String runCommand(List<String> cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (InputStreamReader isr = new InputStreamReader(p.getInputStream()); BufferedReader br = new BufferedReader(isr)) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) out.append(line).append('\n');
            p.waitFor();
            return out.toString();
        }
    }

    private static String escapeForMermaid(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("[", "(").replace("]", ")");
    }
    private static String safeName(String s) {
        if (s == null) return "branch";
        return s.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

}

