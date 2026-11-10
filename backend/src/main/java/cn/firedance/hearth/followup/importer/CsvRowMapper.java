package cn.firedance.hearth.followup.importer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV 行 → 强类型。列名映射集中在这一处。
 *
 * <p>映射依据：2026-11-09 用真实导出（`跟进_待唤醒池.csv`，38 列）逐列核对，
 * 见 `cycles/c1-followup/design.md` 附录。
 */
public final class CsvRowMapper {

    /** 没有对应字段的 16 列，原样存进 extra_json */
    public static final List<String> UNMAPPED_COLUMNS = List.of(
            "次卡_购买课时", "次卡_已用课时", "次卡_到期日", "剩余课时合计", "消耗情况",
            "有效合同数", "近60天到期数", "买过的产品", "会员列表消费", "跟过老师数",
            "跟过的老师", "分配依据", "取消次数", "未到次数", "首次上课", "谈单提示");

    private static final ObjectMapper JSON = new ObjectMapper();

    private CsvRowMapper() {
    }

    public static ImportRow toRow(Map<String, String> raw) {
        return new ImportRow(
                str(raw, "学员"),
                str(raw, "手机"),
                str(raw, "分配老师"),
                intOrNull(raw, "距末课天数"),
                intOrNull(raw, "到课次数"),
                intOrNull(raw, "时间卡_剩余天数"),
                intOrNull(raw, "次卡_剩余课时"),
                str(raw, "时间卡_到期日"),
                intOrNull(raw, "买过199"),
                unmapped(raw));
    }

    /** 未映射的 16 列 → Map（交给上层序列化成 JSON） */
    public static Map<String, String> unmapped(Map<String, String> raw) {
        Map<String, String> extras = new LinkedHashMap<>();
        for (String col : UNMAPPED_COLUMNS) {
            String v = raw.get(col);
            if (v != null && !v.isBlank()) {
                extras.put(col, v);
            }
        }
        return extras;
    }

    public static String toJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            // 序列化失败不该让整批导入失败：这一行不存 extras 而已
            return null;
        }
    }

    // ---------------------------------------------------------------- 取值助手

    public static String str(Map<String, String> raw, String col) {
        String v = raw.get(col);
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    public static Integer intOrNull(Map<String, String> raw, String col) {
        String v = str(raw, col);
        if (v == null) {
            return null;
        }
        try {
            return Integer.valueOf(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static LocalDate dateOrNull(Map<String, String> raw, String col) {
        String v = str(raw, col);
        if (v == null) {
            return null;
        }
        // 兼容 "2026-09-20 19:30:00" 这种带时间的写法
        String datePart = v.length() >= 10 ? v.substring(0, 10) : v;
        try {
            return LocalDate.parse(datePart);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static java.math.BigDecimal decimalOrNull(Map<String, String> raw, String col) {
        String v = str(raw, col);
        if (v == null) {
            return null;
        }
        String cleaned = v.replace(",", "").replace("¥", "").replace("￥", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return new java.math.BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
