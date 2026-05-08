# GitHub Copilot — Repository Instructions

## Branch Protection Rules

**Never push directly to `main`.** All changes must go through a Pull Request.

| Rule | Detail |
|------|--------|
| Direct push to `main` | **Forbidden.** No exceptions. |
| Merging to `main` | Via Pull Request only. |
| Branch naming | `feat/`, `fix/`, `chore/`, `refactor/`, `test/`, `docs/` prefix required. |
| PR review | At least one approval required before merge. |

### Mandatory Workflow

1. Create a branch from `main`: `git checkout -b <prefix>/<short-description>`
2. Commit changes on that branch.
3. Push the branch: `git push --set-upstream origin <branch-name>`
4. Open a Pull Request targeting `main`.
5. Await review and approval before merging.

> When asked to commit and push code, always branch from `main` and open a PR.
> Never run `git push origin main` or any equivalent that bypasses the PR process.

## Project

This is a Java/Spring Boot assessment project for Allwage — a clock-in/clock-out service with WhatsApp notifications.

- Language: Java 17+
- Build: Maven (`pom.xml`)
- Entry point: `clockin-assessment-starter/starter-project/`
