# IntelliJ AI Commit Message Generator Prompt

Use this prompt in IntelliJ IDEA's AI commit message generator to ensure all commits follow the Conventional Commits specification for proper semantic versioning.

## Prompt for IntelliJ AI Assistant

```
Generate a commit message following the Conventional Commits specification (https://www.conventionalcommits.org/). 

**Format:** `<type>[optional scope]: <description>`

**Rules:**
1. Use one of these types based on the change:
   - `feat:` - new features or capabilities (triggers MINOR version bump)
   - `fix:` - bug fixes (triggers PATCH version bump) 
   - `perf:` - performance improvements (triggers PATCH version bump)
   - `refactor:` - code restructuring without behavior changes (triggers PATCH version bump)
   - `build:` - build system or dependency changes (triggers PATCH version bump)
   - `docs:` - documentation only changes (NO release)
   - `style:` - formatting, missing semicolons, etc. (NO release)
   - `test:` - adding or updating tests (NO release)
   - `ci:` - CI configuration changes (NO release)
   - `chore:` - maintenance tasks (NO release)
   - `revert:` - reverting previous commits (triggers PATCH version bump)

2. Keep description under 72 characters, imperative mood, no period
3. Use lowercase after the colon
4. Add optional scope in parentheses if needed: `feat(database): add PostgreSQL support`
5. For breaking changes, add `!` after type/scope: `feat!: remove deprecated API`

**Examples:**
- `feat: add comprehensive Javadoc documentation`
- `fix: resolve connection pooling memory leak`
- `perf: optimize database query performance`
- `refactor: improve error handling in data generators`
- `build: upgrade Maven dependencies to latest versions`
- `docs: update README with new installation instructions`
- `ci: add automated dependency updates workflow`

**This commit analysis:**
[Analyze the staged changes and suggest the most appropriate type and description]
```

## Usage Instructions

1. In IntelliJ IDEA, go to **VCS** → **Git** → **Commit**
2. Click the **AI Assistant** button (brain icon) next to the commit message field
3. Paste the prompt above into the AI assistant
4. The AI will analyze your staged changes and generate a properly formatted conventional commit message
5. Review and edit the suggestion as needed before committing

## Important Notes

- **feat:** commits trigger minor version releases (1.0.0 → 1.1.0)
- **fix/perf/refactor/build/revert:** commits trigger patch releases (1.0.0 → 1.0.1)  
- **docs/style/test/ci/chore:** commits do NOT trigger releases
- Breaking changes (with `!`) trigger major releases (1.0.0 → 2.0.0)
- Our semantic-release workflow automatically handles versioning, changelog generation, and package publishing based on these commit types

## Semantic Versioning Integration

This project uses semantic-release with the following workflow:
1. Analyze commits since last release
2. Determine version bump based on commit types
3. Update `pom.xml` version
4. Generate `CHANGELOG.md`
5. Deploy to GitHub Packages at `https://github.com/timveil/bloviate/packages/1122633`
6. Create GitHub release with auto-generated notes
7. Commit version changes back to repository