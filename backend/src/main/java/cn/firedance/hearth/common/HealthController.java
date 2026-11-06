package cn.firedance.hearth.common;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台探针。只为验证「链路是否通」而存在，不做业务。
 *
 * <p>验证三层：
 * <ol>
 *   <li>进程活着 —— 能返回 JSON</li>
 *   <li>数据库通 —— db 字段为 ok 且带库名版本</li>
 *   <li>入口正确 —— 经 nginx 访问时路径前缀被正确剥掉</li>
 * </ol>
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final DataSource dataSource;

    @Value("${spring.application.name:hearth}")
    private String appName;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("app", appName);
        body.put("time", OffsetDateTime.now().toString());
        body.put("db", probeDb());
        return ApiResponse.ok(body);
    }

    private Map<String, Object> probeDb() {
        Map<String, Object> db = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData meta = c.getMetaData();
            db.put("status", "ok");
            db.put("product", meta.getDatabaseProductName());
            db.put("version", meta.getDatabaseProductVersion());
            db.put("catalog", c.getCatalog());
        } catch (Exception e) {
            db.put("status", "down");
            db.put("error", e.getClass().getSimpleName());
        }
        return db;
    }
}
