# Conversational Skill Trigger Design

## Goal

Make Skills usable from the chat experience through both explicit user selection and automatic routing, while retaining the Skills workspace as an operations and debugging surface.

## Scope

The first end-to-end demonstration targets the existing `astro-shoot-plan` Skill. It is a real composite Skill: execution invokes `milkyway_rise`, `light_pollution`, and `cloud_cover`, then returns a combined plan.

## User Experience

The chat page gains a compact Skill picker and a one-click “银河拍摄计划” example. Selecting a Skill is an explicit instruction: the message is sent to the Skill execution endpoint with structured arguments, and the response displays the selected Skill, its internal tool steps, and final output in the active conversation.

For ordinary free-form messages, the frontend requests a route preview from the existing `SkillRouter`. When the router selects a Skill above its threshold, the same execution endpoint runs it and the response states that automatic routing selected the Skill. If routing selects nothing, the existing normal/RAG/Manus paths remain unchanged.

## Backend Contract

Add a chat-oriented controller facade that accepts `message`, optional `skillName`, optional `arguments`, and `conversationId`. It resolves manual selection first; otherwise it calls `SkillRouter`. It executes through the existing registered Skill abstraction rather than directly invoking internal tools. The response contains `mode` (`manual`, `auto`, or `chat`), selected Skill metadata, structured output, and the Skill’s real step evidence.

No frontend-only success states are introduced. Validation and execution remain backend-authoritative.

## Frontend Responsibilities

`AssistantApp.vue` owns picker state, derives a known sample payload for `astro-shoot-plan`, submits the chat-oriented Skill request, and renders returned trace events next to the assistant message. The existing Skills workspace remains unchanged and continues to expose raw JSON execution for operators.

## Failure Handling

Unknown Skill names, malformed JSON arguments, required parameters, router misses, and Skill failures are returned as explicit assistant-visible errors. Router misses are not errors: they fall through to normal chat. A manual Skill request never silently falls back to the model.

## Tests

Backend tests cover manual execution, automatic selection, router fallback, and invalid manual arguments. Frontend tests cover the sample payload and rendering helper behavior. Integration verification executes `astro-shoot-plan` with latitude `39.9042`, longitude `116.4074`, and date `2026-08-15`, asserting the three named tool steps are represented.

## Non-goals

This change does not make every Manus tool a Skill, replace the Skills workspace, or create a second routing algorithm. It reuses the existing Skill registry/router/executor contract.
