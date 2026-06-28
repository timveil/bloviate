// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import rehypeDocIcons from './src/plugins/rehype-doc-icons.mjs';

// https://astro.build/config
export default defineConfig({
	site: 'https://bloviate.io',
	markdown: {
		// Replace emoji glyphs in docs with Starlight icon SVGs at build time.
		rehypePlugins: [rehypeDocIcons],
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
				// API reference: deferred. Once Bloviate is on Maven Central,
				// javadoc.io auto-hosts the Javadoc (no build/CI) — add a Reference
				// group here linking to https://javadoc.io/doc/io.bloviate/bloviate-core.
			],
		}),
	],
});
