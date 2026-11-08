package cn.firedance.hearth.auth;

import cn.firedance.hearth.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * 真实实现：调用微信 jscode2session。
 *
 * <p>注意：secret 只从配置来，**绝不写进代码或日志**。
 */
public class RealWechatService implements WechatService {

    private static final Logger log = LoggerFactory.getLogger(RealWechatService.class);
    private static final String HOST = "api.weixin.qq.com";
    private static final String PATH = "/sns/jscode2session";

    private final String appid;
    private final String secret;
    private final RestClient http = RestClient.create();

    public RealWechatService(String appid, String secret) {
        this.appid = appid;
        this.secret = secret;
        log.info("使用真实微信登录（appid={}）", appid);
    }

    @Override
    public String code2Openid(String code) {
        SessionResponse resp;
        try {
            resp = http.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host(HOST)
                            .path(PATH)
                            .queryParam("appid", appid)
                            .queryParam("secret", secret)
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(SessionResponse.class);
        } catch (Exception e) {
            // 不打印完整 URL（里面有 secret）
            log.error("调用微信 code2Session 失败: {}", e.getClass().getSimpleName());
            throw new BizException(502, "微信服务暂时不可用");
        }

        if (resp == null || resp.openid() == null || resp.openid().isBlank()) {
            String errMsg = resp == null ? "空响应" : String.valueOf(resp.errmsg());
            int errCode = resp == null || resp.errcode() == null ? -1 : resp.errcode();
            log.warn("微信登录失败 errcode={} errmsg={}", errCode, errMsg);
            throw new BizException(401, "微信登录失败：" + errMsg);
        }
        return resp.openid();
    }

    /** 微信返回体（只取需要的字段） */
    record SessionResponse(String openid, String session_key, Integer errcode, String errmsg) {
    }
}
