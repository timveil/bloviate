/**
 * Per-page OpenGraph images.
 *
 * Emits one static PNG per docs page at build time — `/og/<id>.png`, where
 * `<id>` is the page's collection id (e.g. `index`, `reference`,
 * `guides/architecture`). The matching <meta property="og:image"> is injected
 * by src/components/Head.astro using the same id, so the two always line up.
 */
import type { APIRoute, GetStaticPaths } from 'astro';
import { getCollection } from 'astro:content';
import { renderOgImage, type OgCard } from '../../og/og-template';

/** Keep descriptions short enough that the card stays balanced (no footer overlap). */
function clamp(text: string | undefined, max = 130): string | undefined {
	if (!text) return undefined;
	const t = text.trim();
	return t.length > max ? `${t.slice(0, max - 1).replace(/\s+\S*$/, '')}…` : t;
}

/** Short label shown above the title, derived from where the page lives. */
function eyebrowFor(id: string): string | undefined {
	if (id === 'index') return undefined;
	if (id === 'reference' || id.startsWith('reference/')) return 'API Reference';
	if (id.startsWith('guides/')) return 'Guide';
	return undefined;
}

export const getStaticPaths: GetStaticPaths = async () => {
	const docs = await getCollection('docs');
	return docs.map((entry) => {
		// The home page's frontmatter title is just "Bloviate"; give its card a
		// stronger headline than the bare brand name.
		const isHome = entry.id === 'index';
		const card: OgCard = {
			eyebrow: eyebrowFor(entry.id),
			title: isHome ? 'Hands-free test data for JDBC databases' : entry.data.title,
			description: clamp(isHome ? entry.data.hero?.tagline : entry.data.description),
		};
		return { params: { slug: entry.id }, props: { card } };
	});
};

export const GET: APIRoute = async ({ props }) => {
	const png = await renderOgImage(props.card as OgCard);
	return new Response(new Uint8Array(png), {
		headers: {
			'Content-Type': 'image/png',
			'Cache-Control': 'public, max-age=31536000, immutable',
		},
	});
};
