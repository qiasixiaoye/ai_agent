# Minimal Conversation Management Design

## Goal

Add a small, local-first conversation management layer to the AgentHub workbench so a user can create, name, categorize, clear, pin, select, and delete conversations without changing Tool, Skill, MCP, or memory service contracts.

## Scope and constraints

- The feature is front-end only and extends the existing Pinia chat store and `localStorage` workbench snapshot.
- Existing persisted conversations must remain readable after the change; missing metadata receives safe defaults.
- A cleared conversation keeps its identifier, title, mode, category, and pin state. Deleting a conversation removes its persisted record.
- `currentConversationId` must always reference an existing conversation after selection or deletion. When the final conversation is deleted, create a new normal conversation with the existing welcome message.
- Tool, Skill, MCP and memory invocation APIs, request payloads, and write triggers are not changed.
- Professional terms retain English: Tool, Skill, MCP, Agent. Surrounding UI copy remains Chinese.

## Product behavior

The chat header gains a compact "新建" action and an overflow action for rename, clear, delete, and pin. A conversation drawer/list groups entries into "最近", "普通对话", "知识库", and "Agent 任务". Each entry exposes title, mode, category, update time, and pin state. The selected conversation controls the existing transcript and the right-side inspector.

New conversations start with the existing assistant welcome message. Users can choose a category from the three built-in modes while creating or editing a conversation. The category is purely organizational: it does not alter a Tool/Skill/MCP permission, selected model, system prompt, tool calling, or memory behavior.

## Data model

Each `assistantAppChats[chatId]` record gains optional front-end metadata:

```js
{
  title: '未命名会话',
  mode: 'normal',
  category: 'normal', // normal | rag | agent
  pinned: false,
  messages: [],
  createdAt: 'ISO timestamp',
  updatedAt: 'ISO timestamp'
}
```

Persisted snapshots continue using the current `conversations` array. The existing sanitizer must preserve `category` and `pinned`, while old snapshots receive `category` from `mode` and `pinned: false` during restoration.

## Component boundaries

- `stores/chat.js`: creates, updates, clears, deletes, and selects durable conversation records; no UI markup.
- `utils/chatSession.js`: selects a valid restored conversation or creates the first one; it is pure/testable except for store calls.
- `views/workbench/ChatWorkspace.vue`: renders the list and header actions, delegates lifecycle changes to the store, and keeps streaming behavior unchanged.
- `utils/workbenchPersistence.js`: serializes/restores the two metadata fields only.
- `scripts/workbench-regressions.test.mjs`: verifies restore compatibility, clear/delete selection rules, and that category changes do not modify invocation/memory behavior.

## Error handling and UX

- Rename rejects blank titles in the UI and preserves the previous title.
- Delete uses a browser confirmation because it removes local persisted data.
- Clearing uses a browser confirmation and creates no Tool/Skill/MCP or memory call.
- If storage lacks the current id, the selector uses the most recently updated conversation. If none exists, it creates one welcome conversation.

## Acceptance checks

1. Refreshing `http://localhost:5174` preserves the current selected conversation and its metadata.
2. A user can create, select, rename, pin, clear, and delete a conversation from the chat workspace.
3. Clear retains its conversation id and category; delete chooses another existing conversation or creates one safe default conversation.
4. Existing calls to Tool, Skill, MCP, context preview, memory suggestion, and memory write retain their existing payloads and routes.
5. Node regression tests and the workbench validation/build commands pass.
