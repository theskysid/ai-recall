/* The dark palette's whole job is to keep the roles legible on a ground that
   inverted. A hex nudged by eye is exactly how that quietly stops being true,
   so the ratios are asserted rather than trusted.

   Values mirror the :root[data-theme="dark"] block in index.css. */
import assert from 'node:assert';
import { readFileSync } from 'node:fs';

const css = readFileSync(new URL('../index.css', import.meta.url), 'utf8');
const dark = css.slice(css.indexOf(':root[data-theme="dark"]'));

/* Read the value straight out of the stylesheet, so editing one without the
   other fails here instead of shipping. */
const token = (name) => {
    const m = dark.match(new RegExp(`${name}:\\s*(#[0-9a-fA-F]{6})`));
    assert.ok(m, `${name} not found in the dark block`);
    return m[1];
};

const lin = (c) => (c /= 255) <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
const lum = (hex) => {
    const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
};
const ratio = (a, b) => {
    const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p);
    return (x + 0.05) / (y + 0.05);
};

const sheet = token('--nb-sheet');
const surface = token('--surface');
const chrome = token('--chrome');

const checks = [
    ['body text on the sheet', token('--nb-ink'), sheet],
    ['secondary text on the sheet', token('--nb-ink-2'), sheet],
    ['faint text on the sheet', token('--nb-ink-3'), sheet],
    ['meta text on the sheet', token('--text-3'), sheet],
    ['ghost text on the sheet', token('--text-4'), sheet],
    ['body text on the surface', token('--nb-ink'), surface],
    ['meta text on the chrome', token('--text-3'), chrome],
    ['accent as text, on the surface', token('--accent-text'), surface],
    ['accent as text, on the sheet', token('--accent-text'), sheet],
    ['accent as text, on the chrome', token('--accent-text'), chrome],
    ['white on the accent fill', token('--on-accent'), token('--nb-bind')],
    ['the record, on the surface', token('--nb-seal'), surface],
    ['the correction stamp, on the surface', token('--nb-stamp'), surface],
    ['body text on the accent wash', token('--nb-ink'), token('--accent-wash')],
];

for (const [what, fg, bg] of checks) {
    const r = ratio(fg, bg);
    assert.ok(r >= 4.5, `${what}: ${fg} on ${bg} is ${r.toFixed(2)}:1, needs 4.5:1`);
}

/* The conversation must read as a sheet lying on a desk, not as one flat
   field — the exact failure the light theme was redesigned to fix. The pair
   that carries that is the surface against the chrome around it; the light
   theme separates those by 1.14, so the dark one should not do much less.
   (Sheet against chrome is a hair either way in both themes, ~1.04, and is
   not what the eye is reading.) */
assert.ok(ratio(surface, chrome) >= 1.08,
    `the conversation does not separate from the chrome: ${ratio(surface, chrome).toFixed(3)}`);
assert.ok(ratio(surface, sheet) >= 1.03,
    `the conversation does not separate from the ground: ${ratio(surface, sheet).toFixed(3)}`);

console.log(`palette ok — ${checks.length} pairs clear 4.5:1`);
