package com.vs.vsaiagent.eval.service;

import com.vs.vsaiagent.eval.CaseResult;
import com.vs.vsaiagent.eval.EvalCase;
import com.vs.vsaiagent.eval.EvalJudge;
import com.vs.vsaiagent.eval.EvalRunner;
import com.vs.vsaiagent.eval.EvalSuite;
import com.vs.vsaiagent.eval.SuiteResult;
import com.vs.vsaiagent.eval.judge.KeywordContainsJudge;
import com.vs.vsaiagent.eval.loader.SuiteLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Eval 编排服务：suite 选择 → runner 调用 → judge 评分 → 聚合结果。
 *
 * runner 和 judge 都通过 Spring 自动收集所有实现，按 name 路由：
 *  - suite.runner() 缺省 "assistant_app"
 *  - suite.judge()  缺省 "keyword_contains"
 *  - 未找到 → 回退到 keyword_contains（仍找不到则抛错）
 */
@Slf4j
@Service
public class EvalService {

    private final SuiteLoader suiteLoader;
    private final Map<String, EvalRunner> runners = new HashMap<>();
    private final Map<String, EvalJudge> judges = new HashMap<>();

    public EvalService(SuiteLoader suiteLoader,
                       List<EvalRunner> runnerList,
                       List<EvalJudge> judgeList) {
        this.suiteLoader = suiteLoader;
        for (EvalRunner r : runnerList) runners.put(r.name(), r);
        for (EvalJudge j : judgeList) judges.put(j.name(), j);
        log.info("[eval] runners={} judges={}", runners.keySet(), judges.keySet());
    }

    public List<EvalSuite> listSuites() {
        return suiteLoader.loadAll();
    }

    /** Judges 列表（前端可用于显示哪些 judge 可用） */
    public List<String> listJudges() {
        return new ArrayList<>(judges.keySet());
    }

    public SuiteResult run(String suiteName) {
        EvalSuite suite = suiteLoader.findByName(suiteName)
                .orElseThrow(() -> new IllegalArgumentException("suite not found: " + suiteName));
        EvalRunner runner = runners.get(suite.runner());
        if (runner == null) {
            throw new IllegalStateException("runner not found: " + suite.runner());
        }
        EvalJudge judge = judges.getOrDefault(suite.judge(), judges.get(KeywordContainsJudge.NAME));
        if (judge == null) {
            throw new IllegalStateException("no judge available, expected: " + suite.judge());
        }
        log.info("[eval] run suite={} runner={} judge={}", suite.name(), runner.name(), judge.name());

        long startAll = System.currentTimeMillis();
        List<CaseResult> caseResults = new ArrayList<>();
        int passed = 0, failed = 0;

        for (EvalCase c : suite.cases()) {
            String chatId = "eval-" + suite.name() + "-" + c.id() + "-" + UUID.randomUUID();
            String actual;
            long runnerStart = System.currentTimeMillis();
            try {
                actual = runner.run(c.input(), chatId);
            } catch (Exception e) {
                actual = "[runner error] " + e.getMessage();
                log.warn("[eval] case {} runner failed", c.id(), e);
            }
            long runnerElapsed = System.currentTimeMillis() - runnerStart;

            long judgeStart = System.currentTimeMillis();
            CaseResult cr = judge.judge(c, actual);
            long judgeElapsed = System.currentTimeMillis() - judgeStart;

            CaseResult enriched = new CaseResult(
                    cr.caseId(), cr.input(), cr.actualOutput(),
                    cr.pass(), cr.reason(), cr.missedKeywords(),
                    runnerElapsed, judgeElapsed
            );
            caseResults.add(enriched);
            if (enriched.pass()) passed++; else failed++;
        }

        long totalElapsed = System.currentTimeMillis() - startAll;
        return new SuiteResult(
                suite.name(),
                runner.name(),
                judge.name(),
                suite.cases().size(),
                passed,
                failed,
                totalElapsed,
                caseResults
        );
    }
}
