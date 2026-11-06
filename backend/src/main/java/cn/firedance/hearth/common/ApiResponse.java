package cn.firedance.hearth.common;

/**
 * 统一响应体。
 *
 * <pre>
 * { "code": 0, "msg": "", "data": {} }
 * </pre>
 *
 * <p>code = 0 表示成功；非 0 见 {@link ErrorCode}。
 */
public record ApiResponse<T>(int code, String msg, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "", data);
    }

    public static <T> ApiResponse<T> fail(int code, String msg) {
        return new ApiResponse<>(code, msg, null);
    }
}
