package cn.firedance.hearth.auth;

import cn.firedance.hearth.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录编排。
 *
 * <p>关键点：**未开通 / 已停用时，把 openid 一起返回**，
 * 让小程序「未开通页」能展示出来给老板看 —— 否则老师根本不知道自己那串是什么。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final WechatService wechatService;
    private final AuthMapper authMapper;
    private final JwtUtil jwtUtil;

    public AuthService(WechatService wechatService, AuthMapper authMapper, JwtUtil jwtUtil) {
        this.wechatService = wechatService;
        this.authMapper = authMapper;
        this.jwtUtil = jwtUtil;
    }

    public AuthDtos.LoginVO login(String code) {
        String openid = wechatService.code2Openid(code);

        UserAuthDO user = authMapper.findByOpenid(openid);
        if (user == null) {
            log.info("登录被拒：openid 未开通 {}", maskOpenid(openid));
            throw new BizException(403, "你的微信号还没开通，请联系老板", Map.of("openid", openid));
        }
        if (!STATUS_ACTIVE.equals(user.getStatus())) {
            log.info("登录被拒：账号已停用 userId={} status={}", user.getId(), user.getStatus());
            throw new BizException(403, "账号已停用，请联系老板", Map.of("openid", openid));
        }

        List<String> roles = authMapper.findRoleCodes(user.getId());
        JwtUtil.IssuedToken issued = jwtUtil.issue(user.getId());

        try {
            authMapper.touchLastLogin(user.getId());
        } catch (Exception e) {
            // 记录最后登录时间失败不该影响登录
            log.warn("更新 last_login_at 失败 userId={}", user.getId());
        }

        log.info("登录成功 userId={} roles={}", user.getId(), roles);
        return new AuthDtos.LoginVO(
                issued.token(),
                issued.expiresAt().toString(),
                toVO(user, roles),
                isBlank(user.getStaffMobile())
        );
    }

    public AuthDtos.MeVO me(CurrentUser current) {
        UserAuthDO snapshot = new UserAuthDO();
        snapshot.setId(current.userId());
        snapshot.setName(current.name());
        snapshot.setStaffId(current.staffId());
        snapshot.setStaffName(current.staffName());
        snapshot.setStaffMobile(current.staffMobile());
        return new AuthDtos.MeVO(toVO(snapshot, current.roles()), current.needMobile());
    }

    private static AuthDtos.UserVO toVO(UserAuthDO u, List<String> roles) {
        return new AuthDtos.UserVO(
                u.getId(),
                u.getName(),
                u.getStaffId(),
                u.getStaffName(),
                roles == null ? List.of() : roles
        );
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** openid 脱敏：前 4 后 4，日志用 */
    static String maskOpenid(String openid) {
        if (openid == null || openid.length() <= 8) {
            return "****";
        }
        return openid.substring(0, 4) + "****" + openid.substring(openid.length() - 4);
    }

    /** 供 Controller 记录用 */
    public static Map<String, Object> openidPayload(String openid) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("openid", openid);
        return m;
    }
}
