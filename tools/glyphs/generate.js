// Generates the glyph textures, glyph metrics and glyph quad model that TextSigns uses to draw text on signs.
//
// Usage (from tools/glyphs): npm install && npm run generate
//
// For each font in FONTS and each ink colour in INKS, every printable ASCII character (32 to 126) is drawn into its own
// CELL x CELL PNG. Every glyph shares the same pen origin and baseline inside its cell, so the renderer can place a glyph
// by its pen position alone. A metrics JSON per font records each character's advance and ink extent, which the plugin
// uses for word wrapping and spacing.

import { createCanvas, GlobalFonts } from '@napi-rs/canvas';
import { copyFileSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

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

// Ink colours. Each sign type picks the one that reads best against its face.
const INKS = [
    { name: 'Dark', colour: '#22170e' },
    { name: 'Light', colour: '#f4ead6' },
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

function glyphModel() {
    // A single forward-facing quad the size of one glyph cell, centred on the model origin.
    // The texture path is chosen per entity at runtime, so the model names no texture of its own.
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
                    settings: { isPiece: false, size: { x: CELL, y: CELL }, normal: '+Z', isStaticBox: true },
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

function generateFont(font) {
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
            ctx.clearRect(0, 0, CELL, CELL);
            ctx.fillStyle = ink.colour;
            ctx.textBaseline = 'alphabetic';
            ctx.textAlign = 'left';
            ctx.fillText(char, layout.originX, layout.baselineY);
            writeFileSync(join(inkDir, `Glyph_${pad3(code)}.png`), canvas.toBuffer('image/png'));
        }
    }

    const glyphs = {};
    for (const { code, char } of chars()) {
        const m = layout.measured.glyphs.get(code);
        const empty = char === ' ';
        glyphs[code] = {
            char,
            advance: round2(m.width),
            // Ink extent relative to the pen position, in pixels; inkLeft is negative when ink overhangs the pen.
            inkLeft: empty ? 0 : round2(-m.actualBoundingBoxLeft),
            inkRight: empty ? 0 : round2(m.actualBoundingBoxRight),
            pixelWidth: empty ? 0 : round2(m.actualBoundingBoxLeft + m.actualBoundingBoxRight),
        };
    }

    const metrics = {
        font: font.name,
        licence: font.licenceId,
        cellWidth: CELL,
        cellHeight: CELL,
        fontSize: layout.size,
        // Pen origin and baseline inside every cell, in pixels from the top-left corner.
        originX: layout.originX,
        baselineY: layout.baselineY,
        ascent: round2(fontMetrics.fontBoundingBoxAscent),
        descent: round2(fontMetrics.fontBoundingBoxDescent),
        lineHeight: round2(fontMetrics.fontBoundingBoxAscent + fontMetrics.fontBoundingBoxDescent),
        inks: INKS.map((ink) => ink.name),
        texturePattern: `Items/TextSigns/Fonts/${font.name}/{ink}/Glyph_{code}.png`,
        glyphs,
    };

    mkdirSync(DATA_DIR, { recursive: true });
    writeFileSync(join(DATA_DIR, `${font.name}.json`), JSON.stringify(metrics, null, 2) + '\n');
    copyFileSync(font.licence, join(DATA_DIR, `${font.name}-OFL.txt`));

    console.log(`${font.name}: ${layout.size}px in a ${CELL}px cell, origin (${layout.originX}, ${layout.baselineY}), ${INKS.length} inks`);
}

for (const font of FONTS) {
    generateFont(font);
}

mkdirSync(COMMON_DIR, { recursive: true });
writeFileSync(join(COMMON_DIR, 'Glyph.blockymodel'), JSON.stringify(glyphModel(), null, 2) + '\n');
console.log(`Glyph model: ${CELL}x${CELL} quad`);
