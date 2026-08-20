# Regnum companion AI handoff standard

Every Regnum repository must contain a root `AGENTS.md`. Vector-Regnum's
handoff is the detailed reference; companion handoffs use this smaller common
contract and add only repository-specific boundaries and invariants.

## Required sections

1. **Read first** — `README.md`, numbered `ROADMAP.md` when present, relevant
   local docs, and `hostname; whoami` before machine-specific actions.
2. **Current checkpoint** — distinguish confirmed implementation from plans.
   A scaffold must say it is a scaffold and must not imply build, commands,
   tests, or gameplay exist.
3. **Subagent collaboration** — a named ordered ladder, not an open choice.
   Here that is opencode deepseekflash (`opencode/deepseek-v4-flash-free`,
   `--variant max`) first, Luna max second, Sol xhigh or Opus 5 medium only when
   extremely necessary. Bounded non-overlapping files; the parent owns
   integration, documentation, and the full verification ladder, and verifies
   subagent output rather than trusting a summary.
4. **Repository and machine boundaries** — exact public remote and local path;
   identify any owned ports, services, servers, or remote worktrees. If none
   exist, say so and forbid borrowing another project's workflow implicitly.
5. **Scope and integration boundary** — state what the repository owns and
   which versioned APIs it may consume or provide. Do not duplicate another
   Regnum project's implementation.
6. **Required verification ladder** — documentation checks immediately;
   project-native automated checks once a scaffold exists; isolated dedicated
   server/client and direct visual inspection for player-visible milestones.
   Commands must be documented before agents rely on them.
7. **Regression and safety invariants** — server authority, bounded work/data,
   permission and consent rules, versioned persistence/interfaces,
   accessibility, and repository-specific promises.
8. **Keeping the handoff current** — update README/ROADMAP/scripts, exact test
   counts/evidence, and the matching Obsidian project hub in the same pass.

## Canonical queue rule

Before a repository has a numbered `ROADMAP.md`, its README is authoritative
for scope and first milestone. Once a numbered roadmap is added, it becomes the
canonical work order. Never report a checked item as unfinished, and never mark
a stub complete without an end-to-end path, bounded failure behavior, tests,
and visual confirmation when player-visible.

## Common safety boundary

- Run `hostname; whoami` before acting on stored paths.
- Never touch port 25565, production/modpack servers, tmux Minecraft sessions,
  unrelated saves, or another repository's service/port without explicit
  authorization and documented ownership.
- Keep gameplay server-authoritative and treat client input as untrusted.
- Bound packet/data size, frequency, range, lifetime, persistence, and work.
- A generated screenshot is not visual verification until it is opened and the
  requested behavior is inspected.
- Preserve unrelated user changes and do not publish without intentional scope.
