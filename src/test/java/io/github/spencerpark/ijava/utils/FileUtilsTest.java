package io.github.spencerpark.ijava.utils;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

public class FileUtilsTest {

    @Test
    public void createPrivateTempDirCreatesDirectory() throws Exception {
        Path dir = FileUtils.createPrivateTempDir("ijava-test-");
        try {
            Assert.assertTrue(Files.isDirectory(dir));
            Assert.assertEquals(privateTempBaseDir(), dir.getParent());
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                Set<PosixFilePermission> expected = EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE);
                Assert.assertEquals(expected, Files.getPosixFilePermissions(dir));
            }
        } finally {
            FileUtils.deleteRecursively(dir);
        }
    }

    @Test
    public void createPrivateTempDirCreatesUniqueDirectories() throws Exception {
        Path dir1 = FileUtils.createPrivateTempDir("ijava-test-");
        Path dir2 = FileUtils.createPrivateTempDir("ijava-test-");
        try {
            Assert.assertNotEquals(dir1, dir2);
            Assert.assertTrue(Files.isDirectory(dir1));
            Assert.assertTrue(Files.isDirectory(dir2));
            Assert.assertEquals(privateTempBaseDir(), dir1.getParent());
            Assert.assertEquals(privateTempBaseDir(), dir2.getParent());
        } finally {
            FileUtils.deleteRecursively(dir1);
            FileUtils.deleteRecursively(dir2);
        }
    }

    @Test
    public void privateTempBaseDirHasOwnerOnlyPermissions() throws Exception {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return;
        }
        FileUtils.createPrivateTempDir("ijava-test-");
        Set<PosixFilePermission> expected = EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE);
        Assert.assertEquals(expected, Files.getPosixFilePermissions(privateTempBaseDir()));
    }

    @Test
    public void deleteRecursivelyCleansUpPrivateTempDir() throws Exception {
        Path dir = FileUtils.createPrivateTempDir("ijava-cleanup-");
        try {
            Path sub = Files.createDirectory(dir.resolve("nested"));
            Path file = Files.createFile(dir.resolve("payload.txt"));
            Files.createFile(sub.resolve("inner.txt"));

            FileUtils.deleteRecursively(dir);

            Assert.assertFalse(Files.exists(dir));
            Assert.assertFalse(Files.exists(sub));
            Assert.assertFalse(Files.exists(file));
        } finally {
            FileUtils.deleteRecursively(dir);
        }
    }

    @Test
    public void createPrivateTempFileCreatesFileInDirectory() throws Exception {
        Path dir = FileUtils.createPrivateTempDir("ijava-test-");
        try {
            Path file = FileUtils.createPrivateTempFile(dir, "file-", ".tmp");
            Assert.assertTrue(Files.isRegularFile(file));
            Assert.assertEquals(dir, file.getParent());
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                Set<PosixFilePermission> expected = EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE);
                Assert.assertEquals(expected, Files.getPosixFilePermissions(file));
            }
        } finally {
            FileUtils.deleteRecursively(dir);
        }
    }

    @Test
    public void deleteRecursivelyRemovesNestedContent() throws Exception {
        Path dir = FileUtils.createPrivateTempDir("ijava-test-");
        try {
            Path sub = Files.createDirectory(dir.resolve("sub"));
            Path file = Files.createFile(dir.resolve("file.txt"));
            Files.createFile(sub.resolve("nested.txt"));

            FileUtils.deleteRecursively(dir);

            Assert.assertFalse(Files.exists(dir));
            Assert.assertFalse(Files.exists(sub));
            Assert.assertFalse(Files.exists(file));
        } finally {
            FileUtils.deleteRecursively(dir);
        }
    }

    @Test
    public void deleteRecursivelyToleratesMissingAndNullRoot() {
        FileUtils.deleteRecursively(null);
        FileUtils.deleteRecursively(Path.of("does-not-exist-" + System.nanoTime()));
    }

    private static Path privateTempBaseDir() {
        return Paths.get(System.getProperty("java.io.tmpdir"), "ijava-private");
    }
}
