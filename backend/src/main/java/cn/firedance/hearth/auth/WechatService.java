package cn.firedance.hearth.auth;

/**
 * 微信能力适配层：目前只有一件事 —— code 换 openid。
 *
 * <p>两种实现二选一注入：
 * <ul>
 *   <li>{@link RealWechatService}：配了 appid + secret 时启用</li>
 *   <li>{@link MockWechatService}：没配时启用，openid = "mock_" + code</li>
 * </ul>
 * 两条路径**返回结构一致**，之后的逻辑不分叉。
 */
public interface WechatService {

    /**
     * @param code 小程序 wx.login 拿到的临时凭证
     * @return 该用户在此小程序内的 openid
     */
    String code2Openid(String code);
}
