package com.vs.vsaiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AssistantAppDocumentLoaderTest {

    @Resource
    private AssistantAppDocumentLoader assistantAppDocumentLoader;

    @Test
    void loadMarkdowns() {
        assistantAppDocumentLoader.loadMarkdowns();
    }
}
