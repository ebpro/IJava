package io.github.spencerpark.ijava.magics;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GitMermaidMagicsTest {
    private CapturingKernels.CapturingKernel kernel;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue(gitAvailable());
        kernel = CapturingKernels.install();
        kernel.reset();
    }

    @After
    public void tearDown() throws Exception {
        CapturingKernels.uninstall();
    }

    @Test
    public void generatesFlowchartFromTemporaryRepository() throws Exception {
        Path repo = Files.createTempDirectory(Path.of("build", "tmp"), "git-mermaid");
        runGit(repo, "init");
        runGit(repo, "checkout", "-b", "main");
        runGit(repo, "config", "user.name", "Test User");
        runGit(repo, "config", "user.email", "test@example.com");
        Files.writeString(repo.resolve("file.txt"), "test");
        runGit(repo, "add", "file.txt");
        runGit(repo, "-c", "commit.gpgsign=false", "commit", "-m", "first commit");

        new GitMermaidMagics().gitGraphMermaid(List.of("--repo=" + repo.toString()));

        String output = String.join("\n", kernel.displayedTexts());
        Assert.assertTrue(output.contains("flowchart TD"));
        Assert.assertTrue(output.contains("first commit"));
    }

    private static boolean gitAvailable() {
        try {
            Process process = new ProcessBuilder("git", "--version").redirectErrorStream(true).start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static void runGit(Path repo, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", repo.toString()));
        command.addAll(List.of(args));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
        processBuilder.environment().put("GIT_CONFIG_SYSTEM", "/dev/null");

        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0)
            throw new IllegalStateException(output);
    }
}
