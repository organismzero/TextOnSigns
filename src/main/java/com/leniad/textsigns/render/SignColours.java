package com.leniad.textsigns.render;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The 16 Minecraft text colours that players can use on signs, either for a whole sign or with &amp;0 to &amp;f codes in
 * the text. Each colour's ink name is the folder of its glyph textures, written by tools/glyphs/generate.js.
 */
public enum SignColours {
    BLACK('0', "Black", "#000000"),
    DARK_BLUE('1', "DarkBlue", "#0000aa"),
    DARK_GREEN('2', "DarkGreen", "#00aa00"),
    DARK_AQUA('3', "DarkAqua", "#00aaaa"),
    DARK_RED('4', "DarkRed", "#aa0000"),
    DARK_PURPLE('5', "DarkPurple", "#aa00aa"),
    GOLD('6', "Gold", "#ffaa00"),
    GREY('7', "Grey", "#aaaaaa"),
    DARK_GREY('8', "DarkGrey", "#555555"),
    BLUE('9', "Blue", "#5555ff"),
    GREEN('a', "Green", "#55ff55"),
    AQUA('b', "Aqua", "#55ffff"),
    RED('c', "Red", "#ff5555"),
    LIGHT_PURPLE('d', "LightPurple", "#ff55ff"),
    YELLOW('e', "Yellow", "#ffff55"),
    WHITE('f', "White", "#ffffff");

    // Starts a colour or formatting code in sign text, as in Minecraft
    public static final char CODE_PREFIX = '&';
    // Code that returns to the sign's own colour
    public static final char RESET_CODE = 'r';

    private final char code;
    private final String ink;
    private final String hex;

    SignColours(char code, @Nonnull String ink, @Nonnull String hex) {
        this.code = code;
        this.ink = ink;
        this.hex = hex;
    }

    public char code() {
        return code;
    }

    /**
     * The glyph ink, which is also the colour's stored name and the suffix of its translation key.
     */
    @Nonnull
    public String ink() {
        return ink;
    }

    @Nonnull
    public String hex() {
        return hex;
    }

    @Nullable
    public static SignColours fromCode(char code) {
        char lower = Character.toLowerCase(code);
        for (SignColours colour : values()) {
            if (colour.code == lower) {
                return colour;
            }
        }
        return null;
    }

    @Nullable
    public static SignColours fromInk(@Nullable String ink) {
        if (ink == null) {
            return null;
        }
        for (SignColours colour : values()) {
            if (colour.ink.equals(ink)) {
                return colour;
            }
        }
        return null;
    }

    /**
     * Whether a code is one of Minecraft's formatting codes (obfuscated, bold, strikethrough, underline and italic).
     * Signs don't support these, so they are removed from the text.
     */
    public static boolean isFormattingCode(char code) {
        char lower = Character.toLowerCase(code);
        return lower >= 'k' && lower <= 'o';
    }
}
