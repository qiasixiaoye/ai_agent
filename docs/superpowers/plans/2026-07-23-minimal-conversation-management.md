# Minimal Conversation Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add local-first create, select, rename, pin, categorize, clear, and delete controls for workbench conversations while preserving Tool, Skill, MCP, and memory behavior.

**Architecture:** Store conversation metadata beside the current transcript in `useChatStore`; retain it through the existing workbench snapshot sanitizer. Keep lifecycle rules in a small utility so refresh restoration and final-deletion fallback are testable. Render a compact conversation panel inside the existing chat workspace and delegate all mutations to the store.

**Tech Stack:** Vue 3, Pinia, Node built-in test runner, existing Vite workbench build.

## Global Constraints

- Do not change API endpoint paths, request payloads, Tool, Skill, MCP, or memory-store calls.
- Keep professional terms Tool, Skill, MCP, and Agent in English; use Chinese explanatory copy.
- Preserve v1 `localStorage` snapshot compatibility; missing metadata defaults to the existing conversation mode and `pinned: false`.
- Keep the existing welcome-message copy for the first conversation and for a newly created conversation.
- Limit the implementation to the chat store, persistence helpers, chat workspace, and regression tests.

---

### Task 1: Add metadata persistence and conversation lifecycle contracts

**Files:**
- Modify: `vs-agent-web/scripts/workbench-regressions.test.mjs`
- Modify: `vs-agent-web/src/stores/chat.js`
- Modify: `vs-agent-web/src/utils/workbenchPersistence.js`

**Interfaces:**
- Consumes: existing `assistantAppChats` records keyed by id.
- Produces: `createConversation(mode, metadata)`, `updateConversation(chatId, patch)`, `clearConversation(chatId)`, `deleteConversation(chatId)`, and persisted `category`/`pinned` fields.

- [ ] **Step 1: Write the failing test**

```js
test('workbench persistence preserves conversation category and pin state', () => {
  const snapshot = buildWorkbenchSnapshot({
    workbench: { currentConversationId: 'conv-1' },
    chats: { 'conv-1': { id: 'conv-1', title: '研究 MCP', mode: 'agent', category: 'agent', pinned: true, messages: [] } }
  })
  assert.equal(snapshot.conversations[0].category, 'agent')
  assert.equal(snapshot.conversations[0].pinned, true)
})
```

- [ ] **Step 2: Run the regression file to verify it fails**

Run: `node scripts/workbench-regressions.test.mjs` from `vs-agent-web`.

Expected: FAIL because the snapshot omits `category` and `pinned`.

- [ ] **Step 3: Implement the smallest compatible metadata support**

```js
// workbenchPersistence.js inside normalizeConversation
category: conversation.category || conversation.mode || 'normal',
pinned: Boolean(conversation.pinned),
```

```js
// chat.js action signatures
createConversation(mode = 'normal', metadata = {})
updateConversation(chatId, patch = {})
clearConversation(chatId)
deleteConversation(chatId)
```

`clearConversation` only replaces `messages` with `[]` and updates `updatedAt`. `deleteConversation` returns whether deletion occurred; it does not make any API call.

- [ ] **Step 4: Run the regression file to verify it passes**

Run: `node scripts/workbench-regressions.test.mjs` from `vs-agent-web`.

Expected: PASS with all existing tests plus the metadata persistence assertion.

- [ ] **Step 5: Commit the durable data contract**

```bash
git add vs-agent-web/scripts/workbench-regressions.test.mjs vs-agent-web/src/stores/chat.js vs-agent-web/src/utils/workbenchPersistence.js
git commit -m "feat: persist conversation management metadata"
```

### Task 2: Make selection and empty-state fallback testable

**Files:**
- Modify: `vs-agent-web/scripts/workbench-regressions.test.mjs`
- Create: `vs-agent-web/src/utils/chatSession.js`
- Modify: `vs-agent-web/src/views/workbench/ChatWorkspace.vue`

**Interfaces:**
- Consumes: `chatStore.assistantAppChats`, `chatStore.createConversation`, `chatStore.addMessage`, and `workbench.currentConversationId`.
- Produces: `resolveChatSession({ chatStore, workbench, welcomeMessage })` returning `{ chatId, created }`.

- [ ] **Step 1: Write the failing tests**

```js
test('chat session selects a restored conversation before creating one', () => {
  const result = resolveChatSession({ chatStore, workbench, welcomeMessage: '欢迎' })
  assert.equal(result.chatId, 'restored')
  assert.equal(chatStore.created, 0)
})

test('chat session creates exactly one welcome conversation when none remain', () => {
  const result = resolveChatSession({ chatStore, workbench, welcomeMessage: '欢迎' })
  assert.equal(result.created, true)
  assert.equal(chatStore.messages[0].content, '欢迎')
})
```

- [ ] **Step 2: Run the regression file to verify it fails**

Run: `node scripts/workbench-regressions.test.mjs` from `vs-agent-web`.

Expected: FAIL because `resolveChatSession` does not exist.

- [ ] **Step 3: Implement the session resolver and use it in the chat workspace**

```js
export const resolveChatSession = ({ chatStore, workbench, welcomeMessage }) => {
  const chats = chatStore.assistantAppChats || {}
  const activeId = workbench.currentConversationId
  const restoredId = chats[activeId] ? activeId : Object.values(chats)
    .sort((a, b) => String(b.updatedAt).localeCompare(String(a.updatedAt)))[0]?.id
  const chatId = restoredId || chatStore.createConversation('normal')
  workbench.setConversation(chatId)
  if (!restoredId) chatStore.addMessage(chatId, { content: welcomeMessage, isUser: false, status: 'complete' })
  return { chatId, created: !restoredId }
}
```

Replace the current `onMounted` create-or-reuse branch with this resolver. Preserve Agent history rebuilding after selection.

- [ ] **Step 4: Run the regression file to verify it passes**

Run: `node scripts/workbench-regressions.test.mjs` from `vs-agent-web`.

Expected: PASS; no extra chat is created after reload and a final deletion can safely fall back to a welcome chat.

- [ ] **Step 5: Commit restoration behavior**

```bash
git add vs-agent-web/scripts/workbench-regressions.test.mjs vs-agent-web/src/utils/chatSession.js vs-agent-web/src/views/workbench/ChatWorkspace.vue
git commit -m "fix: restore and create chat sessions safely"
```

### Task 3: Add minimal visible conversation controls

**Files:**
- Modify: `vs-agent-web/src/views/workbench/ChatWorkspace.vue`
- Modify: `vs-agent-web/scripts/workbench-regressions.test.mjs`

**Interfaces:**
- Consumes: Task 1 lifecycle store actions and Task 2 session resolver.
- Produces: New conversation, select, rename, pin, clear, and delete interactions with no Tool/Skill/MCP/memory API calls.

- [ ] **Step 1: Write the failing behavioral test**

```js
test('clearing a conversation retains its id and category while deleting removes it', () => {
  chatStore.clearConversation('conv-1')
  assert.equal(chatStore.assistantAppChats['conv-1'].category, 'rag')
  assert.deepEqual(chatStore.assistantAppChats['conv-1'].messages, [])
  chatStore.deleteConversation('conv-1')
  assert.equal(chatStore.assistantAppChats['conv-1'], undefined)
})
```

- [ ] **Step 2: Run the regression file to verify it fails**

Run: `node scripts/workbench-regressions.test.mjs` from `vs-agent-web`.

Expected: FAIL until the store actions are available and preserve metadata.

- [ ] **Step 3: Render and wire the focused control set**

Add a compact `会话` panel above the transcript with:

```vue
<button type="button" @click="createNewConversation">新建</button>
<button type="button" @click="renameCurrentConversation">重命名</button>
<button type="button" @click="toggleCurrentPin">置顶</button>
<button type="button" @click="clearCurrentConversation">清空</button>
<button type="button" @click="deleteCurrentConversation">删除</button>
```

Render conversations as grouped lists by mode/category: `最近`, `普通对话`, `知识库`, `Agent 任务`. Use `window.prompt` only for the minimal title/category input and `window.confirm` before clear/delete. Reuse the current mode when creating a conversation, set it in the workbench store, and add the existing welcome message. After deleting the selected conversation, call `resolveChatSession` to select a remaining one or create a new default.

- [ ] **Step 4: Run tests and production validation**

Run from `vs-agent-web`:

```bash
node scripts/workbench-regressions.test.mjs
npm.cmd run validate:workbench
npm.cmd run build
```

Expected: all regression tests pass, `WORKBENCH_STRUCTURE_OK`, and the production build exits 0.

- [ ] **Step 5: Commit and update the local Docker UI**

```bash
git add vs-agent-web/src/views/workbench/ChatWorkspace.vue vs-agent-web/scripts/workbench-regressions.test.mjs
git commit -m "feat: add local conversation management"
docker cp vs-agent-web/dist/. vs-agent-web:/usr/share/nginx/html
docker exec vs-agent-web nginx -s reload
docker commit vs-agent-web vs-agent-web:latest
```

Verify `http://localhost:5174/` returns HTTP 200 after reload.
