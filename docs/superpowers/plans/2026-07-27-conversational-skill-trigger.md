# Conversational Skill Trigger Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Execute registered Skills from the chat page by explicit selection or automatic routing.

**Architecture:** Add one chat-facing Skill facade over `SkillRegistry` and `SkillRouter`; it returns the real `SkillResult` and route mode. The chat page calls this facade for manual selection and auto-route hits, while ordinary chat remains unchanged on a miss.

**Tech Stack:** Spring Boot, Java 21, Vue 3, Pinia, Axios.

## Global Constraints

- Use the existing `Skill.execute(Map, SkillContext)` contract; no frontend mock outputs.
- Manual selection must never fall through silently to model chat.
- Auto-route misses must fall through to the existing chat connection.

### Task 1: Chat-oriented Skill facade

**Files:** `skill/controller/ConversationalSkillController.java`, `ConversationalSkillControllerTest.java`

- [ ] Write tests for a manual `astro-shoot-plan` invocation, an automatic matched route, and a route miss.
- [ ] Add `POST /skills/conversation/execute` accepting `message`, `skillName`, `arguments`, and `conversationId`; manual name wins, otherwise select the first router match, and return `mode`, `skillName`, `SkillResult`, and route decision.
- [ ] Run the focused backend test and commit.

### Task 2: Chat sample and routing client

**Files:** `vs-agent-web/src/services/api.js`, `vs-agent-web/src/views/AssistantApp.vue`, `vs-agent-web/scripts/conversational-skill.test.mjs`

- [ ] Write a Node test that verifies the sample payload uses `astro-shoot-plan` and structured latitude/longitude/date arguments.
- [ ] Add a picker plus sample action; manual selection posts the selected Skill, while a route preview before ordinary send triggers the same API only for a matched route.
- [ ] Render returned Skill output and mode in the current conversation; run the Node test and Vite build, then commit.
