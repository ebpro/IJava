package io.github.spencerpark.jupyter.kernel.display.common;

import io.github.spencerpark.jupyter.kernel.display.DisplayData;
import io.github.spencerpark.jupyter.kernel.display.Renderer;
import io.github.spencerpark.jupyter.kernel.display.mime.MIMEType;
import org.junit.Before;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class UrlTest {

    private Renderer renderer;

    @Before
    public void setUp() {
        renderer = new Renderer();
        Url.registerAll(renderer);
    }

    @Test
    public void rendersWellFormedAnchorByDefault() throws MalformedURLException {
        URL url = URI.create("https://example.com/?a=1&b=2").toURL();
        DisplayData data = renderer.renderAs(url, "text/html");
        String html = (String) data.getData(MIMEType.TEXT_HTML);
        assertNotNull(html);
        assertEquals("<a href=\"https://example.com/?a=1&amp;b=2\" target=\"_blank\">https://example.com/?a=1&amp;b=2</a>", html);
    }

    @Test
    public void rendersPlainUrlAsText() throws MalformedURLException {
        URL url = URI.create("https://example.com/?a=1&b=2").toURL();
        DisplayData data = renderer.renderAs(url, "text/plain");
        assertEquals("https://example.com/?a=1&b=2", data.getData(MIMEType.TEXT_PLAIN));
    }

    @Test
    public void rendersCustomVoidTagWithoutClosingTag() throws MalformedURLException {
        URL url = URI.create("https://example.com/image.png").toURL();
        Map<String, Object> params = Map.of(Url.HTML_TAG_KEY, "img", Url.HTML_SRC_ATTR_KEY, "src");
        DisplayData data = renderer.renderAs(url, params, "text/html");
        assertEquals("<img src=\"https://example.com/image.png\" />", (String) data.getData(MIMEType.TEXT_HTML));
    }

    @Test
    public void sanitizesUnsafeHtmlNames() throws MalformedURLException {
        URL url = URI.create("https://example.com/").toURL();
        Map<String, Object> params = Map.of(Url.HTML_TAG_KEY, "<script>", Url.HTML_SRC_ATTR_KEY, "onerror=alert(1)");
        DisplayData data = renderer.renderAs(url, params, "text/html");
        assertEquals("<a href=\"https://example.com/\" target=\"_blank\">https://example.com/</a>", (String) data.getData(MIMEType.TEXT_HTML));
    }
}
