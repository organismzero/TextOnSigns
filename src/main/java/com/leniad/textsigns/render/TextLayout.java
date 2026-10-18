package com.leniad.textsigns.render;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Lays text out on a rectangular sign face: word wraps it, shrinks it until it fits, and centres it.
 * Face measurements use model pixels with the origin at the centre of the face and y pointing up.
 * <p>
 * Text may contain Minecraft colour codes: &amp;0 to &amp;f colour the text that follows, and &amp;r returns to the
 * sign's own colour. Minecraft's formatting codes, &amp;k to &amp;o, are removed. An &amp; that doesn't start a code is
 * drawn as it is.
 */
public final class TextLayout {

    /**
     * A glyph to draw. x and y are the centre of the glyph's quad, in face pixels. colour is null for the sign's own
     * colour.
     */
    public record PlacedGlyph(char character, double x, double y, @Nullable SignColours colour) {
    }

    /**
     * Text ready to lay out, with codes removed: each character with the colour a code gave it, or null for none.
     */
    record StyledText(@Nonnull String text, @Nonnull SignColours[] colours) {
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
        StyledText styled = clean(text);
        String clean = styled.text();
        if (clean.isEmpty() || faceWidth <= 0 || faceHeight <= 0 || maxLines < 1) {
            return Result.EMPTY;
        }

        minLines = Math.max(1, Math.min(minLines, maxLines));
        for (int lineSlots = minLines; lineSlots <= maxLines; lineSlots++) {
            double scale = faceHeight / (lineSlots * font.getLineHeight());
            List<String> lines = wrap(clean, font, faceWidth / scale, false);
            if (lines != null && lines.size() <= lineSlots) {
                return place(lines, styled, font, scale);
            }
        }

        // Nothing fits without breaking a word, so break words at the smallest size
        double scale = faceHeight / (maxLines * font.getLineHeight());
        List<String> lines = wrap(clean, font, faceWidth / scale, true);
        return place(lines.subList(0, Math.min(lines.size(), maxLines)), styled, font, scale);
    }

    /**
     * Applies and removes colour codes, normalises characters to ones the font can draw, and collapses runs of
     * whitespace.
     */
    @Nonnull
    static StyledText clean(@Nonnull String text) {
        StringBuilder out = new StringBuilder(text.length());
        List<SignColours> colours = new ArrayList<>(text.length());
        SignColours colour = null;
        boolean pendingSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == SignColours.CODE_PREFIX && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                SignColours codeColour = SignColours.fromCode(code);
                if (codeColour != null || Character.toLowerCase(code) == SignColours.RESET_CODE) {
                    colour = codeColour;
                    i++;
                    continue;
                }
                if (SignColours.isFormattingCode(code)) {
                    i++;
                    continue;
                }
            }
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
                colours.add(null);
                pendingSpace = false;
            }
            out.append(c);
            colours.add(colour);
        }
        return new StyledText(out.toString(), colours.toArray(new SignColours[0]));
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

    /**
     * Places the wrapped lines. The lines are the styled text in order, less the single spaces where lines were broken
     * between words, so each line's characters are found in the styled text by walking through it alongside them.
     */
    @Nonnull
    private static Result place(@Nonnull List<String> lines, @Nonnull StyledText styled, @Nonnull GlyphFont font, double scale) {
        double lineHeight = font.getLineHeight();
        double top = lines.size() * lineHeight / 2;
        // Offset from the baseline to the vertical centre of a glyph's quad
        double quadCentreY = font.getBaselineY() - font.getCellHeight() / 2.0;

        List<PlacedGlyph> glyphs = new ArrayList<>();
        int cursor = 0;
        for (int row = 0; row < lines.size(); row++) {
            String line = lines.get(row);
            // Skip the space the line was broken at
            if (cursor < styled.text().length() && styled.text().charAt(cursor) == ' ') {
                cursor++;
            }
            double baseline = top - row * lineHeight - font.getAscent();
            double pen = -width(line, font) / 2;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c != ' ') {
                    double quadCentreX = pen + font.quadLeft(c) + font.quadWidth(c) / 2.0;
                    glyphs.add(new PlacedGlyph(c, quadCentreX * scale, (baseline + quadCentreY) * scale, styled.colours()[cursor + i]));
                }
                pen += font.advance(c);
            }
            cursor += line.length();
        }
        return new Result(scale, glyphs, lines.size());
    }
}
