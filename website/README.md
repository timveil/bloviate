# Bloviate docs & marketing site

The [bloviate.io](https://bloviate.io) site — an [Astro](https://astro.build) +
[Starlight](https://starlight.astro.build) project. The canonical documentation is plain,
GitHub-readable Markdown in [`../docs`](../docs); this project renders it (plus a marketing landing
page) into a static site.

## How it works

- **Content source of truth:** `../docs/*.md` (each a normal Markdown file with a single `# Title`).
- **Sync step:** `scripts/sync-docs.mjs` runs automatically before `dev` and `build` (`predev` /
  `prebuild`). It copies each `../docs/<NAME>.md` into `src/content/docs/guides/<slug>.md`, adding
  the Starlight `title` frontmatter from the first `# H1` and rewriting `./OTHER.md` links between
  guides to their `/guides/<slug>/` routes. That generated `guides/` directory is gitignored — never
  edit it by hand; edit the files in `../docs` instead.
- **Landing page:** `src/content/docs/index.mdx` (the marketing splash — not synced from `../docs`).
- **Brand:** `src/styles/bloviate.css` + the logos in `src/assets/` are the single source of brand
  truth (violet primary, coral accent). The favicon is `public/favicon.svg`.
- **Emoji → icons:** `src/plugins/rehype-doc-icons.mjs` swaps a few emoji (✅ ❌ ⚠ 🚀 📖) for
  Starlight icon SVGs at build time.
- **Diagrams:** ` ```mermaid ` and ` ```dot ` (Graphviz) code blocks render client-side. Two small
  rehype plugins (`rehype-mermaid.mjs`, `rehype-graphviz.mjs`) tag the blocks, and lazy loaders in
  `astro.config.mjs` import Mermaid / Graphviz-WASM from a CDN only on pages that have a diagram —
  so the build stays dependency-free (Cloudflare never runs a headless browser) and the diagram
  source stays plain, GitHub-readable Markdown. Both are brand-themed (and dark-mode aware).

## Develop

```bash
pnpm install
pnpm dev      # http://localhost:4321 — runs sync-docs first
pnpm build    # outputs static site to dist/
pnpm preview  # serve the production build locally
```

Requires Node 22+ (see `.node-version`) and pnpm (pinned via the `packageManager` field
in `package.json` — `corepack enable` will provision the right version).

## API reference (Javadoc)

The full API reference is the project's **Javadoc**, generated from source and served from this
site:

- **Landing page:** `src/content/docs/reference/index.mdx` — an on-brand Starlight page (in the
  sidebar and in site search) that introduces the API by package and links into the Javadoc.
- **The Javadoc itself:** committed static HTML under `public/apidocs/`, served at **`/apidocs/`**.
  It has its own per-class search and is themed to match the site (see below).

### Why it's committed, not built in CI

Cloudflare Pages' build image can't run Java, so the Javadoc is generated **locally** and the HTML
is committed. `notimestamp` keeps regenerated output diff-clean, so commits show only real API
changes. Regenerate after changing the public API and commit the result:

```bash
# from the repository root
./mvnw -Pjavadoc-site javadoc:aggregate
git add website/public/apidocs
```

The `javadoc-site` Maven profile (in the root `pom.xml`) runs `javadoc:aggregate` across the public
modules (core, junit, testcontainers, datafaker — benchmarks is excluded) into `public/apidocs/`.

### Theming

`javadoc/bloviate-javadoc.css` is layered onto the JDK stylesheet via javadoc's `--add-stylesheet`.
Modern javadoc is driven entirely by CSS custom properties, so the file just restates them in the
brand palette (violet primary, coral accent, Space Grotesk headings) and adds a `prefers-color-scheme`
dark block so the reference tracks the OS like the dark-first guides. The profile also injects a
breadcrumb strip (`-top`) linking back to the docs. Keep it in sync with `src/styles/bloviate.css`.

> Search isn't polluted: Starlight marks its own pages with `data-pagefind-body`, so Pagefind indexes
> only the guides/landing pages — the Javadoc uses its own built-in search.

## Deploy — Cloudflare Pages (Git integration, no CI)

The site deploys via **Cloudflare Pages' native Git integration** — Cloudflare watches the repo and
builds on push. There is deliberately **no GitHub Actions workflow, no `wrangler`, and no GitHub
secrets**. One-time dashboard setup:

1. **Cloudflare Pages → Create → Connect to Git →** select the `timveil/bloviate` repo, production
   branch `main`.
2. **Build settings:**
   - Framework preset: **Astro**
   - **Root directory:** `website`
   - **Build command:** `pnpm build`
   - **Build output directory:** `dist`

   Cloudflare auto-detects the package manager from the committed `pnpm-lock.yaml` and runs
   `pnpm install` before the build.
3. **Environment variables:** add `NODE_VERSION` = `22` (Astro 6 needs Node ≥ 18.20 / 20.3 / 22;
   also pinned via `website/.node-version`).
4. **Custom domain:** add `bloviate.io` (and a `www` → apex redirect) under the Pages project's
   *Custom domains*.

On each push to `main`, Cloudflare checks out the repo, runs `pnpm build` inside `website/`
(which first runs `sync-docs.mjs` against `../docs`), and publishes `website/dist`. Path filters
aren't needed — a rebuild is cheap and always reflects the latest `docs/`, `website/`, and content.
