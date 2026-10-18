package com.leniad.textsigns.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextLayoutTest {
    private static final GlyphFont FONT = GlyphFont.load("Lexend");

    @Test
    void cleanCollapsesWhitespaceAndReplacesUnsupportedCharacters() {
        assertEquals("Hello world", TextLayout.clean("  Hello \t\n world  ").text());
        assertEquals("Caf? ?", TextLayout.clean("Café 😀").text());
    }

    @Test
    void colourCodesColourTheTextAfterThemAndAreRemoved() {
        TextLayout.StyledText styled = TextLayout.clean("a&cbc&rd&9E");
        assertEquals("abcdE", styled.text());
        assertArrayEquals(new SignColours[]{null, SignColours.RED, SignColours.RED, null, SignColours.BLUE}, styled.colours());
    }

    @Test
    void formattingCodesAreRemovedAndOtherAmpersandsKept() {
        TextLayout.StyledText styled = TextLayout.clean("&lBold & co&");
        assertEquals("Bold & co&", styled.text());
    }

    @Test
    void glyphsKeepTheirColourAcrossLineBreaks() {
        TextLayout.Result result = TextLayout.layout("&cOrganism's &9Sign", FONT, 22, 18, 2, 5);
        assertEquals(2, result.lineCount());
        for (TextLayout.PlacedGlyph glyph : result.glyphs()) {
            SignColours expected = "Sign".indexOf(glyph.character()) >= 0 && glyph.y() < 0 ? SignColours.BLUE : SignColours.RED;
            assertEquals(expected, glyph.colour(), "colour of " + glyph);
        }
    }

    @Test
    void wrapBreaksBetweenWords() {
        double width = TextLayout.width("Welcome ", FONT) + TextLayout.width("trav", FONT);
        assertEquals(List.of("Welcome", "traveller"), TextLayout.wrap("Welcome traveller", FONT, width, false));
    }

    @Test
    void wrapBreaksWordsLongerThanALine() {
        double width = TextLayout.width("abcd", FONT) + 0.01;
        assertNull(TextLayout.wrap("abcdefghij", FONT, width, false));
        List<String> lines = TextLayout.wrap("abcdefghij", FONT, width, true);
        assertEquals("abcdefghij", String.join("", lines));
        for (String line : lines) {
            assertTrue(TextLayout.width(line, FONT) <= width, line);
        }
    }

    @Test
    void shortTextIsSizedForMinLines() {
        TextLayout.Result result = TextLayout.layout("Shop", FONT, 24, 16, 2, 5);
        assertEquals(1, result.lineCount());
        assertEquals(16 / (2 * FONT.getLineHeight()), result.scale(), 1e-9);
        assertEquals(4, result.glyphs().size());
    }

    @Test
    void longTextShrinksUntilItFits() {
        String text = "The quick brown fox jumps over the lazy dog";
        TextLayout.Result result = TextLayout.layout(text, FONT, 24, 16, 2, 5);
        assertTrue(result.lineCount() > 2);
        assertTrue(result.lineCount() <= 5);
        assertEquals(text.replace(" ", "").length(), result.glyphs().size());
        assertFitsFace(result, 24, 16);
    }

    @Test
    void textShrinksRatherThanBreakingAWord() {
        TextLayout.Result result = TextLayout.layout("Organism's Sign", FONT, 22, 18, 2, 5);
        assertEquals(2, result.lineCount());
        // The first line holds the whole of "Organism's": ten glyphs, all above the second line's glyphs
        double firstLineY = result.glyphs().get(0).y();
        assertEquals(10, result.glyphs().stream().filter(g -> g.y() == firstLineY).count());
    }

    @Test
    void lineBreakCodesStartNewLinesAndDropSpacesAroundThem() {
        TextLayout.StyledText styled = TextLayout.clean("Hi \\n there\\n\\nend \\ ok");
        assertEquals("Hi\nthere\n\nend \\ ok", styled.text());
        assertEquals(List.of("Hi", "there", "", "end \\ ok"), TextLayout.wrap(styled.text(), FONT, 1000, false));
    }

    @Test
    void lineBreaksForceShortWordsOntoSeparateLines() {
        TextLayout.Result result = TextLayout.layout("&cA\\nB\\n\\nC", FONT, 24, 16, 2, 5);
        assertEquals(4, result.lineCount());
        assertEquals(3, result.glyphs().size());
        TextLayout.PlacedGlyph a = result.glyphs().get(0);
        TextLayout.PlacedGlyph b = result.glyphs().get(1);
        TextLayout.PlacedGlyph c = result.glyphs().get(2);
        assertEquals('A', a.character());
        assertEquals('B', b.character());
        assertEquals('C', c.character());
        assertTrue(a.y() > b.y() && b.y() > c.y());
        // Two line heights between B and C, for the empty line
        assertEquals(2 * (a.y() - b.y()), b.y() - c.y(), 1e-9);
        assertEquals(SignColours.RED, c.colour());
    }

    @Test
    void textThatNeverFitsIsTruncatedToMaxLines() {
        String text = "word ".repeat(200);
        TextLayout.Result result = TextLayout.layout(text, FONT, 24, 16, 2, 5);
        assertEquals(5, result.lineCount());
    }

    @Test
    void singleLineIsCentredHorizontally() {
        TextLayout.Result result = TextLayout.layout("HH", FONT, 24, 16, 2, 5);
        double penOffset = (FONT.quadLeft('H') + FONT.quadWidth('H') / 2.0) * result.scale();
        double lineStart = result.glyphs().get(0).x() - penOffset;
        double lineEnd = result.glyphs().get(1).x() - penOffset + FONT.advance('H') * result.scale();
        assertEquals(0, (lineStart + lineEnd) / 2, 1e-9);
    }

    @Test
    void blankTextHasNoGlyphs() {
        assertTrue(TextLayout.layout("   ", FONT, 24, 16, 2, 5).glyphs().isEmpty());
    }

    private static void assertFitsFace(TextLayout.Result result, double width, double height) {
        for (TextLayout.PlacedGlyph glyph : result.glyphs()) {
            double halfQuad = FONT.quadWidth(glyph.character()) * result.scale() / 2;
            assertTrue(Math.abs(glyph.x()) + halfQuad <= width / 2 + 1e-9, "x " + glyph);
            assertTrue(Math.abs(glyph.y()) <= height / 2, "y " + glyph);
        }
    }
}
