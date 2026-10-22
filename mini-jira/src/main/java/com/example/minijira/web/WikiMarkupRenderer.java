package com.example.minijira.web;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WikiMarkupRenderer {

    private static final Pattern HEADING = Pattern.compile("^h([1-6])\\.\\s+(.*)$");
    private static final Pattern CODE_START = Pattern.compile("^\\{(code(?::[\\w+#-]+)?|noformat)}\\s*$");
    private static final Pattern BOLD = Pattern.compile("(?<![\\w*])\\*(?!\\s)([^*\\n]+?)(?<!\\s)\\*(?![\\w*])");
    private static final Pattern MONO = Pattern.compile("\\{\\{(.+?)}}");
    private static final Pattern LINK = Pattern.compile("\\[(?:([^|\\]]+)\\|)?((?:https?://)[^\\]\\s]+)]");

    private WikiMarkupRenderer() {
    }

    public static String toHtml(String markup) {
        if (markup == null || markup.isBlank()) {
            return "";
        }
        var html = new StringBuilder();
        String[] lines = markup.replace("\r\n", "\n").split("\n", -1);
        String openList = null;
        StringBuilder paragraph = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.strip();

            Matcher codeStart = CODE_START.matcher(trimmed);
            if (codeStart.matches()) {
                flushParagraph(html, paragraph);
                openList = closeList(html, openList);
                String terminator = codeStart.group(1).startsWith("code") ? "{code}" : "{noformat}";
                var code = new StringBuilder();
                while (++i < lines.length && !lines[i].strip().equals(terminator)) {
                    code.append(lines[i]).append('\n');
                }
                html.append("<pre><code>").append(escape(code.toString())).append("</code></pre>");
                continue;
            }

            Matcher heading = HEADING.matcher(trimmed);
            if (heading.matches()) {
                flushParagraph(html, paragraph);
                openList = closeList(html, openList);
                html.append("<h").append(heading.group(1)).append('>')
                        .append(inline(heading.group(2)))
                        .append("</h").append(heading.group(1)).append('>');
                continue;
            }

            String listType = trimmed.startsWith("* ") ? "ul" : trimmed.startsWith("# ") ? "ol" : null;
            if (listType != null) {
                flushParagraph(html, paragraph);
                if (!listType.equals(openList)) {
                    closeList(html, openList);
                    html.append('<').append(listType).append('>');
                    openList = listType;
                }
                html.append("<li>").append(inline(trimmed.substring(2))).append("</li>");
                continue;
            }

            openList = closeList(html, openList);
            if (trimmed.isEmpty()) {
                flushParagraph(html, paragraph);
            } else {
                if (!paragraph.isEmpty()) {
                    paragraph.append("<br/>");
                }
                paragraph.append(inline(trimmed));
            }
        }
        flushParagraph(html, paragraph);
        closeList(html, openList);
        return html.toString();
    }

    static String inline(String text) {
        String escaped = escape(text);
        escaped = MONO.matcher(escaped).replaceAll("<code>$1</code>");
        escaped = LINK.matcher(escaped).replaceAll(match -> {
            String label = match.group(1) != null ? match.group(1) : match.group(2);
            return Matcher.quoteReplacement("<a href=\"" + match.group(2) + "\" target=\"_blank\" rel=\"noopener\">"
                    + label + "</a>");
        });
        escaped = BOLD.matcher(escaped).replaceAll("<strong>$1</strong>");
        return escaped;
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static void flushParagraph(StringBuilder html, StringBuilder paragraph) {
        if (!paragraph.isEmpty()) {
            html.append("<p>").append(paragraph).append("</p>");
            paragraph.setLength(0);
        }
    }

    private static String closeList(StringBuilder html, String openList) {
        if (openList != null) {
            html.append("</").append(openList).append('>');
        }
        return null;
    }
}
