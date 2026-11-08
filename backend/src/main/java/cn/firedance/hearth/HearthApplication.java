package cn.firedance.hearth;

import org.apache.ibatis.annotations.Mapper;
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
// 扫全包，但只认 @Mapper 注解的接口 —— 这样 Mapper 不必强制放在 **/mapper 目录下
@MapperScan(basePackages = "cn.firedance.hearth", annotationClass = Mapper.class)
public class HearthApplication {

    public static void main(String[] args) {
        SpringApplication.run(HearthApplication.class, args);
    }
}
