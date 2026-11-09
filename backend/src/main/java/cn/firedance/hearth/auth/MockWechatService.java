package cn.firedance.hearth.auth;

import cn.firedance.hearth.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * 开发用实现：不调用微信，直接把 code 映射成固定格式的 openid。
 *
 * <p>映射规则：{@code openid = "mock_" + code}，code 只允许 {@code [A-Za-z0-9_-]，1~64 位}。
 *
 * <p><b>生产必须阻断</b>：mock 等于没有认证，任何人只要知道 openid 就能登录。
 * 所以在 {@code prod} profile 下即使启用 mock，{@link #code2Openid(String)} 也会直接拒绝，
 * 除非显式配置 {@code WX_SECRET}（那时根本不会用 mock）。
 */
public class MockWechatService implements WechatService {

    private static final Logger log = LoggerFactory.getLogger(MockWechatService.class);
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final boolean blocked;

    public MockWechatService(boolean blocked) {
        this.blocked = blocked;
        if (blocked) {
            log.error("生产环境（prod）未配置 WX_APPID / WX_SECRET：**登录接口已禁用**，"
                    + "请把凭据写入 /opt/hearth/app.env 后重启");
        } else {
            log.warn("未配置 WX_APPID / WX_SECRET，使用 MOCK 微信登录（openid = mock_ + code）—— 仅限开发");
        }
    }

    @Override
    public String code2Openid(String code) {
        if (blocked) {
            throw new BizException(503, "微信登录尚未配置，暂不可用");
        }
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new BizException(400, "code 不合法");
        }
        return "mock_" + code;
    }
}
