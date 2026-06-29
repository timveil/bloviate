/**
 * rehype plugin: turn ```dot (or ```graphviz) fenced code blocks into
 * `<pre class="graphviz">` elements holding the raw DOT source. They're rendered
 * in the browser by the loader in astro.config.mjs (Graphviz-WASM, imported
 * lazily from a CDN, only on pages that contain a graph) — so the build stays
 * dependency-free and the DOT stays the single source of truth: readable as text
 * on GitHub, rendered as a diagram on the site. Used to showcase the actual DOT
 * Bloviate's DatabaseFiller emits.
 */

/** Extract the concatenated text content of a hast node. */
function textOf(node) {
	if (node.type === 'text') {
		return node.value;
	}
	if (node.children) {
		return node.children.map(textOf).join('');
	}
	return '';
}

/** Is this a <pre><code class="language-dot|language-graphviz"> block? */
function isDotPre(node) {
	if (node.type !== 'element' || node.tagName !== 'pre' || !node.children) {
		return false;
	}
	const code = node.children.find((c) => c.type === 'element' && c.tagName === 'code');
	const classes = code?.properties?.className ?? [];
	return Array.isArray(classes) && (classes.includes('language-dot') || classes.includes('language-graphviz'));
}

function transform(node) {
	if (!node.children) {
		return;
	}
	node.children = node.children.map((child) => {
		if (isDotPre(child)) {
			return {
				type: 'element',
				tagName: 'pre',
				properties: { className: ['graphviz'] },
				children: [{ type: 'text', value: textOf(child).replace(/\n$/, '') }],
			};
		}
		transform(child);
		return child;
	});
}

export default function rehypeGraphviz() {
	return (tree) => transform(tree);
}
