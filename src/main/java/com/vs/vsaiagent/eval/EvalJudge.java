package com.vs.vsaiagent.eval;

/**
 * Judge 接收 case 期望 + 实际输出，给出判分结论。
 */
public interface EvalJudge {

    String name();

    /**
     * @param evalCase     原始 case（含 expectedContains / rubric）
     * @param actualOutput Runner 返回的实际输出
     * @return             单条 CaseResult；judgeElapsedMs 由调用方填充
     */
    CaseResult judge(EvalCase evalCase, String actualOutput);
}
