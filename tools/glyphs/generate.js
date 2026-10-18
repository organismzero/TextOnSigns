// Generates the glyph textures, glyph metrics and glyph quad model that TextSigns uses to draw text on signs.
//
// Usage (from tools/glyphs): npm install && npm run generate
//
// For each font in FONTS and each ink colour in INKS, every printable ASCII character (32 to 126) is drawn into its own
// CELL x CELL PNG, left aligned so its ink starts at column 0, on the shared baseline. Each glyph is shown on a quad only as
// wide as its ink, so neighbouring glyphs don't overlap: the client would otherwise let the transparent part of one glyph
// hide its neighbour. A metrics JSON per font records each character's advance, quad position and quad width, which the
// plugin uses for word wrapping and placement.

import { createCanvas, GlobalFonts } from '@napi-rs/canvas';
import { copyFileSync, mkdirSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateSync } from 'node:zlib';

const HERE = dirname(fileURLToPath(import.meta.url));
const RESOURCES = join(HERE, '..', '..', 'src', 'main', 'resources');

// Glyph cell size in pixels. Block models use 32 pixels per block, and the glyph quad model is sized to match the cell,
// so one texture pixel maps to one model pixel.
const CELL = 32;

// Transparent margin kept around the ink, in pixels, so glyphs never touch the cell edge.
const PADDING = 1;

const FIRST_CHAR = 32;
const LAST_CHAR = 126;

const FONTS = [
    {
        name: 'Lexend',
        file: join(HERE, 'fonts', 'Lexend', 'Lexend-Medium.ttf'),
        licence: join(HERE, 'fonts', 'Lexend', 'OFL.txt'),
        licenceId: 'OFL-1.1',
    },
];

// Ink colours. Dark and Light are the default inks, and each sign type picks the one that reads best against its face.
// The rest are the 16 Minecraft text colours, which players choose for a whole sign or with &0 to &f codes. Their order
// and names must match SignColours.java.
const INKS = [
    { name: 'Dark', colour: '#22170e' },
    { name: 'Light', colour: '#f4ead6' },
    { name: 'Black', colour: '#000000' },
    { name: 'DarkBlue', colour: '#0000aa' },
    { name: 'DarkGreen', colour: '#00aa00' },
    { name: 'DarkAqua', colour: '#00aaaa' },
    { name: 'DarkRed', colour: '#aa0000' },
    { name: 'DarkPurple', colour: '#aa00aa' },
    { name: 'Gold', colour: '#ffaa00' },
    { name: 'Grey', colour: '#aaaaaa' },
    { name: 'DarkGrey', colour: '#555555' },
    { name: 'Blue', colour: '#5555ff' },
    { name: 'Green', colour: '#55ff55' },
    { name: 'Aqua', colour: '#55ffff' },
    { name: 'Red', colour: '#ff5555' },
    { name: 'LightPurple', colour: '#ff55ff' },
    { name: 'Yellow', colour: '#ffff55' },
    { name: 'White', colour: '#ffffff' },
];

// Common/ paths are sent to clients as assets. Model and texture paths in the game are relative to Common/, and the server only
// accepts entity model assets under Characters/, NPC/, Items/ or VFX/.
const COMMON_DIR = join(RESOURCES, 'Common', 'Items', 'TextSigns');
// Metrics and licences are plain classpath resources read by the plugin, outside the asset pack folders.
const DATA_DIR = join(RESOURCES, 'textsigns', 'fonts');

const REFERENCE_SIZE = 100;

function chars() {
    const out = [];
    for (let code = FIRST_CHAR; code <= LAST_CHAR; code++) {
        out.push({ code, char: String.fromCharCode(code) });
    }
    return out;
}

function pad3(n) {
    return String(n).padStart(3, '0');
}

function round2(n) {
    return Math.round(n * 100) / 100;
}

// Measures every character at the given size and returns the extremes the cell has to hold.
function measureAll(ctx, family, size) {
    ctx.font = `${size}px "${family}"`;
    let maxAscent = 0;
    let maxDescent = 0;
    let maxLeft = 0;
    let maxRight = 0;
    const glyphs = new Map();
    for (const { code, char } of chars()) {
        const m = ctx.measureText(char);
        glyphs.set(code, m);
        if (char === ' ') {
            continue;
        }
        maxAscent = Math.max(maxAscent, m.actualBoundingBoxAscent);
        maxDescent = Math.max(maxDescent, m.actualBoundingBoxDescent);
        maxLeft = Math.max(maxLeft, m.actualBoundingBoxLeft);
        maxRight = Math.max(maxRight, m.actualBoundingBoxRight);
    }
    return { maxAscent, maxDescent, maxLeft, maxRight, glyphs };
}

// Picks the largest whole-pixel font size whose tallest and widest glyphs both fit inside the cell.
function fitLayout(ctx, family) {
    const ref = measureAll(ctx, family, REFERENCE_SIZE);
    const usable = CELL - 2 * PADDING;
    let size = Math.floor((usable * REFERENCE_SIZE) / (ref.maxAscent + ref.maxDescent));
    for (; size > 1; size--) {
        const m = measureAll(ctx, family, size);
        const originX = PADDING + Math.ceil(m.maxLeft);
        const baselineY = PADDING + Math.ceil(m.maxAscent);
        const fitsWidth = originX + Math.ceil(m.maxRight) <= CELL - PADDING;
        const fitsHeight = baselineY + Math.ceil(m.maxDescent) <= CELL - PADDING;
        if (fitsWidth && fitsHeight) {
            return { size, originX, baselineY, measured: m };
        }
    }
    throw new Error(`Could not fit ${family} into a ${CELL}px cell`);
}

// A single forward-facing quad, width pixels wide and one cell tall, centred on the model origin. It shows the leftmost
// width columns of the glyph texture. The texture is chosen per entity at runtime, so the model names none of its own.
// Writes an 8-bit RGBA PNG. Used instead of the canvas's own encoder, which stores colours premultiplied by alpha and
// so gives nearly transparent edge pixels near-random colours (often white) when it converts them back.
function encodePng(width, height, rgba) {
    const crcTable = Array.from({ length: 256 }, (_, n) => {
        let c = n;
        for (let k = 0; k < 8; k++) {
            c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
        }
        return c >>> 0;
    });
    const crc32 = (bytes) => {
        let c = 0xffffffff;
        for (const b of bytes) {
            c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8);
        }
        return (c ^ 0xffffffff) >>> 0;
    };
    const chunk = (type, data) => {
        const length = Buffer.alloc(4);
        length.writeUInt32BE(data.length);
        const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
        const crc = Buffer.alloc(4);
        crc.writeUInt32BE(crc32(body));
        return Buffer.concat([length, body, crc]);
    };

    const header = Buffer.alloc(13);
    header.writeUInt32BE(width, 0);
    header.writeUInt32BE(height, 4);
    header[8] = 8; // bit depth
    header[9] = 6; // colour type: RGBA
    // Each row starts with filter type 0 (none)
    const raw = Buffer.alloc(height * (width * 4 + 1));
    for (let y = 0; y < height; y++) {
        Buffer.from(rgba.buffer, rgba.byteOffset + y * width * 4, width * 4).copy(raw, y * (width * 4 + 1) + 1);
    }
    return Buffer.concat([
        Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
        chunk('IHDR', header),
        chunk('IDAT', deflateSync(raw, { level: 9 })),
        chunk('IEND', Buffer.alloc(0)),
    ]);
}

// The glyph's coverage from the canvas, painted in exactly one colour. Every pixel, including fully transparent ones,
// gets the ink's colour, so neither edge pixels nor texture filtering can bring in another colour.
function glyphPixels(ctx, colour) {
    const r = parseInt(colour.slice(1, 3), 16);
    const g = parseInt(colour.slice(3, 5), 16);
    const b = parseInt(colour.slice(5, 7), 16);
    const pixels = ctx.getImageData(0, 0, CELL, CELL).data;
    const out = new Uint8Array(pixels.length);
    for (let i = 0; i < pixels.length; i += 4) {
        out[i] = r;
        out[i + 1] = g;
        out[i + 2] = b;
        out[i + 3] = pixels[i + 3];
    }
    return out;
}

function glyphModel(width) {
    return {
        nodes: [
            {
                id: '1',
                name: 'Glyph',
                position: { x: 0, y: 0, z: 0 },
                orientation: { x: 0, y: 0, z: 0, w: 1 },
                shape: {
                    type: 'quad',
                    offset: { x: 0, y: 0, z: 0 },
                    stretch: { x: 1, y: 1, z: 1 },
                    settings: { isPiece: false, size: { x: width, y: CELL }, normal: '+Z', isStaticBox: true },
                    textureLayout: {
                        front: { offset: { x: 0, y: 0 }, mirror: { x: false, y: false }, angle: 0 },
                    },
                    unwrapMode: 'custom',
                    visible: true,
                    doubleSided: true,
                    shadingMode: 'flat',
                },
            },
        ],
        format: 'prop',
        lod: 'auto',
    };
}

// Columns of the cell that hold a glyph's ink, from the pen origin and measured ink extent.
function inkColumns(layout, m) {
    const left = Math.max(0, Math.floor(layout.originX - m.actualBoundingBoxLeft));
    const right = Math.min(CELL, Math.ceil(layout.originX + m.actualBoundingBoxRight));
    return { left, width: Math.max(1, right - left) };
}

function generateFont(font, widths) {
    const family = `TextSigns ${font.name}`;
    if (!GlobalFonts.registerFromPath(font.file, family)) {
        throw new Error(`Failed to load font ${font.file}`);
    }

    const canvas = createCanvas(CELL, CELL);
    const ctx = canvas.getContext('2d');
    const layout = fitLayout(ctx, family);
    ctx.font = `${layout.size}px "${family}"`;
    const fontMetrics = ctx.measureText('Hg');

    const fontDir = join(COMMON_DIR, 'Fonts', font.name);
    rmSync(fontDir, { recursive: true, force: true });

    for (const ink of INKS) {
        const inkDir = join(fontDir, ink.name);
        mkdirSync(inkDir, { recursive: true });
        for (const { code, char } of chars()) {
            const columns = inkColumns(layout, layout.measured.glyphs.get(code));
            ctx.clearRect(0, 0, CELL, CELL);
            ctx.fillStyle = ink.colour;
            ctx.textBaseline = 'alphabetic';
            ctx.textAlign = 'left';
            ctx.fillText(char, layout.originX - columns.left, layout.baselineY);
            writeFileSync(join(inkDir, `Glyph_${pad3(code)}.png`), encodePng(CELL, CELL, glyphPixels(ctx, ink.colour)));
        }
    }

    const glyphs = {};
    for (const { code, char } of chars()) {
        const m = layout.measured.glyphs.get(code);
        const empty = char === ' ';
        const columns = inkColumns(layout, m);
        if (!empty) {
            widths.add(columns.width);
        }
        glyphs[code] = {
            char,
            advance: round2(m.width),
            // Ink extent relative to the pen position, in pixels; inkLeft is negative when ink overhangs the pen.
            inkLeft: empty ? 0 : round2(-m.actualBoundingBoxLeft),
            inkRight: empty ? 0 : round2(m.actualBoundingBoxRight),
            pixelWidth: empty ? 0 : round2(m.actualBoundingBoxLeft + m.actualBoundingBoxRight),
            // The glyph's quad: its left edge relative to the pen position and its width, in pixels.
            quadLeft: empty ? 0 : columns.left - layout.originX,
            quadWidth: empty ? 0 : columns.width,
        };
    }

    const metrics = {
        font: font.name,
        licence: font.licenceId,
        cellWidth: CELL,
        cellHeight: CELL,
        fontSize: layout.size,
        // Pen origin and baseline inside the cell the glyphs were drawn in, in pixels from the top-left corner. Textures
        // are shifted left by each glyph's quadLeft - originX, so only baselineY still applies to them.
        originX: layout.originX,
        baselineY: layout.baselineY,
        ascent: round2(fontMetrics.fontBoundingBoxAscent),
        descent: round2(fontMetrics.fontBoundingBoxDescent),
        lineHeight: round2(fontMetrics.fontBoundingBoxAscent + fontMetrics.fontBoundingBoxDescent),
        inks: INKS.map((ink) => ink.name),
        texturePattern: `Items/TextSigns/Fonts/${font.name}/{ink}/Glyph_{code}.png`,
        modelPattern: 'Items/TextSigns/Glyph_W{width}.blockymodel',
        glyphs,
    };

    mkdirSync(DATA_DIR, { recursive: true });
    writeFileSync(join(DATA_DIR, `${font.name}.json`), JSON.stringify(metrics, null, 2) + '\n');
    copyFileSync(font.licence, join(DATA_DIR, `${font.name}-OFL.txt`));

    console.log(`${font.name}: ${layout.size}px in a ${CELL}px cell, origin (${layout.originX}, ${layout.baselineY}), ${INKS.length} inks`);
}

const widths = new Set();
for (const font of FONTS) {
    generateFont(font, widths);
}

// One quad model per glyph width in use, plus a full-cell quad that the glyph model asset names as its default.
mkdirSync(COMMON_DIR, { recursive: true });
for (const file of readdirSync(COMMON_DIR)) {
    if (file.endsWith('.blockymodel')) {
        rmSync(join(COMMON_DIR, file));
    }
}
writeFileSync(join(COMMON_DIR, 'Glyph.blockymodel'), JSON.stringify(glyphModel(CELL), null, 2) + '\n');
for (const width of widths) {
    writeFileSync(join(COMMON_DIR, `Glyph_W${width}.blockymodel`), JSON.stringify(glyphModel(width), null, 2) + '\n');
}
console.log(`Glyph models: ${widths.size} widths, ${Math.min(...widths)} to ${Math.max(...widths)} pixels`);
