# Security Policy

Bloviate is a Java library for generating dummy test data for JDBC-compatible
relational databases. It is intended for use in development, testing, and
benchmarking environments. The maintainers take security seriously and
appreciate responsible disclosure of any vulnerabilities.

## Supported Versions

Security fixes are released against the latest published version. We recommend
always running the most recent release.

| Version | Supported          |
| ------- | ------------------ |
| 2.15.x  | :white_check_mark: |
| < 2.15  | :x:                |

Because the project follows [semantic versioning](https://semver.org/) driven by
[Conventional Commits](https://conventionalcommits.org/), fixes are shipped in a
new patch or minor release rather than backported to older lines.

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues,
discussions, or pull requests.**

Instead, report them privately using GitHub's built-in
[private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability):

1. Go to the [**Security** tab](https://github.com/timveil/bloviate/security)
   of the repository.
2. Click **Report a vulnerability** to open a private advisory.
3. Provide the details described below.

If you are unable to use GitHub's private reporting, you may contact the
maintainer directly at **tjveil@gmail.com**.

### What to Include

A good report helps us triage and fix the issue quickly. Where possible, please
include:

- A description of the vulnerability and its potential impact.
- The affected version(s) and module(s) (`bloviate-core`, `bloviate-junit`,
  `bloviate-testcontainers`, `bloviate-datafaker`).
- Steps to reproduce, ideally a minimal proof of concept.
- Any relevant configuration, stack traces, or logs.
- Suggested remediation, if you have one.

## Disclosure Process

- We will acknowledge your report within **5 business days**.
- We will investigate, keep you informed of progress, and aim to provide an
  initial assessment within **10 business days**.
- Once a fix is available, we will publish a new release and, where warranted, a
  [GitHub Security Advisory](https://github.com/timveil/bloviate/security/advisories)
  with appropriate credit to the reporter (unless you prefer to remain
  anonymous).
- Please give us a reasonable opportunity to release a fix before any public
  disclosure.

## Scope

Bloviate generates randomized data and executes SQL against databases you
configure. Keep the following in mind:

- **Use against test/throwaway databases only.** Bloviate writes generated data
  into the tables you point it at. Do not run it against production systems or
  databases containing real or sensitive data.
- **Connection credentials and JDBC URLs are supplied by the caller.** Protect
  these as you would any other database secret; never commit them to source
  control.
- Reports concerning the security of the library itself — for example, unsafe
  SQL construction, dependency vulnerabilities, or unexpected data exfiltration —
  are in scope and welcome.

## Dependencies

This repository uses [Dependabot](.github/dependabot.yml) to keep Maven and
GitHub Actions dependencies up to date. If you spot a vulnerable transitive
dependency that automated tooling has missed, please report it using the process
above.
