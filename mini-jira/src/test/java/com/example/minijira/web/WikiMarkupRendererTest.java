package com.example.minijira.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WikiMarkupRendererTest {

    @Test
    void rendersAgentCommentMarkup() {
        String html = WikiMarkupRenderer.toHtml("""
                h3. 🤖 AI Bugfix Agent
                *Summary:* NPE in {{OrderService}}
                *Pull request:* [https://github.com/acme/shop/pull/3]
                * first
                * second
                {code:java}
                if (a < b && c) { return; }
                {code}
                """);

        assertThat(html)
                .contains("<h3>🤖 AI Bugfix Agent</h3>")
                .contains("<strong>Summary:</strong> NPE in <code>OrderService</code>")
                .contains("<a href=\"https://github.com/acme/shop/pull/3\"")
                .contains("<ul><li>first</li><li>second</li></ul>")
                .contains("<pre><code>if (a &lt; b &amp;&amp; c) { return; }\n</code></pre>");
    }

    @Test
    void escapesHtmlAndRejectsNonHttpLinks() {
        String html = WikiMarkupRenderer.toHtml("<script>alert(1)</script> [x|javascript:alert(1)]");

        assertThat(html).doesNotContain("<script>").doesNotContain("href=\"javascript");
    }

    @Test
    void keepsNoformatBlocksVerbatim() {
        String html = WikiMarkupRenderer.toHtml("{noformat}\n\tat com.acme.A.b(A.java:1)\n{noformat}");

        assertThat(html).isEqualTo("<pre><code>\tat com.acme.A.b(A.java:1)\n</code></pre>");
    }
}
