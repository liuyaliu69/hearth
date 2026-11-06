package cn.firedance.hearth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 火花私域平台入口。
 *
 * <p>同一个 jar 有两种跑法：
 * <ul>
 *   <li>默认：Web 服务（server 模式）</li>
 *   <li>--spring.profiles.active=import：命令行导入模式（C1 用）</li>
 * </ul>
 */
@SpringBootApplication
@MapperScan("cn.firedance.hearth.**.mapper")
public class HearthApplication {

    public static void main(String[] args) {
        SpringApplication.run(HearthApplication.class, args);
    }
}
