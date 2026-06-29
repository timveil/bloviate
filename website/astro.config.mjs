// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import sitemap from '@astrojs/sitemap';
import rehypeDocIcons from './src/plugins/rehype-doc-icons.mjs';
import rehypeMermaid from './src/plugins/rehype-mermaid.mjs';
import rehypeGraphviz from './src/plugins/rehype-graphviz.mjs';

// Client-side Mermaid loader. Imported lazily from a CDN and only on pages that
// actually contain a diagram, so the build stays dependency-free (Cloudflare
// never runs a headless browser) and other pages pay nothing. Themed to the
// brand palette, with a dark variant keyed off Starlight's data-theme.
const mermaidLoader = `
const els = [...document.querySelectorAll('pre.mermaid')];
if (els.length) {
	try {
		const dark = document.documentElement.dataset.theme === 'dark';
		const { default: mermaid } = await import('https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs');
		mermaid.initialize({
			startOnLoad: false,
			theme: 'base',
			fontFamily: "'Space Grotesk', system-ui, sans-serif",
			themeVariables: dark
				? {
					primaryColor: '#2a2342', primaryBorderColor: '#8a6bff', primaryTextColor: '#e6e6e9', lineColor: '#b9a8ff', secondaryColor: '#25252c', tertiaryColor: '#1b1b22', fontSize: '15px',
					// Bar/line charts (xychart-beta) are themed separately from flowcharts.
					xyChart: {
						backgroundColor: 'transparent',
						titleColor: '#e6e6e9',
						xAxisLabelColor: '#e6e6e9', xAxisTitleColor: '#e6e6e9', xAxisTickColor: '#54546a', xAxisLineColor: '#54546a',
						yAxisLabelColor: '#c9c9d4', yAxisTitleColor: '#e6e6e9', yAxisTickColor: '#54546a', yAxisLineColor: '#54546a',
						plotColorPalette: '#8a6bff',
					},
				}
				: {
					primaryColor: '#ede9ff', primaryBorderColor: '#7c5cff', primaryTextColor: '#1b1b1f', lineColor: '#5b3fd1', secondaryColor: '#f0edff', tertiaryColor: '#f7f5ff', fontSize: '15px',
					xyChart: {
						backgroundColor: 'transparent',
						titleColor: '#1b1b1f',
						xAxisLabelColor: '#1b1b1f', xAxisTitleColor: '#1b1b1f', xAxisTickColor: '#ccc2ff', xAxisLineColor: '#ccc2ff',
						yAxisLabelColor: '#3a3a44', yAxisTitleColor: '#1b1b1f', yAxisTickColor: '#ccc2ff', yAxisLineColor: '#ccc2ff',
						plotColorPalette: '#7c5cff',
					},
				},
		});
		// Render each diagram with an explicit, unique id. mermaid.run()'s
		// auto-ids are timestamp-derived and collide when diagrams render in the
		// same millisecond, which makes them share clip-paths/styles and overlap.
		for (let i = 0; i < els.length; i++) {
			const { svg } = await mermaid.render('blv-mermaid-' + i, els[i].textContent);
			els[i].innerHTML = svg;
			els[i].setAttribute('data-processed', 'true');
		}
	} catch (err) {
		// If Mermaid can't load/render, reveal the raw graph source (CSS hides
		// unprocessed diagrams) rather than leaving blank gaps on the page.
		els.forEach((el) => el.setAttribute('data-processed', 'error'));
		console.error('[mermaid] render failed', err);
	}
}
`;

// Client-side Graphviz (WASM) loader, same lazy-CDN pattern as Mermaid. Renders
// the actual DOT Bloviate emits; brand colors are applied via CSS so the on-page
// DOT stays byte-identical to what the dreampuf link carries.
const graphvizLoader = `
const gv = [...document.querySelectorAll('pre.graphviz')];
if (gv.length) {
	try {
		const { instance } = await import('https://cdn.jsdelivr.net/npm/@viz-js/viz@3/lib/viz-standalone.mjs');
		const viz = await instance();
		for (const el of gv) {
			el.replaceChildren(viz.renderSVGElement(el.textContent));
			el.setAttribute('data-processed', 'true');
		}
	} catch (err) {
		gv.forEach((el) => el.setAttribute('data-processed', 'error'));
		console.error('[graphviz] render failed', err);
	}
}
`;

// https://astro.build/config
export default defineConfig({
	site: 'https://bloviate.io',
	markdown: {
		// rehype-mermaid: turn ```mermaid blocks into <pre class="mermaid"> for the
		// client loader. rehype-doc-icons: swap doc emoji for Starlight icon SVGs.
		rehypePlugins: [rehypeMermaid, rehypeGraphviz, rehypeDocIcons],
	},
	integrations: [
		starlight({
			title: 'Bloviate',
			description:
				'Hands-free test data generator for JDBC databases — auto-discovers your schema, respects foreign keys, and fills tables or flat files with realistic, reproducible data.',
			logo: {
				light: './src/assets/bloviate-logo.svg',
				dark: './src/assets/bloviate-logo-dark.svg',
				replacesTitle: true,
			},
			favicon: '/favicon.svg',
			customCss: ['./src/styles/bloviate.css'],
			// Append the per-page OpenGraph image (og:image / twitter:image) to each
			// page's head. The images themselves are generated at build time by
			// src/pages/og/[...slug].png.ts (Satori → PNG).
			routeMiddleware: './src/og-route-middleware.ts',
			head: [
				// Lazy client-side diagram renderers (no-ops on pages without diagrams).
				{ tag: 'script', attrs: { type: 'module' }, content: mermaidLoader },
				{ tag: 'script', attrs: { type: 'module' }, content: graphvizLoader },
			],
			social: [
				{
					icon: 'github',
					label: 'GitHub',
					href: 'https://github.com/timveil/bloviate',
				},
			],
			sidebar: [
				{
					// Explicit order (Quick Start first). Add new guides here.
					label: 'Guides',
					items: [
						{ slug: 'guides/quickstart' },
						{ slug: 'guides/database-support' },
						{ slug: 'guides/configuration' },
						{ slug: 'guides/generators' },
						{ slug: 'guides/integrations' },
						{ slug: 'guides/file-generation' },
						{ slug: 'guides/comparison' },
					],
				},
				{
					label: 'Under the hood',
					items: [
						{ slug: 'guides/architecture' },
						{ slug: 'guides/benchmarks' },
					],
				},
				{
					label: 'Reference',
					items: [
						{ slug: 'reference' },
						// Brand-themed Javadoc, generated locally and committed under
						// public/apidocs (Cloudflare can't run Java). Regenerate with
						// `./mvnw -Pjavadoc-site javadoc:aggregate`.
						// Link the canonical directory URL, not /apidocs/index.html: Starlight
						// strips the .html, and Cloudflare then 307-redirects the bare path to
						// /apidocs/. Pointing straight at /apidocs/ avoids that per-page redirect.
						{ label: 'Full Javadoc', link: '/apidocs/', attrs: { target: '_blank' } },
					],
				},
			],
		}),
		// Emits sitemap-index.xml + sitemap-0.xml at build (uses `site` above);
		// referenced from public/robots.txt so crawlers discover every page.
		sitemap(),
	],
});
