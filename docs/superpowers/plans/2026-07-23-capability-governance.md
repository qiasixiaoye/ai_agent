# Capability Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Phase 1 capability governance for tools and Skills.

**Architecture:** Extend existing metadata classes, parse `SKILL.md` governance sections, add a read-only audit service/controller, and surface audit output in the capability workspace.

**Tech Stack:** Java 21, Spring Boot 3.4, JUnit 5, Vue 3, Pinia, Vite.

## Global Constraints

- Do not replace existing Tool, Skill, or MCP execution paths in Phase 1.
- The audit endpoint must be read-only.
- High-risk capability inference must be conservative.
- Front-end copy must be Chinese-facing.
- Keep interview notes in `docs/capability-governance-interview-notes.md`.

---

### Task 1: Metadata contracts and parser

**Files:**
- Modify: `src/main/java/com/vs/vsaiagent/skill/SkillMetadata.java`
- Modify: `src/main/java/com/vs/vsaiagent/skill/loader/SkillMdParser.java`
- Modify: `src/main/java/com/vs/vsaiagent/agentplatform/model/ToolMetadata.java`
- Create: `src/main/java/com/vs/vsaiagent/capability/governance/CapabilitySecurityContract.java`
- Create: `src/main/java/com/vs/vsaiagent/capability/governance/CapabilityEvaluationContract.java`
- Test: `src/test/java/com/vs/vsaiagent/skill/loader/SkillMdParserGovernanceTest.java`

**Interfaces:**
- Produces: `SkillMetadata.security()`, `SkillMetadata.evaluation()`, `ToolMetadata.getSecurity()`, `ToolMetadata.getEvaluation()`.

- [ ] Write a failing parser test for `security` and `evaluation` frontmatter.
- [ ] Run the parser test and confirm it fails because the fields do not exist.
- [ ] Add the contract records and metadata fields.
- [ ] Extend the parser to parse nested governance maps.
- [ ] Run the parser test and confirm it passes.

### Task 2: Audit service and endpoint

**Files:**
- Create: `src/main/java/com/vs/vsaiagent/capability/governance/CapabilityAuditIssue.java`
- Create: `src/main/java/com/vs/vsaiagent/capability/governance/CapabilityAuditReport.java`
- Create: `src/main/java/com/vs/vsaiagent/capability/governance/CapabilityGovernanceService.java`
- Create: `src/main/java/com/vs/vsaiagent/capability/governance/CapabilityGovernanceController.java`
- Test: `src/test/java/com/vs/vsaiagent/capability/governance/CapabilityGovernanceServiceTest.java`

**Interfaces:**
- Produces: `CapabilityGovernanceService.audit()`.
- Produces: `GET /capability-governance/audit`.

- [ ] Write a failing audit test for missing security contract, high-risk confirmation, and missing eval cases.
- [ ] Run the audit test and confirm it fails because the service does not exist.
- [ ] Implement conservative inference and audit issue generation.
- [ ] Add the REST endpoint.
- [ ] Run the audit service test and confirm it passes.

### Task 3: Front-end governance display

**Files:**
- Modify: `vs-agent-web/src/services/api.js`
- Modify: `vs-agent-web/src/utils/capabilityCatalog.js`
- Modify: `vs-agent-web/src/views/workbench/CapabilityWorkspace.vue`

**Interfaces:**
- Consumes: `/capability-governance/audit`.
- Produces: a Chinese-facing governance/audit panel in the capability workspace.

- [ ] Add an API helper for the audit endpoint.
- [ ] Normalize backend security/evaluation fields before front-end fallback inference.
- [ ] Display risk level, permission scopes, evaluation profile, review status, lifecycle status, and issue counts.
- [ ] Run the front-end build.

### Task 4: Verification and handoff

**Files:**
- Verify all files touched in Tasks 1-3.

- [ ] Run targeted Maven tests for parser and audit service.
- [ ] Run existing relevant MCP/Skill tests.
- [ ] Run front-end build.
- [ ] Review `git diff` and report only verified status.

