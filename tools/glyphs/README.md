# Glyph generator

Generates the assets TextSigns uses to draw text on sign faces:

- `src/main/resources/Common/Items/TextSigns/Fonts/<Font>/<Ink>/Glyph_<code>.png`: one 32 × 32 texture per printable ASCII character (codes 32 to 126) for each ink colour.
- `src/main/resources/Common/Items/TextSigns/Glyph.blockymodel`: a single quad the size of one glyph cell, which every glyph entity uses with its own texture.
- `src/main/resources/textsigns/fonts/<Font>.json`: metrics for each character (advance width, pixel width and ink extent relative to the pen), plus the shared pen origin and baseline inside the cell. The plugin uses these for word wrapping and spacing.
- `src/main/resources/textsigns/fonts/<Font>-OFL.txt`: the font's licence, shipped in the plugin jar alongside the glyphs rendered from it.

All glyphs share the same pen origin and baseline inside their cell, so a glyph is placed by its pen position alone.

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
