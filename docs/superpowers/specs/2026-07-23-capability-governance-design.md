# Capability Governance Design

## Goal

Build the first production-shaped governance layer for Agent tools and Skills: explicit safety contracts, evaluation contracts, audit output, and a front-end view that explains capability risk in Chinese.

## Scope

This phase covers metadata, parsing, audit, and presentation. It does not implement OS-level sandboxing, full runtime syscall tracing, CI/CD lifecycle automation, or automatic memory writes from bad cases.

## Architecture

The system keeps the existing three capability surfaces:

- Agent Platform Tool: backend atomic tools listed by `/agent-platform/tools`.
- Skill: executable Java `Skill` beans enriched by `SKILL.md` metadata.
- Managed MCP: externally provided tools governed by the existing MCP management layer.

Phase 1 adds a shared governance vocabulary to Tool and Skill metadata, then exposes an audit endpoint that front-end pages can consume. The audit is read-only and does not execute capabilities.

## Safety Contract

Each capability can declare:

- `riskLevel`: `LOW`, `MEDIUM`, `HIGH`.
- `permissionScopes`: `LOCAL_COMPUTE`, `EXTERNAL_READ`, `FILE_READ`, `FILE_WRITE`, `EXTERNAL_WRITE`, `SYSTEM_COMMAND`, `PRIVATE_DATA`, `PAYMENT`, `DEPLOY`.
- `sideEffects`: a short label such as `NONE`, `LOCAL_FILE`, `NETWORK`, `CROSS_SYSTEM`.
- `dataSensitivity`: `PUBLIC`, `USER_INPUT`, `PRIVATE`, `SECRET`.
- `requiresConfirmation`: whether the user must confirm before execution.
- `reviewStatus`: `UNREVIEWED`, `REVIEWED`, `NEEDS_FIX`, `BLOCKED`.
- `lifecycleStatus`: `DRAFT`, `BETA`, `ACTIVE`, `ARCHIVED`, `OFFLINE`.

If a capability does not declare a contract, the governance service infers a conservative default from name, tags, and source type. Dangerous patterns such as shell, terminal, delete, publish, deploy, payment, file write, or MCP routing are not treated as safe by default.

## Evaluation Contract

Each capability can declare:

- `profile`: `deterministic`, `functional`, or `creative`.
- `successCriteria`: human-readable success rules.
- `hardConstraints`: required output format, required fields, or forbidden behavior.
- `goldenCaseTags`: normal, boundary, exception, adversarial.
- `attributionStages`: call layer, execution layer, integration layer.

The evaluation contract is intentionally lightweight in Phase 1. It creates a stable structure for future automated eval runners without blocking existing tools.

## Positive and Negative Examples

The example library is capability-type oriented rather than tied to the current registry. It includes hypothetical tools and Skills so the system can evaluate risks before a concrete implementation exists.

Examples cover:

- positive deterministic tool calls;
- negative prompt-injection or parameter-injection calls;
- high-risk command/write/delete calls that must be blocked or confirmed;
- positive creative Skill outputs;
- negative creative Skill requests that ask the system to fabricate metrics or production claims.

This keeps the evaluation set useful for newly introduced capabilities, not only the four bundled Skills.

## Audit Rules

The audit service reports issues when:

- a capability has no safety contract;
- a high-risk capability does not require confirmation;
- permission scopes imply high risk but the declared risk is lower;
- a Skill has no evaluation contract;
- a Skill has no golden-case tags or success criteria;
- names or tags suggest dangerous behavior.

Audit output is grouped by severity and includes a concrete recommendation for each issue.

## Front-end Behavior

The capability page should stop relying only on front-end guesses. It should display backend governance fields when present, then fall back to local inference only when old metadata is missing.

The page should show:

- risk level;
- permission scope;
- evaluation profile;
- review/lifecycle status;
- audit issue count and issue detail.

Workbench session persistence should be owned by the layout-level shell. A page-level chat component is too narrow because refreshing a non-chat route skips the restore path.

## Interview Talking Point

The key interview answer is: “I separated capability governance into registration-time contracts, runtime policy decisions, evaluation attribution, and lifecycle feedback. This lets an Agent product scale from a few tools to many Skills without losing control over permissions, side effects, quality, and failure attribution.”
