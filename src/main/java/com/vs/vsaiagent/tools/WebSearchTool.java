package com.vs.vsaiagent.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具
 */
public class WebSearchTool {

    // SearchAPI 的搜索接口地址
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    private final String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");
        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);
            List<String> results = extractTopResults(response, 5);
            return results.isEmpty() ? "No web results for: " + query : String.join(",", results);
        } catch (Exception e) {
            return "Error searching Baidu: " + e.getMessage();
        }
    }

    /**
     * 从 SearchAPI 响应中提取前 {@code limit} 条 organic_results。
     * 对结果不足、字段缺失（限流/报错 payload）做防御处理，避免越界 / 空指针。
     * 抽成静态方法便于离线单测。
     */
    static List<String> extractTopResults(String responseJson, int limit) {
        JSONObject jsonObject = JSONUtil.parseObj(responseJson);
        JSONArray organicResults = jsonObject.getJSONArray("organic_results");
        if (organicResults == null || organicResults.isEmpty()) {
            return List.of();
        }
        int n = Math.min(limit, organicResults.size());
        return organicResults.subList(0, n).stream()
                .map(Object::toString)
                .collect(Collectors.toList());
    }
}
