package com.leniad.textsigns.render;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Lays text out on a rectangular sign face: word wraps it, shrinks it until it fits, and centres it.
 * Face measurements use model pixels with the origin at the centre of the face and y pointing up.
 */
public final class TextLayout {

    /**
     * A glyph to draw. x and y are the centre of the glyph's quad, in face pixels.
     */
    public record PlacedGlyph(char character, double x, double y) {
    }

    /**
     * @param scale face pixels per glyph texture pixel, so a glyph cell is cellWidth * scale face pixels wide
     */
    public record Result(double scale, @Nonnull List<PlacedGlyph> glyphs, int lineCount) {
        public static final Result EMPTY = new Result(0, List.of(), 0);
    }

    private TextLayout() {
    }

    /**
     * Lays out text at the largest size where it fits on the face in at most maxLines lines. The text is never drawn
     * larger than the size that fits minLines lines. If it doesn't fit at maxLines lines, the lines that fit are kept and
     * the rest is dropped.
     */
    @Nonnull
    public static Result layout(@Nonnull String text, @Nonnull GlyphFont font, double faceWidth, double faceHeight, int minLines, int maxLines) {
        String clean = clean(text);
        if (clean.isEmpty() || faceWidth <= 0 || faceHeight <= 0 || maxLines < 1) {
            return Result.EMPTY;
        }

        minLines = Math.max(1, Math.min(minLines, maxLines));
        for (int lineSlots = minLines; lineSlots <= maxLines; lineSlots++) {
            double scale = faceHeight / (lineSlots * font.getLineHeight());
            List<String> lines = wrap(clean, font, faceWidth / scale, false);
            if (lines != null && lines.size() <= lineSlots) {
                return place(lines, font, scale);
            }
        }

        // Nothing fits without breaking a word, so break words at the smallest size
        double scale = faceHeight / (maxLines * font.getLineHeight());
        List<String> lines = wrap(clean, font, faceWidth / scale, true);
        return place(lines.subList(0, Math.min(lines.size(), maxLines)), font, scale);
    }

    /**
     * Normalises characters to ones the font can draw and collapses runs of whitespace.
     */
    @Nonnull
    static String clean(@Nonnull String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                i++;
            }
            c = GlyphFont.normalise(c);
            if (c == ' ') {
                pendingSpace = out.length() > 0;
                continue;
            }
            if (pendingSpace) {
                out.append(' ');
                pendingSpace = false;
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * Greedy word wrap. Words wider than a line are broken between characters if breakWords is set; otherwise the text
     * doesn't fit and this returns null.
     */
    @Nullable
    static List<String> wrap(@Nonnull String text, @Nonnull GlyphFont font, double maxWidth, boolean breakWords) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        double lineWidth = 0;
        double spaceWidth = font.advance(' ');

        for (String word : text.split(" ")) {
            double wordWidth = width(word, font);
            double needed = line.isEmpty() ? wordWidth : lineWidth + spaceWidth + wordWidth;
            if (needed <= maxWidth) {
                if (!line.isEmpty()) {
                    line.append(' ');
                }
                line.append(word);
                lineWidth = needed;
                continue;
            }

            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
                lineWidth = 0;
            }

            if (wordWidth <= maxWidth) {
                line.append(word);
                lineWidth = wordWidth;
                continue;
            }
            if (!breakWords) {
                return null;
            }

            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                double advance = font.advance(c);
                if (!line.isEmpty() && lineWidth + advance > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                    lineWidth = 0;
                }
                line.append(c);
                lineWidth += advance;
            }
        }

        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    static double width(@Nonnull String text, @Nonnull GlyphFont font) {
        double width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += font.advance(text.charAt(i));
        }
        return width;
    }

    @Nonnull
    private static Result place(@Nonnull List<String> lines, @Nonnull GlyphFont font, double scale) {
        double lineHeight = font.getLineHeight();
        double top = lines.size() * lineHeight / 2;
        // Offset from the baseline to the vertical centre of a glyph's quad
        double quadCentreY = font.getBaselineY() - font.getCellHeight() / 2.0;

        List<PlacedGlyph> glyphs = new ArrayList<>();
        for (int row = 0; row < lines.size(); row++) {
            String line = lines.get(row);
            double baseline = top - row * lineHeight - font.getAscent();
            double pen = -width(line, font) / 2;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c != ' ') {
                    double quadCentreX = pen + font.quadLeft(c) + font.quadWidth(c) / 2.0;
                    glyphs.add(new PlacedGlyph(c, quadCentreX * scale, (baseline + quadCentreY) * scale));
                }
                pen += font.advance(c);
            }
        }
        return new Result(scale, glyphs, lines.size());
    }
}
