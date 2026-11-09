package cn.firedance.hearth.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

/**
 * 按配置决定启用真实还是 mock 微信登录。
 *
 * <p>判断依据：{@code app.wx.appid} 与 {@code app.wx.secret} **都非空** → 真实；否则 mock。
 *
 * <p><b>安全兜底</b>：{@code prod} profile 下一旦落到 mock，登录接口会被直接禁用（503）。
 * mock 等于没有认证，绝不能让它在生产可用来。
 */
@Configuration
public class WechatConfig {

    @Bean
    @ConditionalOnExpression("'${app.wx.appid:}' != '' and '${app.wx.secret:}' != ''")
    public WechatService realWechatService(@Value("${app.wx.appid}") String appid,
                                           @Value("${app.wx.secret}") String secret) {
        return new RealWechatService(appid, secret);
    }

    @Bean
    @ConditionalOnExpression("'${app.wx.appid:}' == '' or '${app.wx.secret:}' == ''")
    public WechatService mockWechatService(Environment environment) {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        return new MockWechatService(prod);
    }
}
