package io.github.spencerpark.ijava.magics;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

/**
 * Tests for the package-private zsh resolution logic in {@link MyShellMagics}.
 * Only local temporary files are used; no shell process is started.
 */
@SuppressWarnings("deprecation")
public class MyShellMagicsTest {

    @Test
    public void resolveZshReturnsNullWhenNoCandidateExists() {
        String missing1 = "/definitely/missing/zsh-" + System.nanoTime();
        String missing2 = "/also/missing/zsh-" + System.nanoTime();
        Assert.assertNull(MyShellMagics.resolveZsh(new String[]{missing1, missing2}));
    }

    @Test
    public void resolveZshReturnsNullForEmptyCandidates() {
        Assert.assertNull(MyShellMagics.resolveZsh(new String[0]));
    }

    @Test
    public void resolveZshReturnsFirstExecutableCandidate() throws IOException {
        Path first = createExecutableTempFile("zsh-first-");
        Path second = createExecutableTempFile("zsh-second-");
        try {
            String missing = "/definitely/missing/zsh-" + System.nanoTime();
            Assert.assertEquals(first.toString(),
                    MyShellMagics.resolveZsh(new String[]{missing, first.toString(), second.toString()}));
            Assert.assertEquals(second.toString(),
                    MyShellMagics.resolveZsh(new String[]{second.toString(), first.toString()}));
        } finally {
            deleteQuietly(first);
            deleteQuietly(second);
        }
    }

    @Test
    public void resolveZshSkipsDirectories() throws IOException {
        Path directory = Files.createTempDirectory("zsh-dir-");
        Path executable = createExecutableTempFile("zsh-file-");
        try {
            Assert.assertEquals(executable.toString(),
                    MyShellMagics.resolveZsh(new String[]{directory.toString(), executable.toString()}));
        } finally {
            deleteQuietly(executable);
            deleteQuietly(directory);
        }
    }

    @Test
    public void resolveZshDefaultCandidatesResolveToKnownExecutableOrNull() {
        String resolved = MyShellMagics.resolveZsh();
        if (resolved == null) {
            return; // no zsh installed in this environment
        }
        Assert.assertTrue("Unexpected zsh candidate: " + resolved,
                List.of(MyShellMagics.ZSH_CANDIDATES).contains(resolved));
        Assert.assertTrue(Files.isExecutable(Path.of(resolved)));
    }

    private static Path createExecutableTempFile(String prefix) throws IOException {
        Path file = Files.createTempFile(prefix, ".sh");
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwx------"));
        }
        return file;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort cleanup in tests
        }
    }
}
