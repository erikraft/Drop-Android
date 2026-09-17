# ErikrafT Drop™ — Development Rules

## 1. Audit first — do not rebuild existing functionality

This is an existing application. Human contributors and coding agents/models must inspect the current implementation before changing it.

Priority:

1. **Search** for the existing implementation.
2. **Understand** the current architecture and behavior.
3. **Reuse** working components, services, APIs and integrations.
4. **Correct** broken or incomplete existing code.
5. **Extend** the existing implementation when the requirement needs additional behavior.
6. **Refactor** only when technically necessary and keep the scope small.
7. **Create from scratch** only when the existing implementation is technically unusable or cannot be corrected safely.

Do not replace an existing implementation merely because another technology or architecture looks preferable.

Before implementation, document:

- where the feature currently lives;
- which files and components are involved;
- what works;
- what is broken, incomplete, legacy or inconsistent;
- what will be changed;
- what must remain untouched.

Avoid speculative changes and unrelated cleanup.

## 2. Issues and Pull Requests

Every correction, improvement or new feature should have a dedicated GitHub Issue or use an existing issue that precisely covers the work.

The Issue should describe:

- problem or objective;
- current behavior;
- expected behavior;
- scope;
- acceptance criteria.

Implementation belongs in a Pull Request that explicitly references the Issue.

A PR should explain:

- what changed and why;
- which files/components changed;
- how the existing implementation was preserved or reused;
- tests and quality checks executed;
- compatibility considerations and risks.

Do not mix unrelated refactors or architectural rewrites into a focused fix.

## 3. UI and Motion Principles

When UI work is applicable, inspect existing loading, transition and motion behavior before adding anything.

Prefer existing patterns for:

- skeleton/loading states;
- lazy loading;
- progress indicators;
- async feedback;
- state transitions;
- entry and exit animations.

Do not duplicate animations or reconstruct the interface unnecessarily. Keep the existing visual identity and behavior. Respect `prefers-reduced-motion` and avoid motion that harms performance or accessibility.

## 4. Observability

Audit existing observability before introducing a new service.

Use an existing suitable solution when available. Consider Sentry, Datadog, New Relic or OpenTelemetry only when there is a concrete architectural need.

Prioritize useful coverage of:

- exceptions and crashes;
- network failures;
- critical operations;
- performance;
- metrics;
- traces;
- logs.

Do not create duplicate instrumentation stacks.

## 5. Quality and lint

Inspect the project's existing quality tooling before adding another tool.

Potential tools include Arch-contract, Biome, Commitlint, Knip and Stryker/Stryker Mutator, but none should be introduced automatically.

First verify what is already configured and what actually runs in CI. Fix broken or stale tooling before adding redundant tooling.

## 6. Tests

Preserve and evolve the current test strategy.

Before writing a new test, search for existing coverage of the same functionality.

For a bug:

1. reproduce or identify the incorrect behavior;
2. fix the existing implementation;
3. add or update a focused regression test when practical;
4. run the relevant tests.

Use unit, integration and end-to-end tests, Codecov or Playwright only when appropriate to the existing architecture and task. Do not rewrite the whole test suite for one bug.

## 7. Compatibility and regression safety

Consider changes across:

- desktop and mobile;
- supported browsers and Android versions;
- different screen sizes;
- accessibility;
- performance;
- loading and error states;
- offline/network failure;
- asynchronous operations;
- existing integrations.

Do not break an unrelated existing feature to implement a new one. If an unrelated defect is discovered, record it separately instead of silently expanding scope.

## 8. Dependencies and native packaging

Do not add duplicate dependencies when an existing dependency can be reused.

For native Android libraries, verify the complete packaging path rather than assuming a Gradle dependency declaration is sufficient. Inspect the resolved artifact type, ABI layout and final APK/AAB contents when runtime code expects a specific native file.

For Onion Wrapper, the Android runtime expects the native files named `libtor.so` and `liblyrebird.so`. CI must validate the generated artifact rather than only checking Gradle configuration.

## 9. Completion criteria

A task is complete only when:

- the existing implementation was audited;
- the smallest safe incremental change was applied where possible;
- unrelated functionality was preserved;
- no unnecessary duplicate implementation was introduced;
- relevant tests were executed;
- relevant lint/quality checks were executed;
- required documentation was updated;
- the Issue and PR are linked;
- the PR clearly documents the change and validation;
- no dead code or unrelated changes were introduced.

## 10. Rule for all future agents/models

**PROCURE → ENTENDA → REUTILIZE → CORRIJA → ESTENDA → SÓ ENTÃO CRIE.**

The model used to implement a task does not change this rule. A different model, framework preference or newer technology is not by itself a reason to replace working project code.
