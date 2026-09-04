package io.github.spencerpark.ijava.magics;

import java.io.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import io.github.spencerpark.jupyter.kernel.magic.registry.LineMagic;

public class SingleShellMagics implements AutoCloseable {
    private record StreamGobbler(InputStream inputStream, Consumer<String> consumer) implements Runnable {

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                reader.lines().forEach(consumer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private final Process shellProcess;
    private final BufferedWriter shellWriter;
    private final ExecutorService executorService;
    private final StringBuffer outputBuffer;
    private final long defaultTimeout = 30;
    private final TimeUnit defaultTimeoutUnit = TimeUnit.SECONDS;

    public SingleShellMagics() throws IOException {
        String shell = resolveShell();

        outputBuffer = new StringBuffer();
        shellProcess = new ProcessBuilder(shell).start();
        shellWriter = new BufferedWriter(new OutputStreamWriter(shellProcess.getOutputStream()));
        executorService = Executors.newFixedThreadPool(2);
        
        executorService.submit(new StreamGobbler(shellProcess.getInputStream(), 
            s -> {
                synchronized(outputBuffer) {
                    outputBuffer.append(s).append("\n");
                    System.out.println(s);
                }
            }));
        executorService.submit(new StreamGobbler(shellProcess.getErrorStream(), System.err::println));
    }

    private static String resolveShell() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String comspec = System.getenv("ComSpec");
            return comspec != null && !comspec.isBlank() ? comspec : "cmd.exe";
        }

        String[] candidates = {System.getenv("SHELL"), "/bin/zsh", "/bin/bash", "/bin/sh"};
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && new File(candidate).canExecute()) {
                return candidate;
            }
        }

        return "/bin/sh";
    }

    @CellMagic("commonshell")
    public String commonshell(List<String> args, String body) throws IOException, InterruptedException {
        if (args == null) args = List.of();
        if (args.contains("--help") || args.contains("-h")) {
            return "## %%commonshell - Run shell in persistent session\n\nUsage: %%commonshell [--help]\n\nThe cell body is run in a persistent shell process started by the kernel.";
        }

        synchronized(outputBuffer) {
            outputBuffer.setLength(0);
        }

        shellWriter.write(body);
        shellWriter.newLine();
        shellWriter.flush();

        // Give the command some time to execute and produce output
        Thread.sleep(100);
        
        synchronized(outputBuffer) {
            return outputBuffer.toString();
        }
    }

    @LineMagic("commonshellcmd")
    public String commonshellcmd(List<String> args) throws IOException, InterruptedException {
        if (args == null || args.isEmpty()) return "No command provided";
        if (args.size() == 1 && (args.get(0).equals("--help") || args.get(0).equals("-h")))
            return "%commonshellcmd <command...> - run a command in the persistent shell session";
        return commonshell(args, String.join(" ", args));
    }

    @Override
    public void close() {
        try {
            shellWriter.close();
            shellProcess.destroy();
            if (!shellProcess.waitFor(5, TimeUnit.SECONDS)) {
                shellProcess.destroyForcibly();
            }
            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to clean up shell resources", e);
        }
    }
}
