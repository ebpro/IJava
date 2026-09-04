package io.github.spencerpark.ijava.magics;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class OptionUtilsTest {

    @Test
    public void parseOptionsMapsHelpFormatsAndValues() {
        Map<String, String> opts = OptionUtils.parseOptions(
                List.of("--help", "--raw", "--src=one", "--root", "two", "key=value", "index=2"));

        Assert.assertTrue(opts.containsKey("--help"));
        Assert.assertTrue(opts.containsKey("-h"));
        Assert.assertEquals("raw", opts.get("format"));
        Assert.assertEquals("two", opts.get("src"));
        Assert.assertEquals("value", opts.get("key"));
        Assert.assertEquals("2", opts.get("selectIndex"));
    }

    @Test
    public void parseOptionsKeepsFencedFormat() {
        Map<String, String> opts = OptionUtils.parseOptions(List.of("--fenced"));
        Assert.assertEquals("fenced", opts.get("format"));
    }

    @Test
    public void positionalArgsFiltersKnownOptionsAndKeyValuePairs() {
        List<String> positional = OptionUtils.positionalArgs(
                List.of("com.example.Foo", "bar", "--raw", "--src=src", "key=value"));

        Assert.assertEquals(List.of("com.example.Foo", "bar"), positional);
    }
}
