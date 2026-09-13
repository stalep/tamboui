/*
 * Copyright (c) 2026 TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.export.html;

import org.junit.jupiter.api.Test;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.export.Formats;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;

import static dev.tamboui.export.ExportRequest.export;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HtmlExporterTest {

    @Test
    void exportsHtmlWithNonEmbeddedStyles() {
        Buffer buffer = Buffer.empty(new Rect(0, 0, 8, 2));
        buffer.setString(0, 0, "Hello", Style.EMPTY.fg(Color.CYAN));
        buffer.setString(0, 1, "Bold", Style.EMPTY.bold().fg(Color.RED));

        String html = export(buffer).as(Formats.HTML).toString();
        // Default: styles in stylesheet (non-embedded), spans use classes
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("<pre"));
        assertTrue(html.contains("<code"));
        assertTrue(html.contains("Hello"));
        assertTrue(html.contains("Bold"));
        assertTrue(html.contains(".r1 {") || html.contains(".r2 {"), "stylesheet with class rules");
        assertTrue(html.contains("class=\"r1\"") || html.contains("class=\"r2\""), "spans use classes not inline style");
        assertFalse(html.contains("<span style="), "non-embedded must not use inline styles");
        // ASCII-only content must stay plain text: no inline-block wrappers, so box-drawing
        // borders still connect vertically in the <pre> (see #415).
        assertFalse(html.contains("display:inline-block"), "ASCII runs must not be wrapped in inline-block");
    }

    @Test
    void exportsHtmlWithEmbeddedInlineStyles() {
        Buffer buffer = Buffer.empty(new Rect(0, 0, 4, 1));
        buffer.setString(0, 0, "Hi", Style.EMPTY.bold());

        String html = export(buffer).as(Formats.HTML).options(o -> o.inlineStyles(true)).toString();

        assertTrue(html.contains("<span style="), "embedded: styles inlined in spans");
        assertTrue(html.contains("font-weight: bold"));
        assertTrue(html.contains("Hi"));
        assertFalse(html.contains("display:inline-block"), "ASCII runs must not be wrapped in inline-block");
        assertFalse(html.contains("class=\"r1\""), "embedded must not use stylesheet classes");
    }

    @Test
    void wideGlyphsArePinnedToTwoColumnsInlineStyles() {
        // Regression test for #415: each wide (CJK) glyph must be pinned to its 2-column
        // display width so following columns and borders stay aligned regardless of the
        // browser's font fallback. Only the wide glyphs are wrapped; ASCII stays plain so
        // box-drawing borders keep connecting vertically.
        Buffer buffer = Buffer.empty(new Rect(0, 0, 6, 1));
        buffer.setString(0, 0, "\u4e16\u754c|", Style.EMPTY.fg(Color.CYAN));

        String html = export(buffer).as(Formats.HTML).options(o -> o.inlineStyles(true)).toString();

        // Two wide glyphs, each pinned to 2ch; the 1-wide '|' border stays plain text.
        assertTrue(html.contains("display:inline-block;width:2ch"), "CJK glyph pinned to 2 columns");
        int wraps = html.split("display:inline-block", -1).length - 1;
        assertEquals(2, wraps, "only the two wide glyphs are wrapped, not the border");
    }

    @Test
    void wideGlyphsUseSharedClassInStylesheetMode() {
        Buffer buffer = Buffer.empty(new Rect(0, 0, 6, 1));
        buffer.setString(0, 0, "\u4e16\u754c|", Style.EMPTY.fg(Color.CYAN));

        String html = export(buffer).as(Formats.HTML).toString();

        assertTrue(html.contains(".cw2 {"), "stylesheet declares wide-glyph class");
        assertTrue(html.contains("class=\"cw2\""), "wide glyphs reference the shared class");
        assertTrue(html.contains("display:inline-block;width:2ch"), "wide-glyph class pins width to 2 columns");
    }
}
