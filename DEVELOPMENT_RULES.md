# Development Rules — ErikrafT Drop™ for Android

These rules apply to human contributors and automated agents/models working on this repository.

## 1. Audit Before Implementation

This is an existing application. **Do not recreate existing functionality when an implementation is already available.**

Before changing code:

1. Search the repository and inspect the current implementation.
2. Identify the existing components, activities, services, functions, APIs, assets and integrations involved.
3. Determine what is working, incomplete, broken, legacy or inconsistent.
4. Reuse and evolve the existing implementation whenever technically possible.
5. Make the smallest safe change that solves the task.
6. Preserve unrelated behavior, integrations, UI, compatibility and architecture.
7. Do not replace a working technology or architecture merely because another option appears preferable.
8. Do not add redundant dependencies, frameworks or services.
9. If an existing implementation is partially correct, fix or extend it instead of discarding it.
10. Rebuild from scratch only when the existing implementation is technically unusable, incompatible or unsafe to correct; document the reason.

### Priority

**SEARCH → UNDERSTAND → REUSE → CORRECT → EXTEND → REFACTOR IF NECESSARY → CREATE ONLY WHEN NO REUSABLE IMPLEMENTATION EXISTS.**

Future agents/models must follow this order regardless of model, tool or coding preference.

## 2. Diagnose Before Changing

Do not implement speculative fixes. First identify:

- the actual code path;
- files and components involved;
- existing reusable implementation;
- root cause or confirmed gap;
- required changes;
- files that must not be changed.

Separate unrelated findings from the current task. Create a separate Issue when an out-of-scope problem needs follow-up.

## 3. Issues and Pull Requests

Every correction, improvement or new feature must be traceable to a specific GitHub Issue and Pull Request.

The Issue should document:

- problem/objective;
- current behavior;
- expected behavior;
- scope and non-scope;
- acceptance criteria.

The Pull Request must:

- explicitly reference the Issue;
- explain what and why changed;
- identify affected files/components;
- explain how existing functionality was reused or extended;
- list validation/tests actually executed;
- identify relevant risks or compatibility considerations;
- avoid unrelated changes and unnecessary broad refactors.

Never claim a test, build, runtime verification or review was performed unless it actually was.

## 4. Documentation

Update the most appropriate existing documentation when possible. Do not duplicate project guidance. If no suitable document exists, add one appropriate Markdown document and keep the rules centralized.

## 5. UI, Motion and Accessibility

For UI work, audit existing loading, skeleton, lazy-loading, entry/exit animation, transition, progress and asynchronous feedback states before adding anything.

- Preserve correct existing motion.
- Fix broken motion before adding another implementation.
- Avoid redundant or decorative animation that does not improve the interaction.
- Keep motion performant and accessible.
- Respect `prefers-reduced-motion` where web UI/CSS motion is involved.
- Preserve the project's existing visual identity rather than rebuilding the interface.

When a repository-specific or applicable Motion Principles skill/guidance is available, consult it before changing motion behavior.

## 6. Observability

Audit existing observability before introducing instrumentation.

Use an existing suitable solution when one already exists. Consider Sentry, Datadog, New Relic or OpenTelemetry only when the architecture and operational need justify them; never install all of them automatically.

Prioritize useful signals such as:

- exceptions and errors;
- network failures;
- critical operations;
- performance measurements;
- relevant metrics;
- traces;
- actionable logs.

Avoid duplicate instrumentation and sensitive-data logging.

## 7. Quality and Lint

Audit the repository's current quality tooling and CI before adding tools. Check what is configured, what actually runs, what is broken/outdated and what gaps exist.

Use tools such as Checkstyle, Arch-contract, Biome, Commitlint, Knip or Stryker only when they are appropriate to the repository and task. Do not introduce multiple tools for the same responsibility without a technical reason.

For this Android repository, preserve and evolve the existing Gradle, Android Lint and Checkstyle setup rather than replacing it unnecessarily.

## 8. Tests and Regression Prevention

Before creating tests, search for tests covering the same behavior. Preserve the existing strategy and add focused regression coverage when practical.

For a bug:

1. reproduce or identify the incorrect behavior;
2. fix the existing implementation;
3. add/update a focused regression test when the architecture permits;
4. execute the relevant checks.

Use unit, integration or end-to-end testing according to the affected layer. Playwright/Codecov are not requirements by themselves; introduce them only when appropriate to the project and task.

If the repository has no existing test coverage for a layer and adding a test would require a disproportionate architectural rewrite, document that limitation instead of rebuilding the test architecture.

## 9. Compatibility and Regression

Consider, where applicable:

- desktop/mobile and supported Android form factors;
- screen sizes and orientations;
- supported browsers/WebView behavior;
- accessibility;
- performance and resource usage;
- loading/error/offline states;
- asynchronous operations;
- existing integrations and data flows.

Do not break unrelated functionality to implement a new behavior.

## 10. Dependency and Architecture Discipline

Prefer existing dependencies and architecture. Before adding a dependency, verify that the project does not already provide the required capability and that the dependency is compatible with the current build, packaging and supported platforms.

Native Android dependencies must also be validated in the produced artifact when native packaging is part of the task.

## 11. Completion Criteria

A task is complete only when, as applicable:

- the existing implementation was audited first;
- the solution was incremental where possible;
- unrelated behavior was preserved;
- unnecessary duplication was avoided;
- relevant tests/checks were executed and honestly reported;
- lint/quality checks were executed when applicable;
- required documentation was updated;
- the Issue is explicitly linked to the PR;
- the PR clearly documents the implementation and validation;
- no dead or unrelated code was introduced.

For release-sensitive changes, preserve the requested application version unless a version bump is explicitly part of the task.
