package com.vs.vsaiagent.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class ImageSearchTool {

    // 替换为你的 Pexels API 密钥（需从官网申请）
    private String API_KEY = "your api";

    // Pexels 常规搜索接口（请以文档为准）
    private static final String API_URL = "https://api.pexels.com/v1/search";

    public ImageSearchTool(String apiKey) {
        this.API_KEY = apiKey;
    }


    @Tool(description = "search image from web")
    public String searchImage(@ToolParam(description = "Search query keyword") String query) {
        try {
            List<String> images = searchMediumImages(query);
            return images.isEmpty() ? "No images for: " + query : String.join(",", images);
        } catch (Exception e) {
            return "Error search image: " + e.getMessage();
        }
    }

    /**
     * 搜索中等尺寸的图片列表
     *
     * @param query
     * @return
     */
    public List<String> searchMediumImages(String query) {
        // 设置请求头（包含API密钥）
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", API_KEY);

        // 设置请求参数（仅包含query，可根据文档补充page、per_page等参数）
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);

        // 发送 GET 请求
        String response = HttpUtil.createGet(API_URL)
                .addHeaders(headers)
                .form(params)
                .execute()
                .body();

        return extractMediumUrls(response);
    }

    /**
     * 从 Pexels 响应中提取 photos[].src.medium 列表。
     * 对 photos 缺失、单个元素缺 src 字段（报错/限流 payload）做防御处理，避免空指针。
     * 抽成静态方法便于离线单测。
     */
    static List<String> extractMediumUrls(String responseJson) {
        cn.hutool.json.JSONArray photos = JSONUtil.parseObj(responseJson).getJSONArray("photos");
        if (photos == null || photos.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return photos
                .stream()
                .map(photoObj -> (JSONObject) photoObj)
                .map(photoObj -> photoObj.getJSONObject("src"))
                .filter(java.util.Objects::nonNull)
                .map(photo -> photo.getStr("medium"))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }
}
