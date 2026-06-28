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

## Develop

```bash
pnpm install
pnpm dev      # http://localhost:4321 — runs sync-docs first
pnpm build    # outputs static site to dist/
pnpm preview  # serve the production build locally
```

Requires Node 22+ (see `.node-version`) and pnpm (pinned via the `packageManager` field
in `package.json` — `corepack enable` will provision the right version).

## API reference

There is no bundled API reference on the site (yet). Cloudflare's build image can't run Java, so a
source-generated reference would have to be committed or built in CI. The lowest-friction path is to
publish Bloviate to **Maven Central**, which makes [javadoc.io](https://javadoc.io) auto-host the
Javadoc (and unlocks Context7 / sources-jar IDE hovers) with no build or CI. Once that's live, add a
**Reference** group in `astro.config.mjs` linking to
`https://javadoc.io/doc/io.bloviate/bloviate-core`.

In the meantime, the guides carry the API-level detail inline.

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
