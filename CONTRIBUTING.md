# Development and Maintenance Rules

These rules apply to human contributors and automated coding agents, regardless of the model or tool being used.

## Core rule: do not rebuild what already exists

Before changing the project:

1. **Search** for the existing implementation.
2. **Understand** its architecture, dependencies, callers, lifecycle, and behavior.
3. **Reuse** working components and integrations.
4. **Correct** the existing implementation when it is incomplete or broken.
5. **Extend** it incrementally when the requirement can be satisfied safely.
6. **Refactor** only when necessary to make the requested change safe or maintainable.
7. **Create from scratch** only when the existing implementation is technically unusable or cannot be corrected safely; document the reason in the PR.

Do not replace an existing implementation merely because another library, framework, architecture, or coding style appears preferable.

## Issue and Pull Request workflow

Every bug fix, improvement, or new feature should have a focused GitHub Issue and Pull Request.

The Issue should describe the problem/objective, current behavior, expected behavior, scope, and acceptance criteria. The PR must reference the Issue and document:

- what changed and why;
- which files/components changed;
- how existing functionality was preserved or reused;
- tests and quality checks executed;
- known risks and compatibility considerations.

Do not mix unrelated fixes or broad refactors into a focused PR. Problems discovered outside the requested scope should normally become separate Issues.

## UI and Motion

When UI work is required, follow the project's applicable Motion Principles and preserve existing motion before adding anything new.

For each affected component, consider loading/skeleton states, asynchronous progress, entry/exit transitions, state changes, and useful feedback. Do not add animation indiscriminately. Avoid duplicate animations, performance-heavy effects, and inaccessible motion. Respect `prefers-reduced-motion` where the relevant UI layer supports it.

The goal is to evolve the existing UI and interaction model, not replace its identity.

## Observability

Audit the existing observability before adding instrumentation. Reuse the project's current logging, error reporting, metrics, traces, and diagnostics where possible.

Sentry, Datadog, New Relic, OpenTelemetry, or another service may be considered only when there is a concrete architectural need. Do not add multiple tools that provide overlapping telemetry without justification.

Prioritize actionable errors, exceptions, network failures, performance regressions, critical operations, and useful diagnostics.

## Quality and lint

Inspect the repository's existing quality tooling and CI before introducing new tools. Do not install overlapping tools simply because they are available.

Potential tools include Arch-contract, Biome, Commitlint, Knip, and Stryker/Stryker Mutator, but each must be justified by an actual project need and compatible with the existing toolchain.

## Testing and regression prevention

Preserve the existing test strategy. Before adding a test, search for tests covering the same component or flow.

For bug fixes:

1. identify or reproduce the incorrect behavior;
2. make the smallest safe correction to the existing implementation;
3. add or update a regression test when practical;
4. run the relevant unit, integration, UI, or end-to-end checks.

Use Playwright or other existing end-to-end infrastructure for critical UI flows when appropriate. Do not rewrite the entire test suite for a focused fix.

## Compatibility

Changes must consider the project's supported Android versions, screen sizes, accessibility, performance, loading/error/offline states, asynchronous operations, and existing integrations.

For Android changes, preserve the established AndroidX/Material architecture and test the oldest supported Android version as well as a current Android version when device/emulator infrastructure is available.

## Assets and branding

Do not invent replacement brand assets when an official or already-established project asset exists.

For Tor/Onion branding, use the established Tor/Onion asset or glyph source and its documented brand treatment. The Tor Project's current brand assets and trademark guidance are authoritative references:

- https://styleguide.torproject.org/brand-assets/
- https://www.torproject.org/about/trademark/

Do not redraw, approximate, recolor, distort, or otherwise modify a Tor mark merely to make a new icon. If an official asset must be converted into a platform-specific format, preserve its source geometry and branding faithfully and document the source in the PR.

## Completion criteria

A task is complete only when:

- the existing implementation was audited first;
- the change is incremental where possible;
- unrelated functionality is preserved;
- unnecessary duplication is absent;
- relevant tests and quality checks were run, or any unavailable validation is explicitly documented;
- required documentation is updated;
- the Issue and PR are linked;
- the PR explains the implementation and validation;
- no dead code or unrelated changes were introduced.
