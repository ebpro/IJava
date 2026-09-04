package io.github.spencerpark.ijava.magics;

import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MavenResolverTest {

    @Test
    public void addMavenDependenciesHelpPrintsUsage() {
        try (CapturedOutput output = new CapturedOutput()) {
            new MavenResolver(path -> { }).addMavenDependencies(List.of("--help"));
            Assert.assertTrue(output.text().contains("%maven"));
            Assert.assertTrue(output.text().contains("Usage:"));
        }
    }

    @Test
    public void addJarsToClasspathDelegatesToConsumer() {
        List<String> added = new ArrayList<>();
        new MavenResolver(added::add).addJarsToClasspath(List.of("a.jar", "b.jar"));
        Assert.assertEquals(List.of("a.jar", "b.jar"), added);
    }

    @Test
    public void addMavenRepoRegistersRepository() throws Exception {
        MavenResolver resolver = new MavenResolver(path -> { });
        int before = repositoryCount(resolver);

        resolver.addMavenRepo(List.of("test-repo", "https://example.com/maven2/"));

        Assert.assertEquals(before + 1, repositoryCount(resolver));
        Assert.assertEquals("https://example.com/maven2/", lastRepositoryUrl(resolver));
    }

    @Test(expected = IllegalArgumentException.class)
    public void loadFromPomEmptyArgsThrows() {
        new MavenResolver(path -> { }).loadFromPOM(List.of());
    }

    @Test
    public void loadFromPomHelpWithPathPrintsHelp() {
        try (CapturedOutput output = new CapturedOutput()) {
            new MavenResolver(path -> { }).loadFromPOM(List.of("-h", "pom.xml"));
            Assert.assertTrue(output.text().contains("## %pom"));
            Assert.assertTrue(output.text().contains("Usage:"));
        }
    }

    @Test
    public void loadFromPomMissingFileThrows() {
        Path missing = Path.of("build", "tmp", "missing-pom.xml");
        try {
            new MavenResolver(path -> { }).loadFromPOM(List.of(missing.toString()));
            Assert.fail("Expected RuntimeException");
        } catch (RuntimeException expected) {
        }
    }

    private static int repositoryCount(MavenResolver resolver) throws Exception {
        return repositories(resolver).size();
    }

    private static String lastRepositoryUrl(MavenResolver resolver) throws Exception {
        List<RemoteRepository> repos = repositories(resolver);
        return repos.get(repos.size() - 1).getUrl();
    }

    @SuppressWarnings("unchecked")
    private static List<RemoteRepository> repositories(MavenResolver resolver) throws Exception {
        Field field = MavenResolver.class.getDeclaredField("remoteRepos");
        field.setAccessible(true);
        return (List<RemoteRepository>) field.get(resolver);
    }
}
