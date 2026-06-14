package com.vs.vsaiagent.tools.astro;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 夜间云量预报工具。
 *
 * 调用 Open-Meteo 免费预报接口（无需 API key），提取指定日期夜间
 * （当天 20:00 至次日 04:00）的逐小时云量，并给出拍摄适宜度评级。
 */
public class CloudCoverTool {

    private static final String OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast";

    @Tool(description = "查询指定经纬度和日期夜间（20:00-次日04:00）云量预报，用于评估银河摄影当晚的天气适宜度")
    public String getCloudCoverInfo(
            @ToolParam(description = "纬度，如 39.9") double latitude,
            @ToolParam(description = "经度，如 116.4") double longitude,
            @ToolParam(description = "日期，格式 yyyy-MM-dd") String date) {
        try {
            return compute(latitude, longitude, LocalDate.parse(date)).toString();
        } catch (Exception e) {
            return "Error fetching cloud cover: " + e.getMessage();
        }
    }

    public JSONObject compute(double latitude, double longitude, LocalDate date) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("latitude", latitude);
        paramMap.put("longitude", longitude);
        paramMap.put("hourly", "cloudcover");
        paramMap.put("timezone", "auto");
        paramMap.put("start_date", date.toString());
        paramMap.put("end_date", date.plusDays(1).toString());

        String response = HttpUtil.get(OPEN_METEO_URL, paramMap);
        JSONObject jsonObject = JSONUtil.parseObj(response);
        JSONObject hourly = jsonObject.getJSONObject("hourly");
        if (hourly == null) {
            throw new IllegalStateException("Open-Meteo 返回数据格式异常");
        }
        JSONArray times = hourly.getJSONArray("time");
        JSONArray cloudCovers = hourly.getJSONArray("cloudcover");
        if (times == null || cloudCovers == null || times.isEmpty()) {
            throw new IllegalStateException("Open-Meteo 返回数据格式异常");
        }

        JSONArray nightHourly = new JSONArray();
        double sum = 0;
        int count = 0;
        for (int i = 0; i < times.size(); i++) {
            String timeStr = times.getStr(i);
            LocalDateTime time = LocalDateTime.parse(timeStr);
            LocalDate day = time.toLocalDate();
            int hour = time.getHour();
            boolean isNight = (day.equals(date) && hour >= 20) || (day.equals(date.plusDays(1)) && hour <= 4);
            if (!isNight) {
                continue;
            }
            int cloudCover = cloudCovers.getInt(i);
            JSONObject entry = new JSONObject();
            entry.set("time", timeStr);
            entry.set("cloudCoverPercent", cloudCover);
            nightHourly.add(entry);
            sum += cloudCover;
            count++;
        }

        double avg = count == 0 ? 0 : sum / count;
        String suitability;
        if (avg < 20) {
            suitability = "晴朗";
        } else if (avg < 50) {
            suitability = "部分多云";
        } else if (avg < 80) {
            suitability = "多云";
        } else {
            suitability = "阴天";
        }

        JSONObject result = new JSONObject();
        result.set("latitude", latitude);
        result.set("longitude", longitude);
        result.set("date", date.toString());
        result.set("hourly", nightHourly);
        result.set("avgNightCloudCoverPercent", Math.round(avg * 10) / 10.0);
        result.set("suitability", suitability);
        result.set("source", "open-meteo");
        return result;
    }
}
