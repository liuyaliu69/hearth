package cn.firedance.hearth.common;

/**
 * 业务异常。code 与 HTTP 状态码一致（400/401/403/404/409）。
 *
 * <p>可选携带 data —— 例如「未开通」时要带 openid 给前端展示。
 */
public class BizException extends RuntimeException {

    private final int code;
    private final transient Object data;

    public BizException(int code, String message) {
        this(code, message, null);
    }

    public BizException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}
