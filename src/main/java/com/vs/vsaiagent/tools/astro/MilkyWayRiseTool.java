package com.vs.vsaiagent.tools.astro;

import cn.hutool.json.JSONObject;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;

/**
 * 银河核心（银心 / Sagittarius A*）可见性计算工具。
 *
 * 纯天文计算，不依赖外部接口：基于格林尼治平恒星时(GMST)与赤道-地平坐标转换，
 * 估算银心在给定地点、日期的升起、中天（最佳拍摄时刻）、落下时间，以及对应的方位角/高度角。
 */
public class MilkyWayRiseTool {

    /** 银心（Sagittarius A*）赤经，单位：小时（17h45m40s） */
    private static final double GC_RA_HOURS = 17.7611;
    /** 银心赤纬，单位：度 */
    private static final double GC_DEC_DEG = -29.00781;

    @Tool(description = "计算银河核心（银心）在指定地点和日期的升起/中天（最佳拍摄）/落下时间，以及对应的方位角和高度角，用于规划银河摄影时机")
    public String getMilkyWayCoreInfo(
            @ToolParam(description = "纬度，北纬为正、南纬为负，如 39.9") double latitude,
            @ToolParam(description = "经度，东经为正、西经为负，如 116.4") double longitude,
            @ToolParam(description = "日期，格式 yyyy-MM-dd") String date,
            @ToolParam(description = "时区偏移小时数，中国大陆为 8，可不填默认 8", required = false) Double timezoneOffset) {
        try {
            JSONObject result = compute(latitude, longitude, LocalDate.parse(date),
                    timezoneOffset == null ? 8.0 : timezoneOffset);
            return result.toString();
        } catch (Exception e) {
            return "Error computing milky way core info: " + e.getMessage();
        }
    }

    /**
     * 核心计算逻辑，供工具层与 agent-platform 工具层共用。
     */
    public JSONObject compute(double latitude, double longitude, LocalDate date, double tzOffsetHours) {
        double jd0 = date.toEpochDay() + 2440587.5; // 当日 0 时 UT 的儒略日
        double d0 = jd0 - 2451545.0;
        double t = d0 / 36525.0;
        double gmst0 = mod24(6.697374558 + 0.06570982441908 * d0 + 0.000026 * t * t);

        double latRad = Math.toRadians(latitude);
        double decRad = Math.toRadians(GC_DEC_DEG);
        double lonHours = longitude / 15.0;

        JSONObject result = new JSONObject();
        result.set("latitude", latitude);
        result.set("longitude", longitude);
        result.set("date", date.toString());

        double cosH0 = -Math.tan(latRad) * Math.tan(decRad);
        if (cosH0 <= -1) {
            result.set("visibility", "circumpolar");
            result.set("note", "银心在该纬度终夜不落（恒显）");
            // 中天信息仍可计算
        } else if (cosH0 >= 1) {
            result.set("visibility", "never_rises");
            result.set("note", "银心在该纬度终夜不升，无法拍摄");
            return result;
        } else {
            result.set("visibility", "rises_and_sets");
        }

        double h0Deg = Math.toDegrees(Math.acos(clamp(cosH0, -1, 1)));

        double lstRise = mod24(GC_RA_HOURS - h0Deg / 15.0);
        double lstTransit = mod24(GC_RA_HOURS);
        double lstSet = mod24(GC_RA_HOURS + h0Deg / 15.0);

        double altTransit = 90 - Math.abs(latitude - GC_DEC_DEG);
        if (altTransit > 90) {
            altTransit = 180 - altTransit;
        }

        result.set("riseLocalTime", formatLocalTime(lstToUt(lstRise, lonHours, gmst0), tzOffsetHours));
        result.set("transitLocalTime", formatLocalTime(lstToUt(lstTransit, lonHours, gmst0), tzOffsetHours));
        result.set("setLocalTime", formatLocalTime(lstToUt(lstSet, lonHours, gmst0), tzOffsetHours));

        result.set("riseAzimuthDeg", round1(azimuthAt(-h0Deg, latRad, decRad)));
        double transitAz = (latitude > GC_DEC_DEG) ? 180.0 : 0.0;
        result.set("transitAzimuthDeg", transitAz);
        result.set("transitAltitudeDeg", round1(altTransit));
        result.set("setAzimuthDeg", round1(azimuthAt(h0Deg, latRad, decRad)));

        result.set("bestShootingWindow", "中天前后约 2 小时为银心最高、拍摄条件最佳的时段");
        return result;
    }

    /** 给定时角 H（度），计算银心方位角（0=正北，顺时针，90=正东，180=正南） */
    private double azimuthAt(double hDeg, double latRad, double decRad) {
        double hRad = Math.toRadians(hDeg);
        double az = Math.atan2(
                -Math.cos(decRad) * Math.sin(hRad),
                Math.sin(decRad) * Math.cos(latRad) - Math.cos(decRad) * Math.sin(latRad) * Math.cos(hRad));
        double deg = Math.toDegrees(az);
        return deg < 0 ? deg + 360 : deg;
    }

    /** 本地恒星时 → 世界时（小时） */
    private double lstToUt(double lstHours, double lonHours, double gmst0) {
        double gmst = lstHours - lonHours;
        double ut = (gmst - gmst0) / 1.0027379093;
        return mod24(ut);
    }

    private String formatLocalTime(double utHours, double tzOffsetHours) {
        double local = mod24(utHours + tzOffsetHours);
        int h = (int) local;
        int m = (int) Math.round((local - h) * 60);
        if (m == 60) {
            m = 0;
            h = (h + 1) % 24;
        }
        return String.format("%02d:%02d", h, m);
    }

    private double mod24(double hours) {
        double r = hours % 24.0;
        return r < 0 ? r + 24.0 : r;
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
