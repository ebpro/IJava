package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.ijava.IJava;
import io.github.spencerpark.ijava.runtime.Display;
import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import java.util.*;
import java.util.stream.Collectors;

public class BenchmarkMagics {
    @CellMagic("benchmark")
    public void benchmark(List<String> args, String body) throws Exception {
        if (args == null)
            args = Collections.emptyList();
        if (body == null)
            body = "";

        // Help
        if (args.contains("--help") || args.contains("-h")) {
            System.out.println("## %%benchmark - Compare implementations performance\n\n" +
                    "Usage: %%benchmark [--help] [--sweep var=<name> start=<n> end=<n> step=<n>] [--chart] [iterations=<n>] [warmup=<n>]\\n\n" +
                    "Provide one or more implementations separated by a line containing '---'.\n" +
                    "Example:\n%%benchmark iterations=5\ncode-for-impl-1\n---\ncode-for-impl-2\n\n" +
                    "NOTE: timings are measured through jshell snippet evaluation, so they include\n" +
                    "snippet compilation and dispatch overhead. Use %%benchmark for classroom-level\n" +
                    "comparisons only. For publication-grade microbenchmarks use JMH, e.g.\n" +
                    "  %%maven org.openjdk.jmh:jmh-core:1.37\n");
            return;
        }

        Map<String, String> opts = OptionUtils.parseOptions(args);
        int iterations = Integer.parseInt(opts.getOrDefault("iterations", "5"));
        int warmup = Integer.parseInt(opts.getOrDefault("warmup", "1"));

        // Split implementations by a separator line '---'
        String[] parts = body.split("(?m)^---$");
        List<String> impls = Arrays.stream(parts).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (impls.isEmpty()) {
            System.out.println("No implementations provided. Separate implementations by a line containing '---'.");
            return;
        }

        // Sweep option: --sweep var=x start=1 end=10 step=1
        boolean doSweep = args.contains("--sweep") || "true".equals(opts.get("sweep"));
        if (doSweep) {
            String var = opts.get("var");
            if (var == null || var.isBlank()) {
                System.out.println("Missing sweep variable. Provide var=<name> option.");
                return;
            }
            int start = Integer.parseInt(opts.getOrDefault("start", "0"));
            int end = Integer.parseInt(opts.getOrDefault("end", "10"));
            int step = Integer.parseInt(opts.getOrDefault("step", "1"));

            List<Integer> sweepValues = new ArrayList<>();
            if (step == 0)
                step = 1;
            if (step > 0) {
                for (int v = start; v <= end; v += step)
                    sweepValues.add(v);
            } else {
                for (int v = start; v >= end; v += step)
                    sweepValues.add(v);
            }

            // declare variable initially (try declare, otherwise assign)
            try {
                IJava.getKernelInstance().evalRaw("int " + var + " = " + start + ";");
            } catch (Exception e) {
                try {
                    IJava.getKernelInstance().evalRaw(var + " = " + start + ";");
                } catch (Exception ignored) {
                }
            }

            // results: for each sweep value, for each impl store mean ms
            double[][] means = new double[sweepValues.size()][impls.size()];
            for (int si = 0; si < sweepValues.size(); si++) {
                int val = sweepValues.get(si);
                try {
                    IJava.getKernelInstance().evalRaw(var + " = " + val + ";");
                } catch (Exception ignored) {
                }

                for (int ii = 0; ii < impls.size(); ii++) {
                    String impl = impls.get(ii);
                    // warmup
                    for (int w = 0; w < warmup; w++) {
                        IJava.getKernelInstance().evalRaw(impl);
                    }
                    // measure
                    List<Long> times = new ArrayList<>();
                    for (int i = 0; i < iterations; i++) {
                        long startNs = System.nanoTime();
                        IJava.getKernelInstance().evalRaw(impl);
                        long endNs = System.nanoTime();
                        times.add(endNs - startNs);
                    }
                    double meanNs = times.stream().mapToLong(Long::longValue).sum() / (double) times.size();
                    means[si][ii] = meanNs / 1e6; // ms
                }
            }

            // render multi-series SVG line chart (sweep on x, implementations as series)
            try {
                int width = 1000;
                int height = 400;
                int marginLeft = 60;
                int marginRight = 20;
                int legendWidth = 260;
                int marginBottom = 60;
                int marginTop = 30;
                int plotW = width - marginLeft - legendWidth - marginRight;
                int plotH = height - marginTop - marginBottom;

                double max = 0.0;
                for (double[] row : means)
                    for (double d : row)
                        if (d > max)
                            max = d;
                if (max == 0)
                    max = 1.0;

                StringBuilder svg = new StringBuilder();
                svg.append("<?xml version='1.0' encoding='UTF-8'?>");
                svg.append("<svg xmlns='http://www.w3.org/2000/svg' width='" + width + "' height='" + height + "'>");
                svg.append("<style>text{font-family:Arial,Helvetica,sans-serif;font-size:12px}</style>");

                // axes
                svg.append("<line x1='" + marginLeft + "' y1='" + marginTop + "' x2='" + marginLeft + "' y2='"
                        + (marginTop + plotH) + "' stroke='#333'/>");
                svg.append("<line x1='" + marginLeft + "' y1='" + (marginTop + plotH) + "' x2='" + (marginLeft + plotW)
                        + "' y2='" + (marginTop + plotH) + "' stroke='#333'/>");

                // x ticks and labels
                int nX = sweepValues.size();
                int denomX = Math.max(1, nX - 1);
                for (int i = 0; i < nX; i++) {
                    int x = marginLeft + (int) ((i / (double) denomX) * plotW);
                    int y = marginTop + plotH;
                    svg.append("<line x1='" + x + "' y1='" + (y) + "' x2='" + x + "' y2='" + (y + 6)
                            + "' stroke='#666'/>");
                    svg.append("<text x='" + x + "' y='" + (y + 20) + "' text-anchor='middle'>" + sweepValues.get(i)
                            + "</text>");
                }

                // x-axis label (variable name)
                svg.append("<text x='" + (marginLeft + plotW / 2) + "' y='" + (marginTop + plotH + 40)
                        + "' text-anchor='middle' style='font-size:12px;color:#333'>" + var + "</text>");

                // title
                svg.append("<text x='" + (marginLeft + plotW / 2)
                        + "' y='14' text-anchor='middle' style='font-size:14px;font-weight:bold'>Benchmark sweep: "
                        + var + "</text>");

                // y grid and labels
                int yTicks = 5;
                for (int t = 0; t <= yTicks; t++) {
                    double frac = t / (double) yTicks;
                    int y = marginTop + (int) ((1 - frac) * plotH);
                    double val = frac * max;
                    svg.append("<line x1='" + marginLeft + "' y1='" + y + "' x2='" + (marginLeft + plotW) + "' y2='" + y
                            + "' stroke='#eee'/>");
                    svg.append("<text x='" + (marginLeft - 8) + "' y='" + (y + 4) + "' text-anchor='end'>"
                            + String.format("%.2f", val) + "</text>");
                }

                String[] colors = new String[] { "#4CAF50", "#2196F3", "#FF9800", "#9C27B0", "#F44336" };
                // draw series
                for (int implIdx = 0; implIdx < impls.size(); implIdx++) {
                    StringBuilder path = new StringBuilder();
                    for (int xi = 0; xi < nX; xi++) {
                        double d = means[xi][implIdx];
                        int x = marginLeft + (int) ((xi / (double) denomX) * plotW);
                        int y = marginTop + (int) ((1 - (d / max)) * plotH);
                        if (xi == 0)
                            path.append("M " + x + " " + y);
                        else
                            path.append(" L " + x + " " + y);
                    }
                    svg.append("<path d='" + path.toString() + "' fill='none' stroke='"
                            + colors[implIdx % colors.length] + "' stroke-width='2'/>");
                    // draw points
                    for (int xi = 0; xi < nX; xi++) {
                        double d = means[xi][implIdx];
                        int x = marginLeft + (int) ((xi / (double) denomX) * plotW);
                        int y = marginTop + (int) ((1 - (d / max)) * plotH);
                        svg.append("<circle cx='" + x + "' cy='" + y + "' r='3' fill='"
                                + colors[implIdx % colors.length] + "'/>");
                        // annotate point with value
                        svg.append("<text x='" + (x + 6) + "' y='" + (y + 4) + "' style='font-size:10px'>"
                                + String.format("%.2f", d) + "</text>");
                    }
                }

                // legend
                int lx = marginLeft + plotW + 10;
                int ly = marginTop + 10;
                for (int implIdx = 0; implIdx < impls.size(); implIdx++) {
                    String implLabel = impls.get(implIdx).split("\\n")[0].trim();
                    if (implLabel.length() > 40)
                        implLabel = implLabel.substring(0, 37) + "...";
                    svg.append("<rect x='" + lx + "' y='" + (ly + implIdx * 18) + "' width='12' height='12' fill='"
                            + colors[implIdx % colors.length] + "'/>");
                    svg.append("<text x='" + (lx + 18) + "' y='" + (ly + implIdx * 18 + 10) + "'>" + implLabel
                            + "</text>");
                }

                svg.append("<text x='" + (marginLeft + plotW / 2) + "' y='" + (height - 10)
                        + "' text-anchor='middle' style='font-size:11px;color:#666'>averaged over " + iterations
                        + " iterations (warmup=" + warmup + ")</text>");
                svg.append("</svg>");

                Display.display(svg.toString(), "image/svg+xml");
            } catch (Exception e) {
                System.out.println("Failed to render sweep chart: " + e.getMessage());
            }

            return;
        }

        List<List<Long>> results = new ArrayList<>();
        for (String impl : impls) {
            // warmup
            for (int w = 0; w < warmup; w++) {
                IJava.getKernelInstance().evalRaw(impl);
            }
            List<Long> times = new ArrayList<>();
            for (int i = 0; i < iterations; i++) {
                long start = System.nanoTime();
                IJava.getKernelInstance().evalRaw(impl);
                long end = System.nanoTime();
                times.add(end - start);
            }
            results.add(times);
        }

        // Print comparative table
        System.out.println("Benchmark results (nanoseconds):");
        for (int i = 0; i < impls.size(); i++) {
            List<Long> t = results.get(i);
            long sum = t.stream().mapToLong(Long::longValue).sum();
            List<Long> sorted = t.stream().sorted().collect(Collectors.toList());
            long median = sorted.get(sorted.size() / 2);
            System.out.printf("Impl %d: mean=%d median=%d samples=%s%n", i, sum / t.size(), median, t);
        }

        // Optionally render a simple SVG comparison chart
        boolean doChart = args.contains("--chart") || args.contains("--diagram") || "true".equals(opts.get("chart"));
        if (doChart) {
            try {
                // compute mean in milliseconds
                List<Double> meansMs = new ArrayList<>();
                double max = 0.0;
                for (List<Long> t : results) {
                    double meanNs = t.stream().mapToLong(Long::longValue).sum() / (double) t.size();
                    double meanMs = meanNs / 1e6;
                    meansMs.add(meanMs);
                    if (meanMs > max)
                        max = meanMs;
                }

                int width = 640;
                int leftLabel = 160;
                int barArea = width - leftLabel - 40;
                int barH = 36;
                int gap = 18;
                int height = (barH + gap) * meansMs.size() + 40;

                StringBuilder svg = new StringBuilder();
                svg.append("<?xml version='1.0' encoding='UTF-8'?>");
                svg.append("<svg xmlns='http://www.w3.org/2000/svg' width='" + width + "' height='" + height + "'>");
                svg.append("<style>text{font-family:Arial,Helvetica,sans-serif;font-size:12px}</style>");

                int y = 20;
                String[] colors = new String[] { "#4CAF50", "#2196F3", "#FF9800", "#9C27B0", "#F44336" };
                for (int i = 0; i < meansMs.size(); i++) {
                    double m = meansMs.get(i);
                    int w = (int) ((max == 0) ? 0 : (m / max) * barArea);
                    String label = "Impl " + i;
                    svg.append("<text x='10' y='" + (y + barH / 2 + 5) + "'>" + label + "</text>");
                    svg.append("<rect x='" + leftLabel + "' y='" + y + "' width='" + w + "' height='" + barH
                            + "' fill='" + colors[i % colors.length] + "' rx='4'/>");
                    svg.append("<text x='" + (leftLabel + w + 8) + "' y='" + (y + barH / 2 + 5) + "'>"
                            + String.format("%.3f ms", m) + "</text>");
                    y += barH + gap;
                }

                svg.append("<text x='10' y='" + (height - 10) + "' style='font-size:11px;color:#666'>averaged over "
                        + iterations + " iterations (warmup=" + warmup + ")</text>");
                svg.append("</svg>");

                Display.display(svg.toString(), "image/svg+xml");
            } catch (Exception e) {
                System.out.println("Failed to render chart: " + e.getMessage());
            }
        }
    }
}
