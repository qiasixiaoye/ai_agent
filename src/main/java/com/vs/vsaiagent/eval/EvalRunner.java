package com.vs.vsaiagent.eval;

/**
 * Runner 把 case 输入打到一个具体的被测对象上（AssistantApp / VsManus / Dify Workflow 等），
 * 返回字符串形式的实际输出。
 */
public interface EvalRunner {

    /** 唯一名字，与 EvalSuite.runner 字段对应 */
    String name();

    /**
     * 执行一次 case。
     *
     * @param input    case 输入
     * @param chatId   建议每个 case 用独立 chatId，避免上下文串污染
     * @return         模型/智能体最终给出的字符串答案
     */
    String run(String input, String chatId);
}
