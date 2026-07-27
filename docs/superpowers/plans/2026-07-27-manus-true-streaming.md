# Manus True Streaming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Manus's delayed fake stream with token-level typed SSE events and render the final answer separately from its execution trace.

**Architecture:** Retain the MVC `SseEmitter` endpoint and ReAct loop. A typed event emitter serializes named SSE messages. `ToolCallAgent` observes `ChatClient.stream().chatResponse()` chunks immediately, then assembles one complete response before the existing tool execution runs. The Vue client consumes named events only for Manus.

**Tech Stack:** Java 21, Spring Boot 3.4.4, Spring AI 1.0.0-M6, Reactor, Spring MVC, Vue 3, Vite, Node test runner.

## Global Constraints

- Retain `GET /api/ai/manus/chat` and `SseEmitter`; do not migrate to WebFlux.
- Use `ChatClient.stream().chatResponse()`; remove random chunking and `Thread.sleep`.
- Emit only `thinking`, `tool_call`, `tool_result`, `answer`, `complete`, and `error` from the Manus endpoint.
- Execute tools only after the complete model response has been assembled.
- Do not change normal chat, RAG chat, tool-selection semantics, or persistence behavior.
- Backend tests must not need a database or a Spring application context.

---

## File Structure

- Create `src/main/java/com/vs/vsaiagent/agent/model/ManusStreamEvent.java`: immutable JSON payload and factories for the six protocol event kinds.
- Create `src/main/java/com/vs/vsaiagent/agent/ManusStreamEventListener.java`: functional boundary from ReAct execution to any event transport.
- Create `src/main/java/com/vs/vsaiagent/agent/ManusStreamEventEmitter.java`: single-terminal-event guard that delegates to the listener.
- Modify `src/main/java/com/vs/vsaiagent/agent/ToolCallAgent.java`: token callback and complete `ChatResponse` aggregation that preserves tool calls.
- Modify `src/main/java/com/vs/vsaiagent/agent/BaseAgent.java`: stream-aware ReAct execution and event ordering.
- Modify `src/main/java/com/vs/vsaiagent/controller/AiController.java`: explicit event-stream media type.
- Create `src/test/java/com/vs/vsaiagent/agent/ManusStreamEventEmitterTest.java`: protocol-emission unit tests.
- Create `src/test/java/com/vs/vsaiagent/agent/ToolCallAgentStreamingTest.java`: controlled Flux tests for delta timing and tool-call preservation.
- Create `vs-agent-web/src/utils/manusStream.js`: pure named-event state reducer.
- Create `vs-agent-web/scripts/manus-stream.test.mjs`: reducer tests.
- Modify `vs-agent-web/src/views/AssistantApp.vue`: named Manus listeners and collapsible trace rendering.

### Task 1: Typed SSE Event Boundary

**Files:**
- Create: `src/main/java/com/vs/vsaiagent/agent/model/ManusStreamEvent.java`
- Create: `src/main/java/com/vs/vsaiagent/agent/ManusStreamEventListener.java`
- Create: `src/main/java/com/vs/vsaiagent/agent/ManusStreamEventEmitter.java`
- Test: `src/test/java/com/vs/vsaiagent/agent/ManusStreamEventEmitterTest.java`

**Interfaces:** Produces `ManusStreamEvent(String type, int step, String text, String toolName, String toolArguments, String toolResult, String message)`, `ManusStreamEventListener#onEvent(ManusStreamEvent)`, and `ManusStreamEventEmitter#emit(ManusStreamEvent)`, `#complete(int)`, and `#error(String)`.

- [ ] **Step 1: Write failing protocol tests**

```java
@Test
void forwardsNamedThinkingPayloadToItsConsumer() {
    List<ManusStreamEvent> events = new ArrayList<>();
    ManusStreamEventEmitter emitter = new ManusStreamEventEmitter(events::add);
    emitter.emit(ManusStreamEvent.thinking(2, "plan"));
    assertThat(events).containsExactly(ManusStreamEvent.thinking(2, "plan"));
}

@Test
void acceptsOnlyTheFirstTerminalEvent() {
    List<ManusStreamEvent> events = new ArrayList<>();
    ManusStreamEventEmitter emitter = new ManusStreamEventEmitter(events::add);
    emitter.complete(3);
    emitter.error("ignored");
    assertThat(events).containsExactly(ManusStreamEvent.complete(3));
}
```

- [ ] **Step 2: Verify RED** — Run `./mvnw.cmd test -Dtest=ManusStreamEventEmitterTest`. Expected: compilation fails because the event record and emitter do not exist.

- [ ] **Step 3: Implement the smallest protocol boundary**

```java
public static ManusStreamEvent thinking(int step, String text) {
    return new ManusStreamEvent("thinking", step, text, null, null, null, null);
}

private void emitTerminal(ManusStreamEvent event) {
    if (terminal.compareAndSet(false, true)) {
        sender.accept(event);
    }
}
```

`ManusStreamEventListener` is a production seam: the controller supplies a listener that calls `sseEmitter.send(SseEmitter.event().name(event.type()).data(event))`; unit tests use a list-appending listener without needing HTTP or Spring.

- [ ] **Step 4: Verify GREEN** — Run `./mvnw.cmd test -Dtest=ManusStreamEventEmitterTest`. Expected: Maven exits 0 and both tests pass.

- [ ] **Step 5: Commit** — Stage the event record, listener, emitter, and test, then commit with `feat: add typed Manus stream events`.

### Task 2: Token-Level ReAct Streaming

**Files:**
- Modify: `src/main/java/com/vs/vsaiagent/agent/ToolCallAgent.java`
- Modify: `src/main/java/com/vs/vsaiagent/agent/BaseAgent.java`
- Modify: `src/main/java/com/vs/vsaiagent/controller/AiController.java`
- Test: `src/test/java/com/vs/vsaiagent/agent/ToolCallAgentStreamingTest.java`

**Interfaces:** Consumes `ManusStreamEventEmitter`. Produces `ToolCallAgent#thinkStream(int step, ManusStreamEventEmitter emitter)` and protected `streamPrompt(Prompt)` returning `Flux<ChatResponse>`.

- [ ] **Step 1: Write failing Flux tests**

```java
@Test
void emitsEveryTextChunkBeforeTheCompleteResponse() {
    TestToolCallAgent agent = new TestToolCallAgent(Flux.just(response("first "), response("token")));
    List<ManusStreamEvent> events = new ArrayList<>();
    agent.thinkStream(1, new ManusStreamEventEmitter(events::add));
    assertThat(events).extracting(ManusStreamEvent::type, ManusStreamEvent::text)
        .containsExactly(tuple("thinking", "first "), tuple("thinking", "token"));
}

@Test
void keepsTheCompleteToolCallForTheExistingActMethod() {
    AssistantMessage.ToolCall call = new AssistantMessage.ToolCall("id-1", "function", "web_search", "{\"q\":\"ai\"}");
    TestToolCallAgent agent = new TestToolCallAgent(Flux.just(responseWithToolCall(call)));
    assertThat(agent.thinkStream(1, new ManusStreamEventEmitter(event -> {}))).isTrue();
    assertThat(agent.getToolCallChatResponse().getResult().getOutput().getToolCalls()).containsExactly(call);
}
```

- [ ] **Step 2: Verify RED** — Run `./mvnw.cmd test -Dtest=ToolCallAgentStreamingTest`. Expected: compilation fails because `thinkStream` and `streamPrompt` do not exist.

- [ ] **Step 3: Implement the minimum streaming path**

Implement `streamPrompt` as the current prompt construction with `.stream().chatResponse()`. `thinkStream` emits every nonblank chunk text as `thinking`, collects chunks inside the existing background task, concatenates the texts, preserves the latest nonempty `AssistantMessage.ToolCall` list, and builds a single `ChatResponse` for `act()`. Do not use Spring AI M6 `MessageAggregator`: it reconstructs text but drops tool calls.

Replace `BaseAgent#runStream`'s `step()` and random substring loop with `thinkStream`. Emit `tool_call` before `act()`, one `tool_result` per tool response after it, or `answer` chunks and `complete` when no tool is needed. Convert exceptions to `error`, clean up once, and remove all `Thread.sleep` and `ThreadLocalRandom` imports. Set the controller `produces` to `MediaType.TEXT_EVENT_STREAM_VALUE`.

- [ ] **Step 4: Verify GREEN** — Run `./mvnw.cmd test -Dtest=ManusStreamEventEmitterTest,ToolCallAgentStreamingTest`. Expected: Maven exits 0; no database or Spring application context starts.

- [ ] **Step 5: Commit** — Stage the three backend production files and the stream test, then commit with `feat: stream Manus ReAct events`.

### Task 3: Named-Event Manus Client

**Files:**
- Create: `vs-agent-web/src/utils/manusStream.js`
- Create: `vs-agent-web/scripts/manus-stream.test.mjs`
- Modify: `vs-agent-web/src/views/AssistantApp.vue`

**Interfaces:** Consumes JSON data matching `ManusStreamEvent`. Produces `createManusStreamState()` and `applyManusStreamEvent(state, event)` returning `answerText`, `trace`, `terminal`, and `errorMessage`.

- [ ] **Step 1: Write failing client-state test**

```javascript
test('keeps final answer text out of execution trace', () => {
  let state = createManusStreamState()
  state = applyManusStreamEvent(state, { type: 'thinking', step: 1, text: 'searching' })
  state = applyManusStreamEvent(state, { type: 'tool_call', step: 1, toolName: 'web_search' })
  state = applyManusStreamEvent(state, { type: 'answer', step: 2, text: 'result' })
  assert.equal(state.answerText, 'result')
  assert.deepEqual(state.trace.map(item => item.type), ['thinking', 'tool_call'])
})
```

- [ ] **Step 2: Verify RED** — Run `node --test scripts/manus-stream.test.mjs`. Expected: module-not-found failure for `src/utils/manusStream.js`.

- [ ] **Step 3: Implement reducer and named listeners**

The reducer appends only `answer` text to `answerText`; it appends `thinking`, `tool_call`, and `tool_result` to `trace`; it accepts terminal state only from `complete` and `error`. In `AssistantApp.vue`, register listeners for all six names when mode is `agent`, update the chat message only from `answerText`, and show trace in a `details` block below it. Preserve existing `onmessage` behavior exclusively for normal and RAG modes. Close the source once on terminal event and append only `answerText` to `agentHistory`.

- [ ] **Step 4: Verify GREEN** — Run `node --test scripts/manus-stream.test.mjs; npm.cmd run build`. Expected: both commands exit 0.

- [ ] **Step 5: Commit** — Stage the reducer, client test, and component, then commit with `feat: render Manus execution events`.

### Task 4: Final Verification and Push

**Files:** Verify only files changed by Tasks 1–3.

- [ ] **Step 1: Run backend focused tests** — Run `./mvnw.cmd test -Dtest=ManusStreamEventEmitterTest,ToolCallAgentStreamingTest`. Expected: Maven exits 0.

- [ ] **Step 2: Run full backend suite and record the actual result** — Run `./mvnw.cmd test`. Expected: compilation completes. The known pre-existing `VsManusTest` may still fail because its full Spring context has no local data source; do not change unrelated test configuration.

- [ ] **Step 3: Run frontend test and build** — Run `node --test scripts/manus-stream.test.mjs; npm.cmd run build`. Expected: both commands exit 0.

- [ ] **Step 4: Inspect scope and whitespace** — Run `git status --short`, `git log --oneline origin/feat/agent-workbench-redesign..HEAD`, and `git diff --check origin/feat/agent-workbench-redesign...HEAD`. Expected: only design/plan, Manus backend, and Manus client files differ; the whitespace check is clean.

- [ ] **Step 5: Push the feature branch** — Run `git push -u origin feat/manus-true-streaming`. Expected: origin accepts the new upstream branch.
