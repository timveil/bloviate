/**
 * rehype plugin: turn ```mermaid fenced code blocks into `<pre class="mermaid">`
 * elements holding the raw graph source. The diagrams are then rendered in the
 * browser by the loader registered in astro.config.mjs (Mermaid is imported
 * lazily from a CDN, and only on pages that actually contain a diagram — so the
 * build stays dependency-free and Cloudflare never has to run a headless
 * browser). Keeping the source as plain Markdown also keeps it GitHub-readable.
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

/** Is this a <pre><code class="language-mermaid"> block? */
function isMermaidPre(node) {
	if (node.type !== 'element' || node.tagName !== 'pre' || !node.children) {
		return false;
	}
	const code = node.children.find((c) => c.type === 'element' && c.tagName === 'code');
	const classes = code?.properties?.className ?? [];
	return Array.isArray(classes) && classes.includes('language-mermaid');
}

function transform(node) {
	if (!node.children) {
		return;
	}
	node.children = node.children.map((child) => {
		if (isMermaidPre(child)) {
			return {
				type: 'element',
				tagName: 'pre',
				properties: { className: ['mermaid'] },
				children: [{ type: 'text', value: textOf(child).replace(/\n$/, '') }],
			};
		}
		transform(child);
		return child;
	});
}

export default function rehypeMermaid() {
	return (tree) => transform(tree);
}
