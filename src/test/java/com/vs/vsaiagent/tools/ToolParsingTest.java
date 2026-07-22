package com.vs.vsaiagent.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具结果解析的稳健性测试（离线，不发网络请求）。
 * 覆盖 B1/B2 修复：结果不足、字段缺失、异常 payload 不应越界 / 空指针。
 */
class ToolParsingTest {

    // ---------- WebSearchTool.extractTopResults ----------

    @Test
    void webSearchTruncatesToLimitWhenMoreThanFive() {
        String json = "{\"organic_results\":[1,2,3,4,5,6,7]}";
        List<String> results = WebSearchTool.extractTopResults(json, 5);
        assertEquals(5, results.size());
    }

    @Test
    void webSearchReturnsAllWhenFewerThanLimit() {
        String json = "{\"organic_results\":[{\"a\":1},{\"a\":2},{\"a\":3}]}";
        List<String> results = WebSearchTool.extractTopResults(json, 5);
        assertEquals(3, results.size(), "结果不足 5 条时应全取而非越界");
    }

    @Test
    void webSearchReturnsEmptyWhenFieldMissing() {
        // 限流 / 报错时 organic_results 缺失，旧实现会 NPE
        assertTrue(WebSearchTool.extractTopResults("{\"error\":\"rate limited\"}", 5).isEmpty());
        assertTrue(WebSearchTool.extractTopResults("{\"organic_results\":[]}", 5).isEmpty());
    }

    // ---------- ImageSearchTool.extractMediumUrls ----------

    @Test
    void imageSearchExtractsMediumUrls() {
        String json = "{\"photos\":[{\"src\":{\"medium\":\"http://a/1.jpg\"}},{\"src\":{\"medium\":\"http://a/2.jpg\"}}]}";
        List<String> urls = ImageSearchTool.extractMediumUrls(json);
        assertEquals(List.of("http://a/1.jpg", "http://a/2.jpg"), urls);
    }

    @Test
    void imageSearchReturnsEmptyWhenPhotosMissing() {
        // photos 缺失（报错 payload）旧实现会 NPE
        assertTrue(ImageSearchTool.extractMediumUrls("{\"error\":\"unauthorized\"}").isEmpty());
        assertTrue(ImageSearchTool.extractMediumUrls("{\"photos\":[]}").isEmpty());
    }

    @Test
    void imageSearchSkipsElementsMissingSrc() {
        String json = "{\"photos\":[{\"id\":1},{\"src\":{\"medium\":\"http://a/2.jpg\"}}]}";
        assertEquals(List.of("http://a/2.jpg"), ImageSearchTool.extractMediumUrls(json));
    }
}
