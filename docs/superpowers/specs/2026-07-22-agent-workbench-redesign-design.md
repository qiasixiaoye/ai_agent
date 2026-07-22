# Agent Workbench Redesign Design

Date: 2026-07-22
Status: approved approach, pending written-spec review

## Goal

Rebuild the Vue frontend into a unified Agent Workbench modeled after Codex and Claude Code style clients. The first screen should be the usable workbench, not a marketing home page or a collection of demo pages.

The workbench must bring chat, tools, Skills, MCP runtime, memory, context diagnostics, observability, knowledge base, and workflow builder into one coherent operator surface. The implementation should reuse the existing Spring Boot APIs first, and only add thin frontend orchestration or small API adapters where current endpoints are insufficient.

## Scope

In scope:

- Replace the current page-per-demo information architecture with one application shell.
- Rework chat as the central task workspace, including normal chat, RAG chat, and agent mode.
- Surface tools, Skills, MCP runtime state, memory, context diagnostics, workflow, knowledge, and observability through consistent workbench panels.
- Add visible permission/risk state for tool calls and MCP invocation.
- Add memory controls for automatic semantic-memory write suggestions, manual writes, and context preview.
- Add clear loading, empty, error, high-risk confirmation, and timeout states.
- Improve frontend state boundaries so tool/Skill/runtime/context data is not duplicated across large view files.

Out of scope for this phase:

- Replacing the Spring AI runtime or MCP governance implementation.
- Building a full multi-tenant RBAC system.
- Replacing vector memory, scoring, or context selection algorithms.
- Adding a new UI component library unless implementation proves the current stack cannot support the required interface.
- Rewriting Dify, workflow execution, or observability backend behavior.

## Current Constraints

The real project root is `C:\Users\lmh\Desktop\ai_agent\ai_agent`. The frontend lives in `vs-agent-web` and already uses Vue 3, Vite, Vue Router, Pinia, axios, and local CSS tokens. There is no dedicated component library.

Existing APIs already cover the important runtime surfaces:

- Assistant SSE chat, RAG chat, and Manus agent chat.
- Skill list/detail/execute, route preview, and routing evaluation.
- MCP runtime health, tool catalog refresh, managed invocation, and confirmation flag.
- Conversation memory, semantic-memory writes, context preview, and context diagnostics.
- Agent platform tool/task execution.
- Knowledge base documents, workflow builder, Dify bridge, and observability queries.

The current frontend problem is not only visual polish. It is that related capabilities are split into separate demo-style pages with repeated headers, inconsistent styling, and no shared operator context. Chat, tools, Skills, runtime governance, memory, and observability should feel like different panes of the same client.

## Proposed Architecture

Use a single workbench shell:

- Left rail: workspace navigation, current conversation selector, mode switch, runtime health indicator.
- Main pane: the active workspace route. Chat is the default route. Tools, Skills, Runtime, Memory, Workflow, Knowledge, and Observability remain routable but share the same shell.
- Right inspector: context and execution side panel. It follows the active conversation and shows memory, context budget, selected tools/skills, permissions, recent calls, trace ids, and diagnostics.

The shell should replace the old home-first flow. `/` should land on the workbench chat, with optional internal tabs or routes for the other modules. Existing routes can remain as compatibility aliases, but they should render inside the new shell or redirect to the matching workbench section.

Frontend layers:

- `layouts/WorkbenchLayout.vue`: owns the left rail, main slot, right inspector, and responsive drawer behavior.
- `components/workbench/*`: shared UI pieces for rail items, status chips, command bar, inspector sections, split panes, and confirmation rows.
- `stores/workbench.js`: current conversation id, selected mode, active panel, inspector state, permission preferences, and recent invocation summaries.
- `stores/runtime.js`: MCP health, tool catalog, permission/risk metadata, circuit state if exposed, and managed invocation status.
- `stores/skills.js`: skill catalog, selected skill, route preview, evaluation result, and execution state.
- `stores/memory.js`: conversation memory, context preview, diagnostics, semantic write suggestions, manual write state, and auto-write preference.
- Existing `stores/chat.js`: keep message storage but extend it to support mode metadata, trace hints, and post-response memory suggestions.

## Main User Experience

Chat becomes the workbench center. The user can select Normal, RAG, or Agent mode from a compact segmented control. Messages should use a restrained client style: readable transcript, compact metadata, collapsible tool or retrieval details, and visible stop/error states.

The right inspector updates as the chat progresses:

- Context: estimated token budget, recent history, summary, semantic memory, episodic/tool memory, RAG, Skills, and tool-schema allocations when diagnostics are available.
- Memory: current conversation memory, manual write box, suggested memory write after useful responses, and auto-write toggle.
- Tools: available tools and recent calls, grouped by source such as local tools, MCP, platform tools, and Skills.
- Permissions: risk level, confirmation requirement, last allowed/blocked action, and reason.
- Trace: latest request id/session id when observability returns data.

Tools and Skills should be usable from both their dedicated panels and from the chat context. A user should be able to search a tool or Skill, inspect its inputs, run it, and see the result without leaving the workbench.

## Permission and Range Model

This phase models permission as a visible frontend governance layer over existing backend policy. The UI should not pretend to enforce more than the backend supports.

Permission levels:

- Safe: run directly and log the result.
- Confirm: show a clear confirmation row before invoking with `confirmed: true`.
- Blocked or unavailable: disable invocation and show the backend reason.
- Unknown: require explicit confirmation and label the source as not classified.

Scope display:

- Tool source: local, MCP, platform, Skill, workflow, or Dify.
- Operation type: read, generate, execute, external call, file-like output, or unknown.
- Conversation scope: whether the call affects only the current session, shared memory, external runtime, or a workflow import/run.

The frontend will persist local display preferences such as "collapse safe calls" or "show permission detail", but it will not create a bypass for backend confirmation.

## Context and Memory Behavior

Memory should be surfaced as a workbench capability, not hidden in a runtime demo page.

Manual memory:

- User can add semantic memory for the current conversation from the inspector.
- User can clear conversation memory from a deliberate danger action.
- User can preview context composition before asking a question.

Automatic memory suggestion:

- After an assistant response completes, the frontend derives a candidate memory suggestion from the final user message and assistant answer.
- The suggestion appears as "Write to memory?" with editable text and importance.
- Auto-write can be enabled per browser session, but the first implementation should default to suggestion-first. Fully silent memory writes are deferred unless the backend has explicit policy support.

Context optimization:

- Before sending, the user can open diagnostics for the current message and mode.
- Diagnostics should show budget pressure and which context buckets are likely active.
- If the context is overloaded, the UI should suggest a narrower mode or memory cleanup rather than silently hiding the issue.

## Data Flow

Chat flow:

1. User selects mode and sends a message.
2. `stores/chat.js` records the user message and opens the existing SSE endpoint for Normal, RAG, or Agent.
3. Streaming tokens update the active assistant message.
4. On completion, the workbench store records the call summary, refreshes context diagnostics if enabled, and creates a memory-write suggestion.
5. If a trace id is available from response metadata or follow-up observability lookup, the inspector links the message to trace details.

Tool and Skill flow:

1. User searches or selects a tool/Skill from command bar, inspector, or dedicated panel.
2. The relevant store loads schema/detail data from existing APIs.
3. Input form is generated from known args where possible, with JSON fallback for unknown shapes.
4. Before invocation, the permission row resolves risk and confirmation state.
5. Result renders as structured JSON, plain text, file link, or execution timeline depending on payload shape.
6. Recent invocation summary is added to the inspector.

Memory/context flow:

1. Current conversation id is shared by chat, memory, context diagnostics, and inspector.
2. Memory panel loads conversation memory and context preview on demand.
3. User-approved semantic writes call the existing memory endpoint.
4. Diagnostics calls use current conversation id, query, and mode.
5. UI never claims memory was written unless the API confirms success.

## Error Handling

The frontend should standardize errors across modules:

- Network or backend unavailable: show module-level offline state with retry.
- SSE interrupted: keep partial assistant text, mark response incomplete, allow retry.
- Tool high-risk confirmation missing: show confirmation prompt and do not auto-retry.
- Tool timeout or circuit open: show failed status, backend reason, and trace context if available.
- JSON input invalid: validate before request and keep the user's input.
- Memory write failure: keep the suggestion editable and mark the write as failed.
- Empty catalogs: show actionable empty states for Skills, MCP tools, knowledge documents, and workflows.

Error text should be concrete and operational. It should not hide backend reasons behind generic "request failed" messages.

## Visual Direction

The UI should be dense, calm, and repeat-use friendly:

- Use a restrained neutral base with clear accent colors for state, not large glowing gradients.
- Avoid marketing hero sections, oversized cards, decorative bokeh/orbs, and nested cards.
- Use 8px or smaller radii for panels and cards.
- Use compact tables/lists for tool catalogs, invocation history, and diagnostics.
- Use icons for common commands and text labels only where command meaning would be ambiguous.
- Keep Chinese copy concise and professional.
- Preserve responsive behavior: desktop has persistent side panels; mobile collapses rail and inspector into drawers.

## Component Boundaries

The first implementation plan should avoid one giant replacement file. The target boundaries are:

- Layout owns frame and responsiveness.
- Route views own page-level composition only.
- Stores own API calls and loading/error state.
- Shared workbench components own visual primitives and repeated interaction patterns.
- Existing service API file remains the transport boundary.

Large current views such as `Home.vue`, `AssistantApp.vue`, `AgentPlatform.vue`, `Skills.vue`, and `RuntimeManagement.vue` should be split or wrapped incrementally. The priority is to make the workbench usable without breaking existing endpoints.

## Testing and Verification

Implementation should be verified at three levels:

- Static: frontend build must pass.
- Behavior: chat modes can send and stream; tool/Skill/runtime/memory panels can load data; invalid JSON and failed requests show clear states.
- Visual: run the dev server and inspect desktop and mobile widths for overlapping text, broken layout, and blank panels.

If backend services are unavailable during frontend verification, the frontend should still be checked for graceful loading and error states, and that limitation should be reported.

## Acceptance Criteria

- Opening the frontend lands in the unified workbench, not a marketing-style home page.
- Chat, Tools, Skills, Runtime, Memory, Workflow, Knowledge, and Observability are accessible from one shell.
- The right inspector shows context/memory/tool/permission state tied to the current conversation.
- Tool and Skill invocation share a consistent run/confirm/result/error pattern.
- Memory manual write and suggestion-first auto-write are visible and API-backed.
- Context diagnostics are available from the chat context and memory panel.
- Existing backend APIs remain compatible.
- Frontend build passes.
- Desktop and mobile visual checks show no obvious broken layout or text overlap.

## Implementation Sequence Preview

The next phase should create a detailed plan in this order:

1. Add shared workbench layout and base components.
2. Normalize tokens and remove conflicting global app styling.
3. Move chat into the workbench main pane.
4. Add inspector with memory/context/tool/permission sections.
5. Convert Tools, Skills, Runtime, Memory, Workflow, Knowledge, and Observability into workbench panels.
6. Add store-level orchestration for memory suggestions, diagnostics, permissions, and recent calls.
7. Verify build and visual behavior.

