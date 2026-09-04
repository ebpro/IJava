/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2025 ebpro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.spencerpark.ijava.magics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ShellMagics {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    /**
     * Gracefully shutdown the shared executor service used for stream gobbling.
     * Safe to call multiple times.
     */
    public static void shutdownExecutor() {
        EXECUTOR.shutdown();
        try {
            EXECUTOR.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Return whether the shared executor has been shutdown.
     */
    public static boolean isExecutorShutdown() {
        return EXECUTOR.isShutdown();
    }

    private static class StreamGobbler implements Runnable {
        private InputStream inputStream;
        private Consumer<String> consumer;

        public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
                    .forEach(consumer);
        }
    }

    @CellMagic("shell")
    public void shell(List<String> args, String body) throws InterruptedException, IOException {
        Map<String, String> opts = OptionUtils.parseOptions(args);

        // Show help if requested
        if (opts.containsKey("--help") || opts.containsKey("-h")) {
            System.out.println("""
                    ## %%shell - Execute shell commands

                    **Usage:** `%%shell [--shell=SHELL] [--timeout=SECONDS]`

                    **Options:**
                    - `--shell=SHELL` : Shell to use (default: zsh, or $SHELL environment variable)
                    - `--timeout=SECONDS` : Maximum execution time in seconds (default: 180)
                    - `--help, -h` : Show this help message

                    **Examples:**
                    ```
                    %%shell
                    ls -la
                    ```

                    ```
                    %%shell --shell=bash
                    echo "Using bash"
                    ```

                    ```
                    %%shell --timeout=60
                    long-running-command
                    ```
                    """);
            return;
        }

        // Determine shell to use
        String shell = opts.getOrDefault("--shell", System.getenv("SHELL"));
        if (shell == null || shell.isEmpty()) {
            shell = "zsh";
        }

        // Get timeout (default 3 minutes)
        long timeout = 180;
        if (opts.containsKey("--timeout")) {
            try {
                timeout = Long.parseLong(opts.get("--timeout"));
            } catch (NumberFormatException e) {
                log.warn("Invalid timeout value, using default: 180 seconds");
            }
        }

        log.debug("Running shell command with {}: {}", shell, body);

        String[] commands = { shell, "-c", body };
        Process process;
        try {
            process = new ProcessBuilder()
                    .command(commands).start();
            StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream(), System.out::println);
            StreamGobbler streamGobblerErr = new StreamGobbler(process.getErrorStream(), System.err::println);
            Future<?> fOut = EXECUTOR.submit(streamGobbler);
            Future<?> fErr = EXECUTOR.submit(streamGobblerErr);

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Command timed out after " + timeout + " seconds");
            }
            // Wait briefly for gobblers to flush remaining output
            try {
                fOut.get(1, TimeUnit.SECONDS);
                fErr.get(1, TimeUnit.SECONDS);
            } catch (ExecutionException | java.util.concurrent.TimeoutException e) {
                // ignore - best-effort
            }
        } catch (IOException e) {
            log.error("Error while running shell command", e);
            throw e;
        } catch (InterruptedException e) {
            log.error("Error while waiting for process to finish", e);
            throw e;
        }
    }

    // Deprecated wrappers removed: use %%shell instead
}
