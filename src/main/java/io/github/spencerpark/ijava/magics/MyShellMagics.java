package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MyShellMagics {

    @CellMagic("myshell")
    public void myshell(List<String> args, String body) {
        if (args.isEmpty())
            return;

        log.info("Running shell command: {}", body);
        System.out.println("--> Running shell command: " + body);

        String[] commands = { "zsh", "-c", body };
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
            log.error("Error while waiting for process to finish", e);
            e.printStackTrace();
        } catch (IOException e) {
            log.error("Error while running shell command", e);

        }
    }
}