package app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependency-free Markdown → TipTap/ProseMirror {@code doc} converter.
 *
 * <p>Produces a node of the shape {@code {"type":"doc","content":[...]}} suitable for
 * sending to the Pages / Thoughts / Daily-Note backends (which all store TipTap JSON).
 * It is a deliberately small, line-oriented parser supporting headings, bullet/ordered
 * lists, paragraphs, and a single level of inline bold/italic/code marks.
 *
 * <p><b>Never throws.</b> Any unexpected input results in a best-effort doc (the raw
 * text as a single paragraph, or an empty paragraph) so callers can rely on always
 * getting a valid TipTap document.
 */
public final class MarkdownToTipTap {

    private static final ObjectMapper M = new ObjectMapper();
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern ULI = Pattern.compile("^[-*]\\s+(.*)$");
    private static final Pattern OLI = Pattern.compile("^\\d+\\.\\s+(.*)$");

    private MarkdownToTipTap() {}

    /** Convert Markdown to a TipTap {@code doc} node. Never throws. */
    public static ObjectNode toDoc(String markdown) {
        ObjectNode doc = M.createObjectNode();
        doc.put("type", "doc");
        ArrayNode content = doc.putArray("content");
        try {
            buildBlocks(markdown == null ? "" : markdown, content);
        } catch (Exception e) {
            content.removeAll();
            content.add(paragraph(markdown == null ? "" : markdown));
        }
        if (content.isEmpty()) content.add(emptyParagraph());
        return doc;
    }

    // ── Block-level parsing ──────────────────────────────────────────────────

    private static void buildBlocks(String markdown, ArrayNode content) {
        String[] lines = markdown.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);

        StringBuilder paragraphBuf = new StringBuilder();
        ArrayNode bulletItems = null;
        ArrayNode orderedItems = null;

        for (String line : lines) {
            String trimmed = line.trim();

            // Blank line: close any open block run.
            if (trimmed.isEmpty()) {
                flushParagraph(content, paragraphBuf);
                bulletItems = flushBullet(content, bulletItems);
                orderedItems = flushOrdered(content, orderedItems);
                continue;
            }

            Matcher h = HEADING.matcher(trimmed);
            if (h.matches()) {
                flushParagraph(content, paragraphBuf);
                bulletItems = flushBullet(content, bulletItems);
                orderedItems = flushOrdered(content, orderedItems);
                int level = h.group(1).length();
                content.add(heading(level, h.group(2)));
                continue;
            }

            Matcher u = ULI.matcher(trimmed);
            if (u.matches()) {
                flushParagraph(content, paragraphBuf);
                orderedItems = flushOrdered(content, orderedItems);
                if (bulletItems == null) bulletItems = M.createArrayNode();
                bulletItems.add(listItem(u.group(1)));
                continue;
            }

            Matcher o = OLI.matcher(trimmed);
            if (o.matches()) {
                flushParagraph(content, paragraphBuf);
                bulletItems = flushBullet(content, bulletItems);
                if (orderedItems == null) orderedItems = M.createArrayNode();
                orderedItems.add(listItem(o.group(1)));
                continue;
            }

            // Plain text line: accumulate into the current paragraph.
            bulletItems = flushBullet(content, bulletItems);
            orderedItems = flushOrdered(content, orderedItems);
            if (paragraphBuf.length() > 0) paragraphBuf.append(' ');
            paragraphBuf.append(trimmed);
        }

        // Flush any trailing block.
        flushParagraph(content, paragraphBuf);
        flushBullet(content, bulletItems);
        flushOrdered(content, orderedItems);
    }

    private static void flushParagraph(ArrayNode content, StringBuilder buf) {
        if (buf.length() == 0) return;
        content.add(paragraph(buf.toString()));
        buf.setLength(0);
    }

    private static ArrayNode flushBullet(ArrayNode content, ArrayNode items) {
        if (items == null || items.isEmpty()) return null;
        ObjectNode list = M.createObjectNode();
        list.put("type", "bulletList");
        list.set("content", items);
        content.add(list);
        return null;
    }

    private static ArrayNode flushOrdered(ArrayNode content, ArrayNode items) {
        if (items == null || items.isEmpty()) return null;
        ObjectNode list = M.createObjectNode();
        list.put("type", "orderedList");
        list.putObject("attrs").put("start", 1);
        list.set("content", items);
        content.add(list);
        return null;
    }

    // ── Node builders ────────────────────────────────────────────────────────

    private static ObjectNode heading(int level, String text) {
        ObjectNode node = M.createObjectNode();
        node.put("type", "heading");
        node.putObject("attrs").put("level", level);
        ArrayNode inline = inline(text);
        if (!inline.isEmpty()) node.set("content", inline);
        return node;
    }

    private static ObjectNode paragraph(String text) {
        ObjectNode node = M.createObjectNode();
        node.put("type", "paragraph");
        ArrayNode inline = inline(text);
        if (!inline.isEmpty()) node.set("content", inline);
        return node;
    }

    private static ObjectNode emptyParagraph() {
        ObjectNode node = M.createObjectNode();
        node.put("type", "paragraph");
        return node;
    }

    private static ObjectNode listItem(String text) {
        ObjectNode item = M.createObjectNode();
        item.put("type", "listItem");
        ArrayNode children = item.putArray("content");
        children.add(paragraph(text));
        return item;
    }

    // ── Inline parsing (bold / italic / code) ─────────────────────────────────

    /** Parse a single line of inline Markdown into an array of text nodes with marks. */
    private static ArrayNode inline(String text) {
        ArrayNode out = M.createArrayNode();
        if (text == null || text.isEmpty()) return out;

        int i = 0;
        int n = text.length();
        StringBuilder plain = new StringBuilder();

        while (i < n) {
            char c = text.charAt(i);

            // Inline code: `x`
            if (c == '`') {
                int close = text.indexOf('`', i + 1);
                if (close > i) {
                    flushPlain(out, plain);
                    addMarked(out, text.substring(i + 1, close), "code");
                    i = close + 1;
                    continue;
                }
            }

            // Bold: **x**
            if (c == '*' && i + 1 < n && text.charAt(i + 1) == '*') {
                int close = text.indexOf("**", i + 2);
                if (close > i + 1) {
                    flushPlain(out, plain);
                    addMarked(out, text.substring(i + 2, close), "bold");
                    i = close + 2;
                    continue;
                }
            }

            // Italic: *x*
            if (c == '*') {
                int close = text.indexOf('*', i + 1);
                if (close > i) {
                    flushPlain(out, plain);
                    addMarked(out, text.substring(i + 1, close), "italic");
                    i = close + 1;
                    continue;
                }
            }

            // Italic: _x_
            if (c == '_') {
                int close = text.indexOf('_', i + 1);
                if (close > i) {
                    flushPlain(out, plain);
                    addMarked(out, text.substring(i + 1, close), "italic");
                    i = close + 1;
                    continue;
                }
            }

            // Literal character (includes unmatched delimiters).
            plain.append(c);
            i++;
        }

        flushPlain(out, plain);
        return out;
    }

    private static void flushPlain(ArrayNode out, StringBuilder plain) {
        if (plain.length() == 0) return;
        ObjectNode node = M.createObjectNode();
        node.put("type", "text");
        node.put("text", plain.toString());
        out.add(node);
        plain.setLength(0);
    }

    private static void addMarked(ArrayNode out, String text, String markType) {
        if (text == null || text.isEmpty()) return;
        ObjectNode node = M.createObjectNode();
        node.put("type", "text");
        node.put("text", text);
        ArrayNode marks = node.putArray("marks");
        marks.addObject().put("type", markType);
        out.add(node);
    }
}
