/**
 * Sync the repository's Markdown docs into the Starlight content collection.
 *
 * The repo keeps its docs as plain GitHub-readable Markdown under ../docs (no
 * frontmatter). Starlight requires a `title` in frontmatter, so rather than
 * editing every doc, this script derives the title from the first H1 and writes
 * frontmatter'd copies into src/content/docs/guides/. The output dir is
 * gitignored — it is a build artifact, never edited by hand.
 *
 *   ../docs/*.md  ->  src/content/docs/guides/<slug>.md
 *
 * Doc scoping convention: only TOP-LEVEL ../docs/*.md is user-facing and synced.
 * Contributor/build/release docs (if any) live in subdirectories like
 * ../docs/contributing/ and are not synced.
 */
import { readdirSync, readFileSync, writeFileSync, mkdirSync, rmSync } from 'node:fs';
import { join, resolve, extname } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = fileURLToPath(new URL('.', import.meta.url));
const websiteDir = resolve(scriptDir, '..');
const repoRoot = resolve(websiteDir, '..');

const docsDir = join(repoRoot, 'docs');
const guidesOut = join(websiteDir, 'src', 'content', 'docs', 'guides');

/** Turn a file name like DATABASE_SUPPORT.md into "database-support". */
function slugify(fileName) {
	return fileName
		.slice(0, fileName.length - extname(fileName).length)
		.toLowerCase()
		.replace(/[^a-z0-9]+/g, '-')
		.replace(/^-+|-+$/g, '');
}

/** Remove a leading HTML comment block (e.g. a DO-NOT-EDIT banner). */
function stripLeadingComment(md) {
	return md.replace(/^\s*<!--[\s\S]*?-->\s*/, '');
}

/**
 * Rewrite relative links between sibling guides for the site.
 *
 * In the canonical ../docs/*.md these are written as `./OTHER.md` (or
 * `OTHER.md`) so they resolve on GitHub. On the site each guide lives at
 * `/guides/<slug>/`, so rewrite them to that route. Links to root-level docs
 * (`../ARCHITECTURE.md`) and absolute URLs are intentionally left untouched —
 * those are authored as full GitHub URLs in the docs.
 */
const GUIDE_LINK_RE = /\]\(\.\/([A-Za-z0-9_-]+)\.md(#[^)]*)?\)/g;
function rewriteGuideLinks(md) {
	return md.replace(GUIDE_LINK_RE, (_full, name, hash = '') => `](/guides/${slugify(`${name}.md`)}/${hash})`);
}

/**
 * Derive a meta description from the first prose paragraph of a doc.
 *
 * Starlight otherwise falls back to the site-wide description on every page,
 * which SEO crawlers flag as duplicate meta descriptions. We take the first
 * real paragraph (skipping headings, lists, tables, blockquotes, HTML and code
 * fences), strip Markdown to plain text, and truncate at a word boundary.
 */
function deriveDescription(bodyLines) {
	const para = [];
	let inFence = false;
	for (const raw of bodyLines) {
		const line = raw.trim();
		if (line.startsWith('```')) {
			inFence = !inFence;
			if (para.length) break;
			continue;
		}
		if (inFence) continue;
		if (line === '') {
			if (para.length) break;
			continue;
		}
		// Skip non-prose blocks (headings, tables, lists, quotes, raw HTML).
		if (/^(#{1,6}\s|[|>]|[-*+]\s|\d+\.\s|<)/.test(line)) {
			if (para.length) break;
			continue;
		}
		para.push(line);
	}
	let text = para
		.join(' ')
		.replace(/\[([^\]]+)\]\([^)]*\)/g, '$1') // [text](url) -> text
		.replace(/[`*_]/g, '') // inline code / emphasis markers
		.replace(/\s+/g, ' ')
		.trim();
	if (text.length > 160) {
		text = `${text.slice(0, 157).replace(/\s+\S*$/, '')}…`;
	}
	return text;
}

/**
 * Build the Starlight page: strip a leading comment, pull the first H1 out to
 * use as the frontmatter title (avoiding a duplicate heading), derive a
 * per-page meta description from the first paragraph, prepend frontmatter.
 */
function toStarlightPage(md, fallbackTitle) {
	const lines = stripLeadingComment(md).split('\n');
	let title = fallbackTitle;
	for (let i = 0; i < lines.length; i++) {
		const match = lines[i].match(/^#\s+(.+?)\s*$/);
		if (match) {
			title = match[1];
			lines.splice(i, 1);
			break;
		}
	}
	const body = lines.join('\n').replace(/^\n+/, '');
	const description = deriveDescription(lines);
	const frontmatter = [`title: ${JSON.stringify(title)}`];
	if (description) {
		frontmatter.push(`description: ${JSON.stringify(description)}`);
	}
	return `---\n${frontmatter.join('\n')}\n---\n\n${body}`;
}

function resetDir(dir) {
	rmSync(dir, { recursive: true, force: true });
	mkdirSync(dir, { recursive: true });
}

/** Sync the top-level guide docs into the Starlight content collection. */
function syncGuides() {
	const written = [];
	for (const entry of readdirSync(docsDir, { withFileTypes: true })) {
		if (!entry.isFile() || !entry.name.endsWith('.md')) {
			continue;
		}
		const slug = slugify(entry.name);
		const raw = rewriteGuideLinks(readFileSync(join(docsDir, entry.name), 'utf8'));
		writeFileSync(join(guidesOut, `${slug}.md`), toStarlightPage(raw, slug), 'utf8');
		written.push(slug);
	}
	return written;
}

resetDir(guidesOut);
const guides = syncGuides();

if (guides.length === 0) {
	console.warn(`[sync-docs] warning: no guide docs found in ${docsDir}`);
}
console.log(`[sync-docs] synced ${guides.length} guide(s): ${guides.join(', ')}`);
