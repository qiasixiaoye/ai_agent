# Agent Runtime Finalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the remaining code-grounded agent reliability and quality gaps with independently testable commits.

**Architecture:** Keep Manus execution on a named Spring executor that propagates `TraceContext`; retain the existing bounded server-side conversation memory. Expose semantic evaluation through the already present LLM judge and measure judge latency at the source. Do not implement a fictitious embedding batch layer because `VectorStore.add(List<Document>)` already batches embeddings in the current implementation; keep hybrid retrieval as a separately scoped future change because the active advisor has no lexical index or corpus abstraction to compose safely.

**Tech Stack:** Spring Boot 3, Spring AI, Reactor, Java 21, Vue 3.

## Global Constraints

- Every behavior change starts with a failing focused test.
- One independently reviewable commit and remote branch push per function point.
- Preserve the existing typed Manus SSE protocol.

### Task 1: Dedicated Manus executor (completed)

**Files:** `AsyncConfig.java`, `BaseAgent.java`, `TraceContextTest.java`

- [x] Add a failing test that submits a wrapped task through the named executor and asserts the captured trace reaches the worker.
- [x] Add `manusAgentExecutor` with a `TaskDecorator` that uses `TraceContext.wrap`, then inject it into `BaseAgent.runStream` instead of the common ForkJoin pool.
- [x] Run the trace and Manus focused tests; commit and push.

### Task 2: Evaluation timing and semantic judge selection (verified; no change required)

**Files:** `LlmAsJudge.java`, `EvalService.java`, `EvalServiceTest.java`

- [x] Verify `EvalService` measures `runnerElapsed` and `judgeElapsed` around the real calls, then replaces the judge's internal placeholder values before returning `SuiteResult`.
- [x] Verify a suite explicitly selects `llm_as_judge`; keyword judging is only the documented fallback for a missing judge name.

### Task 3: Retrieval and ingestion boundary report (verified; no change required)

**Files:** `DocumentProcessingServiceImpl.java`, `AssistantAppRagCustomAdvisorFactory.java`, this plan.

- [x] Verify `DocumentProcessingServiceImpl.rebuildEmbeddingByDocumentId` constructs one `List<Document>` then calls `VectorStore.add(docs)` once.
- [x] Verify the active RAG advisor only has `VectorStoreDocumentRetriever`; no lexical corpus/index exists to compose with it.
- [x] Record these as verified boundaries rather than adding untestable pseudo-batching or pseudo-BM25 code.
