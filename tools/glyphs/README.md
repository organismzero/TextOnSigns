# Glyph generator

Generates the assets TextSigns uses to draw text on sign faces:

- `src/main/resources/Common/Items/TextSigns/Fonts/<Font>/<Ink>/Glyph_<code>.png`: one 32 × 32 texture per printable ASCII character, left aligned, (codes 32 to 126) for each ink colour.
- `src/main/resources/Common/Items/TextSigns/Glyph_W<width>.blockymodel`: one quad model for each glyph width in use, one cell tall. Each glyph entity uses the quad as wide as its glyph, with its own texture. `Glyph.blockymodel` is a full-cell quad that the `TextSigns_Glyph` model asset names as its default.
- `src/main/resources/textsigns/fonts/<Font>.json`: metrics for each character (advance width, ink extent, and quad position and width relative to the pen), plus the shared pen origin and baseline inside the cell. The plugin uses these for word wrapping and spacing.
- `src/main/resources/textsigns/fonts/<Font>-OFL.txt`: the font's licence, shipped in the plugin jar alongside the glyphs rendered from it.

Each glyph texture is left aligned so its ink starts at column 0, on a baseline shared by every glyph. The metrics give each glyph's quad position relative to the pen and its width. Glyph quads are cropped to their ink because the client lets the transparent part of one quad hide a glyph behind it, so overlapping full-cell quads lost thin characters.

## Running it

Requires Node.js 20 or later.

```sh
cd tools/glyphs
npm install
npm run generate
```

The script deletes and rewrites each font's output folder, so commit the results after running it.

## Fonts

| Font | File | Source | Licence |
|---|---|---|---|
| Lexend Medium | `fonts/Lexend/Lexend-Medium.ttf` | [googlefonts/lexend](https://github.com/googlefonts/lexend) at commit `7894f02b2e7eabc48595f1d4eff3b17b48c6e651` | SIL Open Font Licence 1.1 (`fonts/Lexend/OFL.txt`) |

To add a font, put its file and licence under `fonts/<Name>/` and add an entry to `FONTS` in `generate.js`. Only use fonts whose licence allows redistribution.

## Ink colours

Ink colours are defined in `INKS` in `generate.js`. Each sign type chooses the ink that reads best against its face.
