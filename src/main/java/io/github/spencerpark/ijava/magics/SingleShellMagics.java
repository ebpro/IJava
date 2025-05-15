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
        String shell = System.getenv("SHELL");
        if (shell == null || shell.isEmpty()) {
            shell = "/bin/zsh";
        }
        
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

    @CellMagic("commonshell")
    public String commonshell(List<String> args, String body) throws IOException, InterruptedException {
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
        if (args.isEmpty()) return "No command provided";
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
