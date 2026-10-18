package com.leniad.textsigns.render;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Where text goes on one sign block type, in the sign's model coordinates before block rotation: 32 pixels per block,
 * origin at the bottom centre of the block, with the front of the sign facing +Z.
 *
 * @param centreX  horizontal centre of the text area
 * @param centreY  vertical centre of the text area
 * @param width    width of the text area
 * @param height   height of the text area
 * @param frontZ   z of the front surface, which faces +Z
 * @param backZ    z of the back surface, which faces -Z, or null if the back shows no text
 * @param roll     anticlockwise tilt of the board as seen from the front, in degrees
 * @param ink      glyph ink colour that reads best against the face
 * @param minLines line count used to size short text, so it is never drawn larger than this many lines would allow
 * @param maxLines the most lines text is shrunk to before the remainder is dropped
 */
public record SignFace(
        double centreX,
        double centreY,
        double width,
        double height,
        double frontZ,
        @Nullable Double backZ,
        double roll,
        @Nonnull String ink,
        int minLines,
        int maxLines
) {
    public static final int MODEL_PIXELS_PER_BLOCK = 32;
}
