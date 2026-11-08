package cn.firedance.hearth.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按配置决定启用真实还是 mock 微信登录。
 *
 * <p>判断依据：{@code app.wx.appid} 与 {@code app.wx.secret} **都非空** → 真实；否则 mock。
 * 这样部署时只需写环境变量，不用改代码、不用改 profile。
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
    public WechatService mockWechatService() {
        return new MockWechatService();
    }
}
