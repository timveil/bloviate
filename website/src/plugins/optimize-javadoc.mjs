/**
 * Astro integration: post-process the built Javadoc (dist/apidocs) for serving
 * on Cloudflare Pages. Runs on the build output only, so the committed
 * public/apidocs stays byte-identical to what javadoc generates.
 *
 * - Links: Pages 307-redirects every `*.html` request to its extensionless path
 *   (and `index.html` to its directory), so Javadoc's own `Foo.html` links would
 *   each cost a temporary redirect — thousands across the API reference, all
 *   reported by site audits. Point them at the served URLs instead. Links built
 *   at runtime by Javadoc's search scripts still use `.html`; they're never
 *   crawled, and a user click just follows the redirect.
 * - Assets: javadoc ships its scripts and stylesheets unminified; minify them.
 *   The vendored `*.min.*` files already are.
 */
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { transformSync } from 'esbuild';

// Relative hrefs only: absolute URLs (docs.oracle.com), root paths and bare
// fragments are left alone.
const RELATIVE_HTML_HREF_RE = /href="(?![a-z][a-z0-9+.-]*:|\/|#)([^"#?]*)\.html([#?][^"]*)?"/gi;

/** `Foo` -> `Foo`, `../../index` -> `../../`, `index` -> `./`. */
function servedPath(path) {
	if (!/(^|\/)index$/.test(path)) {
		return path;
	}
	return path.slice(0, -'index'.length) || './';
}

export function rewriteJavadocLinks(html) {
	return html.replace(RELATIVE_HTML_HREF_RE, (_full, path, suffix = '') => `href="${servedPath(path)}${suffix}"`);
}

/**
 * Minify one script or stylesheet. No output `format` is set, so esbuild keeps
 * top-level names: the pages' inline scripts call globals such as `loadScripts`
 * and `switchTab`, and the search index files assign globals the search reads.
 */
function minify(source, loader) {
	return transformSync(source, { loader, minify: true }).code;
}

function rewriteEach(root, files, transform) {
	for (const path of files) {
		const file = join(root, path);
		writeFileSync(file, transform(readFileSync(file, 'utf8'), path), 'utf8');
	}
}

export default function optimizeJavadoc() {
	return {
		name: 'bloviate:optimize-javadoc',
		hooks: {
			'astro:build:done': ({ dir, logger }) => {
				const apidocs = join(fileURLToPath(dir), 'apidocs');
				const files = readdirSync(apidocs, { recursive: true }).map(String);

				const pages = files.filter((path) => path.endsWith('.html'));
				rewriteEach(apidocs, pages, rewriteJavadocLinks);

				const assets = files.filter((path) => /\.(js|css)$/.test(path) && !/\.min\.(js|css)$/.test(path));
				rewriteEach(apidocs, assets, (source, path) => minify(source, path.endsWith('.css') ? 'css' : 'js'));

				logger.info(`Rewrote links in ${pages.length} Javadoc pages; minified ${assets.length} scripts/stylesheets`);
			},
		},
	};
}
