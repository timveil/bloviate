/**
 * OpenGraph card renderer.
 *
 * Builds a 1200×630 social-share image for a page with Satori (HTML/CSS → SVG)
 * and rasterizes it to PNG with sharp (already a site dependency). The card is
 * styled from the Bloviate brand tokens in src/styles/bloviate.css — violet
 * gradient, coral accent, dark surface, the speech-bubble mark, and the
 * lowercase wordmark — so social previews match the site.
 *
 * Fonts are vendored as static .woff under src/assets/og (Satori needs an
 * explicit font and cannot parse a variable font's `fvar` table), keeping the
 * build self-contained — no network fetch, no native rasterizer.
 */
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import satori from 'satori';
import sharp from 'sharp';

const OG_WIDTH = 1200;
const OG_HEIGHT = 630;

// Read from the project root rather than an import.meta.url-relative path: the
// bundler relocates this module into dist/.prerender/chunks at build time, which
// would break a relative resolve. `astro build` always runs from the site root.
const font = (file: string) => readFileSync(join(process.cwd(), 'src/assets/og', file));
const FONT_REGULAR = font('space-grotesk-400.woff');
const FONT_BOLD = font('space-grotesk-700.woff');

// Brand palette (mirrors src/styles/bloviate.css).
const INK = '#16151d';
const INK_2 = '#221d36';
const VIOLET = '#8a6bff';
const VIOLET_LIGHT = '#a48bff';
const CORAL = '#ff6b5c';
const TEXT = '#f5f5f7';
const TEXT_DIM = '#b9a8ff';

/** Satori works on a tiny React-element-shaped tree; this avoids a JSX setup. */
type Node = { type: string; props: Record<string, unknown> };
const h = (type: string, props: Record<string, unknown>, ...children: unknown[]): Node => ({
	type,
	props: { ...props, children: children.length <= 1 ? children[0] : children },
});

/** The speech-bubble logo mark, rebuilt from divs so it needs no embedded SVG/font. */
function mark(): Node {
	const bar = (width: number, color: string, opacity = 1) =>
		h('div', { style: { width, height: 9, borderRadius: 5, background: color, opacity } });
	return h(
		'div',
		{
			style: {
				display: 'flex',
				flexDirection: 'column',
				justifyContent: 'center',
				gap: 8,
				width: 76,
				height: 76,
				paddingLeft: 16,
				borderRadius: 20,
				background: `linear-gradient(160deg, ${VIOLET_LIGHT}, ${VIOLET})`,
			},
		},
		bar(44, CORAL),
		bar(36, '#ffffff', 0.95),
		bar(24, '#ffffff', 0.82),
	);
}

export interface OgCard {
	/** Small label above the title (e.g. "Guide", "API Reference"). */
	eyebrow?: string;
	title: string;
	description?: string;
}

/** Render one OG card to a PNG buffer. */
export async function renderOgImage({ eyebrow, title, description }: OgCard): Promise<Buffer> {
	const tree = h(
		'div',
		{
			style: {
				display: 'flex',
				flexDirection: 'column',
				width: '100%',
				height: '100%',
				padding: '72px 80px',
				background: `radial-gradient(900px 500px at 78% -8%, ${INK_2}, ${INK})`,
				color: TEXT,
				fontFamily: 'Space Grotesk',
			},
		},
		// Header: mark + wordmark.
		h(
			'div',
			{ style: { display: 'flex', alignItems: 'center', gap: 24 } },
			mark(),
			h('div', { style: { fontSize: 40, fontWeight: 700, letterSpacing: -1 } }, 'bloviate'),
		),
		// Body: eyebrow + title + description, vertically centered.
		h(
			'div',
			{ style: { display: 'flex', flexDirection: 'column', flexGrow: 1, justifyContent: 'center' } },
			eyebrow
				? h(
						'div',
						{
							style: {
								display: 'flex',
								fontSize: 24,
								fontWeight: 700,
								letterSpacing: 4,
								textTransform: 'uppercase',
								color: VIOLET_LIGHT,
								marginBottom: 18,
							},
						},
						eyebrow,
					)
				: null,
			h(
				'div',
				{ style: { display: 'flex', fontSize: 70, fontWeight: 700, lineHeight: 1.1, letterSpacing: -1.5 } },
				title,
			),
			description
				? h(
						'div',
						{
							style: {
								display: 'flex',
								fontSize: 30,
								lineHeight: 1.4,
								marginTop: 26,
								color: TEXT_DIM,
								// Clamp long descriptions to keep the card balanced.
								maxWidth: 940,
							},
						},
						description,
					)
				: null,
		),
		// Footer: accent rule + domain.
		h(
			'div',
			{ style: { display: 'flex', alignItems: 'center', gap: 20 } },
			h('div', { style: { width: 64, height: 6, borderRadius: 3, background: CORAL } }),
			h('div', { style: { display: 'flex', fontSize: 26, color: TEXT, opacity: 0.85 } }, 'bloviate.io'),
		),
	);

	const svg = await satori(tree as unknown as Parameters<typeof satori>[0], {
		width: OG_WIDTH,
		height: OG_HEIGHT,
		fonts: [
			{ name: 'Space Grotesk', data: FONT_REGULAR, weight: 400, style: 'normal' },
			{ name: 'Space Grotesk', data: FONT_BOLD, weight: 700, style: 'normal' },
		],
	});

	return sharp(Buffer.from(svg)).png().toBuffer();
}
