/**
 * Sync the repository's Markdown docs into the Starlight content collection,
 * and generate the LLM-facing index/full-text files from those same docs.
 *
 * The repo keeps its docs as plain GitHub-readable Markdown under ../docs (no
 * frontmatter). Starlight requires a `title` in frontmatter, so rather than
 * editing every doc, this script derives the title from the first H1 and writes
 * frontmatter'd copies into src/content/docs/guides/. All output is gitignored —
 * these are build artifacts, never edited by hand.
 *
 *   ../docs/*.md  ->  src/content/docs/guides/<slug>.md   (Starlight pages)
 *   ../docs/*.md  ->  public/llms.txt                     (curated LLM index)
 *   ../docs/*.md  ->  public/llms-full.txt                (full concatenated text)
 *
 * Generating llms.txt here (rather than hand-maintaining it) keeps it from
 * drifting: the guide list comes from the synced docs, and the supported-database
 * list is parsed from DATABASE_SUPPORT.md — the single source of truth — so adding
 * a database or a guide updates the LLM index automatically.
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
const publicDir = join(websiteDir, 'public');

const SITE = 'https://bloviate.io';

/** One-line project summary used as the llms.txt blockquote (stable; rarely changes). */
const SITE_SUMMARY =
	'Bloviate is a Java library that fills JDBC-compatible relational databases with realistic, ' +
	'reproducible dummy data. It auto-discovers your schema, infers a generator for every column ' +
	'from its JDBC type, respects foreign keys via topological ordering, and can also emit flat ' +
	'files (CSV, TSV, pipe-delimited). It never touches production data and is deterministic by seed.';

/**
 * Curated grouping, display title, and blurb for each guide slug in the LLM index.
 * Insertion order is the emission order. A synced doc with no entry here is still
 * listed (auto-titled, auto-described) under "Guides", so a new doc is never dropped.
 */
const GUIDE_META = {
	quickstart: { group: 'Guides', title: 'Quick Start', blurb: 'Install Bloviate and fill a database or flat file in a few lines.' },
	'database-support': { group: 'Guides', title: 'Database Support', blurb: 'Per-database type handling and how vendor-specific types are resolved.' },
	configuration: { group: 'Guides', title: 'Configuration', blurb: 'Control row counts, batch size, and per-column generators.' },
	generators: { group: 'Guides', title: 'Generators', blurb: 'Built-in generators and how to apply custom ones across columns or whole types.' },
	integrations: { group: 'Guides', title: 'Testing Integrations', blurb: 'JUnit 5 and Testcontainers integrations for reproducible test data.' },
	'file-generation': { group: 'Guides', title: 'File Generation', blurb: 'Generate CSV, TSV, or pipe-delimited files with no database involved.' },
	comparison: { group: 'Guides', title: 'Why Bloviate', blurb: 'Where Bloviate fits among test-data tools and when to use it.' },
	architecture: { group: 'Under the hood', title: 'Architecture', blurb: 'Schema discovery, foreign-key ordering, parallel fill, and deterministic seeding.' },
	benchmarks: { group: 'Under the hood', title: 'Benchmarks', blurb: 'CPU micro-benchmarks (JMH) and end-to-end fill throughput against real databases.' },
};
const GUIDE_ORDER = Object.keys(GUIDE_META);
const GROUP_ORDER = ['Guides', 'Under the hood'];

/** Non-doc reference links appended to the index. */
const REFERENCE_LINKS = [
	{ title: 'API Reference', url: `${SITE}/reference/`, blurb: 'Browse the Java API by package.' },
	{ title: 'Full Javadoc', url: `${SITE}/apidocs/index.html`, blurb: 'Complete generated Javadoc for the core engine and integrations.' },
];

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
 * Parse a raw doc into its pieces: pull the first H1 out for the title (so the
 * page doesn't repeat it), keep the remaining body, and derive a description.
 */
function parseDoc(md, fallbackTitle) {
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
	const body = lines.join('\n').replace(/^\n+/, '').trimEnd();
	return { title, description: deriveDescription(lines), body };
}

/** Build the frontmatter'd Starlight page from a parsed doc. */
function toStarlightPage({ title, description, body }) {
	const frontmatter = [`title: ${JSON.stringify(title)}`];
	if (description) {
		frontmatter.push(`description: ${JSON.stringify(description)}`);
	}
	return `---\n${frontmatter.join('\n')}\n---\n\n${body}\n`;
}

function resetDir(dir) {
	rmSync(dir, { recursive: true, force: true });
	mkdirSync(dir, { recursive: true });
}

/**
 * Sync the top-level guide docs into the Starlight content collection, returning
 * a parsed page object per doc for the LLM-index generators.
 */
function syncGuides() {
	const pages = [];
	for (const entry of readdirSync(docsDir, { withFileTypes: true })) {
		if (!entry.isFile() || !entry.name.endsWith('.md')) {
			continue;
		}
		const slug = slugify(entry.name);
		const raw = rewriteGuideLinks(readFileSync(join(docsDir, entry.name), 'utf8'));
		const parsed = parseDoc(raw, slug);
		writeFileSync(join(guidesOut, `${slug}.md`), toStarlightPage(parsed), 'utf8');
		pages.push({ slug, ...parsed });
	}
	// emit in the curated order; unlisted docs (no GUIDE_META entry) sort last, by slug
	return pages.sort((a, b) => {
		const ai = GUIDE_ORDER.indexOf(a.slug);
		const bi = GUIDE_ORDER.indexOf(b.slug);
		return (ai < 0 ? Infinity : ai) - (bi < 0 ? Infinity : bi) || a.slug.localeCompare(b.slug);
	});
}

/** Join a list with commas and a trailing "and": [a,b,c] -> "a, b and c". */
function joinList(items) {
	if (items.length <= 1) return items.join('');
	return `${items.slice(0, -1).join(', ')} and ${items[items.length - 1]}`;
}

/**
 * Parse the first ("Database") column of the DATABASE_SUPPORT.md table — the
 * single source of truth for supported databases — skipping the header, the
 * `---` separator, and the generic-default row. Returns [] if the table can't be
 * found, so callers can fall back to a non-enumerated sentence.
 */
function supportedDatabases() {
	let md;
	try {
		md = readFileSync(join(docsDir, 'DATABASE_SUPPORT.md'), 'utf8');
	} catch {
		return [];
	}
	const names = [];
	for (const line of md.split('\n')) {
		const cell = line.match(/^\|\s*([^|]+?)\s*\|/);
		if (!cell) continue;
		const name = cell[1].trim();
		if (!name || /^[-:\s]+$/.test(name) || name.toLowerCase() === 'database' || /generic/i.test(name)) {
			continue;
		}
		names.push(name);
	}
	return names;
}

/** Build the curated llms.txt index from the synced pages. */
function buildLlmsIndex(pages) {
	const dbs = supportedDatabases();
	const dbFact = dbs.length
		? `First-class type handling for ${joinList(dbs)}, plus a generic default for any other JDBC driver.`
		: 'First-class type handling for several JDBC databases, plus a generic default for the rest (see the Database Support guide).';

	const out = [`# Bloviate`, ``, `> ${SITE_SUMMARY}`, ``, `Key facts:`, ``];
	out.push(
		'- Java 25, multi-module Maven build: `bloviate-core` (dependency-free engine), `bloviate-junit` (JUnit 5 `@FillDatabase`/`@FillSource`), `bloviate-testcontainers` (fills a started `JdbcDatabaseContainer`).',
		`- ${dbFact}`,
		'- Deterministic output: per-column seeds derived from schema identity, backed by the JDK `L64X128MixRandom` generator.',
	);

	for (const group of GROUP_ORDER) {
		const inGroup = pages.filter((p) => (GUIDE_META[p.slug]?.group ?? 'Guides') === group);
		if (!inGroup.length) continue;
		out.push(``, `## ${group}`, ``);
		for (const p of inGroup) {
			const meta = GUIDE_META[p.slug];
			const title = meta?.title ?? p.title;
			const blurb = meta?.blurb ?? p.description;
			out.push(`- [${title}](${SITE}/guides/${p.slug}/): ${blurb}`);
		}
	}

	out.push(``, `## Reference`, ``);
	for (const ref of REFERENCE_LINKS) {
		out.push(`- [${ref.title}](${ref.url}): ${ref.blurb}`);
	}
	return `${out.join('\n')}\n`;
}

/** Build llms-full.txt: the project summary followed by every guide's full text. */
function buildLlmsFull(pages) {
	const out = [`# Bloviate — full documentation`, ``, `> ${SITE_SUMMARY}`];
	for (const p of pages) {
		const title = GUIDE_META[p.slug]?.title ?? p.title;
		out.push(``, `---`, ``, `# ${title}`, ``, p.body);
	}
	return `${out.join('\n')}\n`;
}

resetDir(guidesOut);
const pages = syncGuides();

if (pages.length === 0) {
	console.warn(`[sync-docs] warning: no guide docs found in ${docsDir}`);
}

writeFileSync(join(publicDir, 'llms.txt'), buildLlmsIndex(pages), 'utf8');
writeFileSync(join(publicDir, 'llms-full.txt'), buildLlmsFull(pages), 'utf8');

console.log(`[sync-docs] synced ${pages.length} guide(s): ${pages.map((p) => p.slug).join(', ')}`);
console.log('[sync-docs] generated public/llms.txt and public/llms-full.txt');
