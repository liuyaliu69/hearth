package cn.firedance.hearth.auth;

import cn.firedance.hearth.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * 开发用实现：不调用微信，直接把 code 映射成固定格式的 openid。
 *
 * <p>目的：没有 AppSecret 时也能把整条链路（含小程序端）跑通并验收。
 * 等 secret 配好，只切换配置，业务代码零改动。
 *
 * <p>映射规则：{@code openid = "mock_" + code}，code 只允许 {@code [A-Za-z0-9_-]，1~64 位}。
 * 这样测试里可以任意造用户，例如 {@code code=owner1} → {@code mock_owner1}。
 */
public class MockWechatService implements WechatService {

    private static final Logger log = LoggerFactory.getLogger(MockWechatService.class);
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    public MockWechatService() {
        log.warn("未配置 WX_APPID / WX_SECRET，使用 MOCK 微信登录（openid = mock_ + code）—— 不要用于生产");
    }

    @Override
    public String code2Openid(String code) {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new BizException(400, "code 不合法");
        }
        return "mock_" + code;
    }
}
