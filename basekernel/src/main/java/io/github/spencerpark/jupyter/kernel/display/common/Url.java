package io.github.spencerpark.jupyter.kernel.display.common;

import io.github.spencerpark.jupyter.kernel.display.DisplayData;
import io.github.spencerpark.jupyter.kernel.display.RenderContext;
import io.github.spencerpark.jupyter.kernel.display.Renderer;
import io.github.spencerpark.jupyter.kernel.display.mime.MIMEType;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Url {
    public static String EMBED_KEY = "embed";
    public static String HTML_TAG_KEY = "url.html.tag";
    public static String HTML_SRC_ATTR_KEY = "url.html.src-attr";

    public static void registerAll(Renderer renderer) {
        renderer.createRegistration(java.net.URL.class)
                .supporting(MIMEType.ANY)
                .register(Url::renderUrl);
        renderer.createRegistration(java.net.URLConnection.class)
                .supporting(MIMEType.ANY)
                .register((conn, ctx) -> renderUrl(conn.getURL(), ctx));
    }

    public static void renderUrl(java.net.URL url, RenderContext context) {
        if (context.getParameterAsBoolean(EMBED_KEY, false)) {
            try {
                Object content = url.getContent();
                DisplayData rendered = context.getRenderer().render(content, context.getParams());
                context.getOutputContainer().assign(rendered);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            context.renderIfRequested(MIMEType.TEXT_HTML, () -> {
                String tag = context.getParameterAsString(HTML_TAG_KEY, "a");
                String srcAttr = context.getParameterAsString(HTML_SRC_ATTR_KEY, "href");
                return renderHTML(tag, srcAttr, url, Collections.emptyMap());
            });
        }
    }

    private static final Set<String> HTML_VOID_ELEMENTS = Set.of("area", "base", "br", "col", "embed", "hr",
            "img", "input", "link", "meta", "param", "source", "track", "wbr");

    private static String renderHTML(String tag, String srcAttr, java.net.URL url, Map<String, String> attrs) {
        String safeTag = sanitizeHtmlName(tag, "a");
        String safeSrcAttr = sanitizeHtmlName(srcAttr, "href");
        String externalForm = url.toExternalForm();
        StringBuilder html = new StringBuilder("<");
        html.append(safeTag);
        html.append(" ").append(safeSrcAttr).append("=\"").append(escapeHtml(externalForm)).append('"');
        if (safeTag.equals("a") && !attrs.containsKey("target"))
            html.append(" target=\"_blank\"");
        attrs.forEach((attr, val) -> {
            if (val != null) {
                String safeAttr = sanitizeHtmlName(attr, attr);
                html.append(" ").append(safeAttr).append("=\"").append(escapeHtml(val)).append('"');
            }
        });
        if (HTML_VOID_ELEMENTS.contains(safeTag.toLowerCase(Locale.ROOT))) {
            html.append(" />");
        } else {
            html.append(">").append(escapeHtml(externalForm)).append("</").append(safeTag).append('>');
        }
        return html.toString();
    }

    private static String sanitizeHtmlName(String value, String defaultValue) {
        if (value == null || !value.matches("[A-Za-z][A-Za-z0-9-]*"))
            return defaultValue;
        return value;
    }

    private static String escapeHtml(String value) {
        if (value == null)
            return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
