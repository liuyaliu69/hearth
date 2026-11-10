package cn.firedance.hearth.followup.importer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV 解析器（RFC 4180 的实用子集）。
 *
 * <p><b>为什么不用 split(",")</b>：三体导出的 {@code 话术} 列里含换行，
 * 1270 行的文件其实只有 423 条记录。按行切会把一条记录拆成好几条，
 * 而且错得很隐蔽 —— 数据看起来"导入成功"了，只是内容全是碎的。
 *
 * <p>支持：
 * <ul>
 *   <li>UTF-8 BOM（\uFEFF）自动剥离</li>
 *   <li>引号包裹的字段，字段内可以有逗号、换行</li>
 *   <li>双引号转义（{@code ""} → {@code "}）</li>
 *   <li>CRLF 与 LF 混用</li>
 *   <li>末尾空行忽略</li>
 * </ul>
 *
 * <p>不支持（当前用不到）：自定义分隔符、注释行、转义字符 {@code \}。
 */
public final class CsvParser {

    private static final char BOM = '\uFEFF';

    private CsvParser() {
    }

    /**
     * 按表头解析成「列名 → 值」的行列表。
     *
     * @param text 文件全文
     * @return 每行一个 LinkedHashMap（保持列顺序）；空行会被跳过
     */
    public static List<Map<String, String>> parseWithHeader(String text) {
        List<List<String>> rows = parse(text);
        List<Map<String, String>> result = new ArrayList<>();
        if (rows.isEmpty()) {
            return result;
        }

        List<String> header = new ArrayList<>(rows.get(0).size());
        for (String h : rows.get(0)) {
            header.add(h == null ? "" : h.trim());
        }

        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (isBlankRow(row)) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                String key = header.get(c);
                if (key.isEmpty()) {
                    continue;
                }
                item.put(key, c < row.size() ? row.get(c) : null);
            }
            result.add(item);
        }
        return result;
    }

    /** 只按 CSV 规则切分，不做表头处理 */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return rows;
        }
        if (text.charAt(0) == BOM) {
            text = text.substring(1);
        }

        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        int n = text.length();

        while (i < n) {
            char c = text.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                    } else {
                        inQuotes = false;
                        i++;
                    }
                } else {
                    field.append(c);
                    i++;
                }
                continue;
            }

            if (c == '"') {
                inQuotes = true;
                i++;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                i++;
            } else if (c == '\r') {
                // 等 \n 来收尾，或者后面不是 \n 就当普通换行
                i++;
                if (i >= n) {
                    row.add(field.toString());
                    field.setLength(0);
                    rows.add(row);
                    row = new ArrayList<>();
                }
            } else if (c == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                i++;
            } else {
                field.append(c);
                i++;
            }
        }

        // 最后一行没有换行符结尾
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private static boolean isBlankRow(List<String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        for (String v : row) {
            if (v != null && !v.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
