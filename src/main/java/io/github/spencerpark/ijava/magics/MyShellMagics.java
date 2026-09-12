package io.github.spencerpark.ijava.magics;

// legacy file: no longer registers a magic
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Deprecated(forRemoval = true)
public class MyShellMagics {

    // No longer registers a magic. Use ShellMagics (%%shell) instead.
    public void myshell(List<String> args, String body) {
        if (args.isEmpty())
            return;

        log.info("Running shell command: {}", body);
        System.out.println("--> Running shell command: " + body);

        String zsh = resolveZsh();
        if (zsh == null) {
            log.error("No fixed zsh executable found in {}, refusing to run shell command", Arrays.toString(ZSH_CANDIDATES));
            return;
        }

        String[] commands = { zsh, "-c", body };
        System.out.println("----> Running shell command: " + Arrays.toString(commands));
        try {
            Process proc = Runtime.getRuntime().exec(commands);
            proc.waitFor(3, TimeUnit.MINUTES);

            try (InputStreamReader inputStreamReader = new InputStreamReader(proc.getInputStream());
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
                String s;
                while ((s = bufferedReader.readLine()) != null) {
                    System.out.println(s);
                }
            }

            try (InputStreamReader inputStreamReader = new InputStreamReader(proc.getErrorStream());
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
                String s;
                while ((s = bufferedReader.readLine()) != null) {
                    System.err.println(s);
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error while waiting for process to finish", e);
        } catch (IOException e) {
            log.error("Error while running shell command", e);

        }
    }

    static final String[] ZSH_CANDIDATES = { "/bin/zsh", "/usr/bin/zsh", "/usr/local/bin/zsh" };

    static String resolveZsh() {
        return resolveZsh(ZSH_CANDIDATES);
    }

    static String resolveZsh(String[] candidates) {
        for (String candidate : candidates) {
            File file = new File(candidate);
            if (file.isFile() && file.canExecute())
                return candidate;
        }
        return null;
    }
}
