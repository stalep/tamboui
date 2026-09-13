/*
 * Copyright (c) 2026 TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.export.svg;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

final class SvgExporterTest {

    @Test
    void exportsSvgWithStylesAndBackgrounds() {
        Buffer buffer = Buffer.empty(new Rect(0, 0, 6, 2));
        buffer.setString(0, 0, "Hello!", Style.EMPTY.fg(Color.hex("#c5c8c6")));
        buffer.setString(0, 1, "AB", Style.EMPTY.onBlue().bold());
        buffer.setString(2, 1, "CD", Style.EMPTY.italic().underlined());

        String svg = export(buffer).as(Formats.SVG)
            .options(o -> o.title("Test").uniqueId("test"))
            .toString();

        // Basic structure
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("test-matrix"));
        assertTrue(svg.contains("clipPath id=\"test-line-0\""));
        assertTrue(svg.contains("clipPath id=\"test-line-1\""));

        // Style rules
        assertTrue(svg.contains("font-weight: bold"));
        assertTrue(svg.contains("font-style: italic"));
        assertTrue(svg.contains("text-decoration: underline"));

        // Background rect for the bold-on-blue run
        assertTrue(svg.contains("<rect") && svg.contains("shape-rendering=\"crispEdges\""));

        // Text nodes for non-space content
        assertTrue(svg.contains(">Hello!<") || svg.contains("Hello!"));
        assertTrue(svg.contains(">AB<") || svg.contains("AB"));
        assertTrue(svg.contains(">CD<") || svg.contains("CD"));
    }

    @Test
    void textSegmentsSnapToTheColumnGridForWideCharsAndEmoji() {
        // Regression test for #415: box-drawing borders must align with content rows even
        // when content contains wide (CJK) and emoji glyphs. Every <text> segment must sit
        // on the column grid: both its x offset and its textLength must be whole multiples
        // of the column width, so wide/emoji glyphs cannot drift neighbouring columns.
        Buffer buffer = Buffer.empty(new Rect(0, 0, 12, 3));
        buffer.setString(0, 0, "\u256d\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u256e", Style.EMPTY);
        // │世界AB🔮X│ : side bars + CJK (2 cols each) + ASCII + emoji (2 cols) = 12 columns
        buffer.setString(0, 1, "\u2502\u4e16\u754cAB\ud83d\udd2eX\u2502", Style.EMPTY);
        buffer.setString(0, 2, "\u2570\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u256f", Style.EMPTY);

        String svg = export(buffer).as(Formats.SVG).options(o -> o.uniqueId("align")).toString();

        // charWidth = fontSize (20) * fontAspectRatio (0.61) = 12.2 user units per column.
        double charWidth = 12.2;
        List<double[]> segments = textSegments(svg);
        assertFalse(segments.isEmpty(), "expected <text> segments in the export");
        for (double[] seg : segments) {
            double x = seg[0];
            double textLength = seg[1];
            assertEquals(0.0, remainderToGrid(x, charWidth), 0.01,
                "segment x must sit on the column grid: " + x);
            assertEquals(0.0, remainderToGrid(textLength, charWidth), 0.01,
                "segment textLength must be a whole number of columns: " + textLength);
        }
    }

    private static double remainderToGrid(double value, double unit) {
        double columns = value / unit;
        return Math.abs(columns - Math.rint(columns)) * unit;
    }

    private static List<double[]> textSegments(String svg) {
        List<double[]> segments = new ArrayList<>();
        Matcher matcher = Pattern.compile("<text[^>]*\\bx=\"([0-9.]+)\"[^>]*textLength=\"([0-9.]+)\"")
            .matcher(svg);
        while (matcher.find()) {
            segments.add(new double[]{Double.parseDouble(matcher.group(1)), Double.parseDouble(matcher.group(2))});
        }
        return segments;
    }

    @Test
    void cropExportsOnlyRegion() {
        Buffer buffer = Buffer.empty(new Rect(0, 0, 10, 4));
        buffer.setString(0, 0, "Row0", Style.EMPTY);
        buffer.setString(0, 1, "Row1", Style.EMPTY);
        buffer.setString(0, 2, "Row2", Style.EMPTY);
        buffer.setString(0, 3, "Row3", Style.EMPTY);

        Rect crop = new Rect(0, 1, 4, 2);  // Row1 and Row2, first 4 cols
        String svg = export(buffer).crop(crop).svg().options(o -> o.uniqueId("crop")).toString();

        assertTrue(svg.contains("Row1"));
        assertTrue(svg.contains("Row2"));
        assertFalse(svg.contains("Row0"));
        assertFalse(svg.contains("Row3"));
    }
}
