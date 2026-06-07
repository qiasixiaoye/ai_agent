package com.vs.vsaiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class AssistantAppTest {

    @Resource
    private AssistantApp assistantApp;

    @Test
    void doChat() {
        String chatId = UUID.randomUUID().toString();
        // 多轮对话基本路径
        String answer1 = assistantApp.doChat("你好，请简单介绍一下自己", chatId);
        String answer2 = assistantApp.doChat("帮我推荐一本入门 Java 的书", chatId);
        String answer3 = assistantApp.doChat("我刚才问了什么？", chatId);
        // 这里不强校验回复内容（依赖外部模型），仅确保链路跑通
    }
}
