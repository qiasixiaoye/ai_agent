package com.vs.vsaiagent.tools.astro;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * 光污染估算工具（启发式）。
 *
 * 没有可离线使用的免费光污染地图 API，这里用「与最近主要城市的距离 + 城市规模」
 * 做启发式估算，输出近似 Bortle 等级与拍摄建议。仅供演示数据流通，不代表真实测光数据。
 */
public class LightPollutionTool {

    /** 主要城市：名称、纬度、经度、规模权重（1~3，越大代表光污染影响半径越大） */
    private static final City[] CITIES = {
            new City("北京", 39.9042, 116.4074, 3),
            new City("上海", 31.2304, 121.4737, 3),
            new City("广州", 23.1291, 113.2644, 3),
            new City("深圳", 22.5431, 114.0579, 3),
            new City("成都", 30.5728, 104.0668, 2.5),
            new City("重庆", 29.5630, 106.5516, 2.5),
            new City("杭州", 30.2741, 120.1551, 2.5),
            new City("武汉", 30.5928, 114.3055, 2.5),
            new City("西安", 34.3416, 108.9398, 2.5),
            new City("南京", 32.0603, 118.7969, 2.5),
            new City("天津", 39.3434, 117.3616, 2.5),
            new City("苏州", 31.2989, 120.5853, 2),
            new City("青岛", 36.0671, 120.3826, 2),
            new City("郑州", 34.7466, 113.6254, 2),
            new City("长沙", 28.2282, 112.9388, 2),
            new City("昆明", 25.0389, 102.7183, 2),
            new City("拉萨", 29.6500, 91.1000, 1.5),
            new City("乌鲁木齐", 43.8256, 87.6168, 1.5),
            new City("呼和浩特", 40.8424, 111.7491, 1.5),
            new City("兰州", 36.0611, 103.8343, 1.5),
    };

    @Tool(description = "估算指定经纬度位置的光污染等级（启发式 Bortle 等级），用于评估该地点是否适合银河/星空摄影")
    public String getLightPollutionInfo(
            @ToolParam(description = "纬度，如 39.9") double latitude,
            @ToolParam(description = "经度，如 116.4") double longitude) {
        try {
            return compute(latitude, longitude).toString();
        } catch (Exception e) {
            return "Error estimating light pollution: " + e.getMessage();
        }
    }

    public JSONObject compute(double latitude, double longitude) {
        City nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (City city : CITIES) {
            double d = haversineKm(latitude, longitude, city.lat, city.lon);
            if (d < nearestDistance) {
                nearestDistance = d;
                nearest = city;
            }
        }

        // 城市规模越大，光污染影响半径越大：用 distance / scale 归一化
        double normalized = nearest == null ? nearestDistance : nearestDistance / nearest.scale;

        int bortle;
        String level;
        String advice;
        if (normalized < 15) {
            bortle = 8;
            level = "城市天空（严重光污染）";
            advice = "银心几乎不可见，不建议作为银河摄影地点";
        } else if (normalized < 35) {
            bortle = 6;
            level = "城郊（明显光污染）";
            advice = "可见亮星与银心轮廓，建议向更暗区域移动";
        } else if (normalized < 70) {
            bortle = 4;
            level = "乡村/城郊过渡（轻度光污染）";
            advice = "银河结构基本可见，适合入门银河摄影";
        } else if (normalized < 150) {
            bortle = 3;
            level = "乡村天空（轻微光污染）";
            advice = "银河细节清晰，适合银河摄影";
        } else {
            bortle = 2;
            level = "极暗天空（理想观测地）";
            advice = "银河细节极佳，理想的银河摄影地点";
        }

        JSONObject result = new JSONObject();
        result.set("latitude", latitude);
        result.set("longitude", longitude);
        if (nearest != null) {
            JSONObject nearestJson = new JSONObject();
            nearestJson.set("name", nearest.name);
            nearestJson.set("distanceKm", Math.round(nearestDistance));
            result.set("nearestCity", nearestJson);
        }
        result.set("bortleScale", bortle);
        result.set("level", level);
        result.set("advice", advice);
        result.set("source", "heuristic-estimate");
        return result;
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }

    private record City(String name, double lat, double lon, double scale) {
    }
}
