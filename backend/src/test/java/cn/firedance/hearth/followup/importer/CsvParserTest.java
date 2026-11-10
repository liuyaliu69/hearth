package cn.firedance.hearth.followup.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSV 解析 —— 这组用例的重点不是"能解析"，而是"不会静默解析错"。
 *
 * <p>真实文件里 {@code 话术} 含换行，按行切会把 1 条拆成多条，
 * 而且导入会"看起来成功"。所以引号内换行必须有专门用例。
 */
class CsvParserTest {

    @Test
    @DisplayName("引号内的换行不算换行 —— 一条记录就是一条")
    void newlineInsideQuotes() {
        String csv = "学员,话术\n张艳,\"第一行\n第二行\"\n李四,正常\n";
        List<Map<String, String>> rows = CsvParser.parseWithHeader(csv);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("学员")).isEqualTo("张艳");
        assertThat(rows.get(0).get("话术")).isEqualTo("第一行\n第二行");
        assertThat(rows.get(1).get("学员")).isEqualTo("李四");
    }

    @Test
    @DisplayName("引号内的逗号不切列")
    void commaInsideQuotes() {
        String csv = "学员,备注\n张三,\"a,b,c\"\n";
        List<Map<String, String>> rows = CsvParser.parseWithHeader(csv);
        assertThat(rows.get(0).get("备注")).isEqualTo("a,b,c");
    }

    @Test
    @DisplayName("双引号转义（\"\" → \"）")
    void escapedQuotes() {
        String csv = "学员,话术\n张三,\"他说\"\"你好\"\"\"\n";
        List<Map<String, String>> rows = CsvParser.parseWithHeader(csv);
        assertThat(rows.get(0).get("话术")).isEqualTo("他说\"你好\"");
    }

    @Test
    @DisplayName("BOM 被剥离，第一列名不带隐藏字符")
    void stripsBom() {
        String csv = "\uFEFF学员,手机\n张三,13800000000\n";
        List<Map<String, String>> rows = CsvParser.parseWithHeader(csv);
        assertThat(rows.get(0)).containsKey("学员");
        assertThat(rows.get(0).get("学员")).isEqualTo("张三");
    }

    @Test
    @DisplayName("CRLF 与 LF 混用都能正确处理")
    void mixedLineEndings() {
        String csv = "学员,手机\r\n张三,1\n李四,2\r\n";
        List<Map<String, String>> rows = CsvParser.parseWithHeader(csv);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("手机")).isEqualTo("1");
        assertThat(rows.get(1).get("手机")).isEqualTo("2");
    }

    @Test
    @DisplayName("末尾空行与空记录被跳过")
    void skipsBlankRows() {
        String csv = "学员,手机\n张三,1\n,,\n\n";
        List<Map<String, String>> rows = CsvParser.parseWithHeader(csv);
        assertThat(rows).hasSize(1);
    }

    @Test
    @DisplayName("字段数少于表头时补 null，不抛异常")
    void shortRowGetsNull() {
        String csv = "学员,手机,备注\n张三,138\n";
        List<Map<String, String>> rows = CsvParser.parseWithHeader(csv);
        assertThat(rows.get(0).get("备注")).isNull();
    }

    @Test
    @DisplayName("空文本返回空列表，不抛异常")
    void emptyInput() {
        assertThat(CsvParser.parseWithHeader("")).isEmpty();
        assertThat(CsvParser.parseWithHeader(null)).isEmpty();
    }

    @Test
    @DisplayName("空字段保留为空串（区别于缺失列的 null）")
    void emptyFieldIsEmptyString() {
        String csv = "学员,手机\n张三,\n";
        List<Map<String, String>> rows = CsvParser.parseWithHeader(csv);
        assertThat(rows.get(0).get("手机")).isEmpty();
    }
}
