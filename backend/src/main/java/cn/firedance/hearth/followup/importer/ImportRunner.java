package cn.firedance.hearth.followup.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 导入命令行入口（与 Web 服务同一个 jar）。
 *
 * <pre>
 * java -jar hearth.jar --spring.profiles.active=import --import=/tmp/followup.csv
 * </pre>
 *
 * 可选 {@code --followup.import-date=2026-11-10} 指定快照日期，默认今天。
 *
 * <p>为什么用同一个 jar：规则只实现一份。独立脚本迟早和线上逻辑分叉。
 */
@Component
@Profile("import")
public class ImportRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ImportRunner.class);

    @Value("${import:}")
    private String importPath;

    @Value("${followup.import-date:}")
    private String importDate;

    private final ImportService importService;

    public ImportRunner(ImportService importService) {
        this.importService = importService;
    }

    @Override
    public void run(String... args) throws Exception {
        if (importPath == null || importPath.isBlank()) {
            System.err.println("用法：java -jar hearth.jar --spring.profiles.active=import --import=<csv路径> [--followup.import-date=yyyy-MM-dd]");
            System.exit(2);
        }

        Path path = Path.of(importPath);
        if (!Files.isReadable(path)) {
            System.err.println("读不到文件：" + path.toAbsolutePath());
            System.exit(2);
        }

        LocalDate date = parseDate(importDate);
        String batch = "import-" + java.time.LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        long start = System.currentTimeMillis();
        String text = Files.readString(path, StandardCharsets.UTF_8);
        ImportReport report = importService.run(text, date, batch);

        printReport(report, path);
        System.out.println("总耗时 " + (System.currentTimeMillis() - start) + " ms");
    }

    private void printReport(ImportReport r, Path path) {
        System.out.println();
        System.out.println("========== 导入结果 ==========");
        System.out.println("文件      : " + path.getFileName());
        System.out.println("批次      : " + r.batch());
        System.out.println("快照日期  : " + r.snapshotDate());
        System.out.println("总行数    : " + r.total());
        System.out.println("写入快照  : " + r.inserted());
        System.out.println("继承已核实: " + r.inheritedVerified());
        System.out.println("继承其他  : " + r.inheritedOther());
        System.out.println("待核实    : " + r.pending() + "（其中数据变更 " + r.changed() + "）");
        System.out.println("新建会员  : " + r.memberCreated());
        System.out.println("匹配到老师: " + r.staffMatched() + "，未匹配 " + r.staffUnmatched());
        System.out.println("带扩展列  : " + r.withExtraJson());
        System.out.println();
        System.out.println("疑点命中：");
        if (r.flagHits().isEmpty()) {
            System.out.println("  （无）");
        } else {
            for (Map.Entry<String, Integer> e : r.flagHits().entrySet()) {
                double pct = r.total() == 0 ? 0 : e.getValue() * 100.0 / r.total();
                System.out.printf("  %-16s %4d  %.1f%%%n", e.getKey(), e.getValue(), pct);
            }
        }
        System.out.println("=============================");
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            log.warn("import-date 格式不对（{}），改用今天", s);
            return LocalDate.now();
        }
    }
}
