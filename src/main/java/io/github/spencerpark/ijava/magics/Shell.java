package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class Shell {


    @CellMagic("myshell")
    public void shell(List<String> args, String body) throws IOException {
        if (args.isEmpty()) return;

        log.info("Running shell command: {}", body);
        System.out.println("--> Running shell command: "+body);

        String[] commands = { "zsh", "-c", body };
        System.out.println("----> Running shell command: "+Arrays.toString(commands));        Process proc = Runtime.getRuntime().exec(commands);

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
    }
}