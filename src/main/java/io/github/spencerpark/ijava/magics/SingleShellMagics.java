package io.github.spencerpark.ijava.magics;

import java.io.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;

public class SingleShellMagics {

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

    private Process shellProcess;
    private BufferedWriter shellWriter;
    private ExecutorService executorService;

    public SingleShellMagics() {
        try {
            shellProcess = new ProcessBuilder("zsh").start();
            shellWriter = new BufferedWriter(new OutputStreamWriter(shellProcess.getOutputStream()));
            executorService = Executors.newFixedThreadPool(2);
            executorService.submit(new StreamGobbler(shellProcess.getInputStream(), System.out::println));
            executorService.submit(new StreamGobbler(shellProcess.getErrorStream(), System.err::println));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @CellMagic("singleshell")
    public void shell(List<String> args, String body) {
        try {
            shellWriter.write(body);
            shellWriter.newLine();
            shellWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            shellWriter.close();
            shellProcess.destroy();
            executorService.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
