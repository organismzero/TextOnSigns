package com.leniad.textsigns.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Glyph metrics for one font, read from the JSON written by tools/glyphs/generate.js.
 * All measurements are in glyph texture pixels. Every glyph cell shares the same pen origin and baseline.
 */
public final class GlyphFont {
    public static final char FIRST_CHAR = 32;
    public static final char LAST_CHAR = 126;
    public static final char REPLACEMENT_CHAR = '?';

    private final String name;
    private final int cellWidth;
    private final int cellHeight;
    private final double originX;
    private final double baselineY;
    private final double ascent;
    private final double descent;
    private final double lineHeight;
    private final String texturePattern;
    private final String modelPattern;
    private final double[] advances = new double[LAST_CHAR - FIRST_CHAR + 1];
    private final int[] quadLefts = new int[LAST_CHAR - FIRST_CHAR + 1];
    private final int[] quadWidths = new int[LAST_CHAR - FIRST_CHAR + 1];

    private GlyphFont(@Nonnull JsonObject json) {
        this.name = json.get("font").getAsString();
        this.cellWidth = json.get("cellWidth").getAsInt();
        this.cellHeight = json.get("cellHeight").getAsInt();
        this.originX = json.get("originX").getAsDouble();
        this.baselineY = json.get("baselineY").getAsDouble();
        this.ascent = json.get("ascent").getAsDouble();
        this.descent = json.get("descent").getAsDouble();
        this.lineHeight = json.get("lineHeight").getAsDouble();
        this.texturePattern = json.get("texturePattern").getAsString();
        this.modelPattern = json.get("modelPattern").getAsString();

        JsonObject glyphs = json.getAsJsonObject("glyphs");
        for (Map.Entry<String, JsonElement> entry : glyphs.entrySet()) {
            int code = Integer.parseInt(entry.getKey());
            if (code >= FIRST_CHAR && code <= LAST_CHAR) {
                JsonObject glyph = entry.getValue().getAsJsonObject();
                this.advances[code - FIRST_CHAR] = glyph.get("advance").getAsDouble();
                this.quadLefts[code - FIRST_CHAR] = glyph.get("quadLeft").getAsInt();
                this.quadWidths[code - FIRST_CHAR] = glyph.get("quadWidth").getAsInt();
            }
        }
    }

    @Nonnull
    public static GlyphFont parse(@Nonnull Reader reader) {
        return new GlyphFont(JsonParser.parseReader(reader).getAsJsonObject());
    }

    /**
     * Loads a font's metrics from the plugin jar, for example "Lexend" reads textsigns/fonts/Lexend.json.
     */
    @Nonnull
    public static GlyphFont load(@Nonnull String fontName) {
        String path = "textsigns/fonts/" + fontName + ".json";
        try (InputStream in = GlyphFont.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing font metrics " + path);
            }
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read font metrics " + path, e);
        }
    }

    /**
     * Maps a character to one the font has a glyph for. Characters outside printable ASCII become '?',
     * and whitespace becomes a space.
     */
    public static char normalise(char c) {
        if (Character.isWhitespace(c)) {
            return ' ';
        }
        return c >= FIRST_CHAR && c <= LAST_CHAR ? c : REPLACEMENT_CHAR;
    }

    public double advance(char c) {
        return advances[normalise(c) - FIRST_CHAR];
    }

    /**
     * Left edge of a glyph's quad relative to the pen position.
     */
    public int quadLeft(char c) {
        return quadLefts[normalise(c) - FIRST_CHAR];
    }

    /**
     * Width of a glyph's quad, which is as wide as its ink. Zero for a space.
     */
    public int quadWidth(char c) {
        return quadWidths[normalise(c) - FIRST_CHAR];
    }

    /**
     * Path of the quad model a glyph is drawn on, relative to the Common asset folder.
     */
    @Nonnull
    public String model(char c) {
        return modelPattern.replace("{width}", Integer.toString(quadWidth(c)));
    }

    /**
     * Texture path of a glyph, relative to the Common asset folder.
     */
    @Nonnull
    public String texture(@Nonnull String ink, char c) {
        return texturePattern.replace("{ink}", ink).replace("{code}", String.format("%03d", (int) normalise(c)));
    }

    @Nonnull
    public String getName() {
        return name;
    }

    public int getCellWidth() {
        return cellWidth;
    }

    public int getCellHeight() {
        return cellHeight;
    }

    public double getOriginX() {
        return originX;
    }

    public double getBaselineY() {
        return baselineY;
    }

    public double getAscent() {
        return ascent;
    }

    public double getDescent() {
        return descent;
    }

    public double getLineHeight() {
        return lineHeight;
    }
}
