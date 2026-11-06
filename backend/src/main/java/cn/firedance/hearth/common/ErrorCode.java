package cn.firedance.hearth.common;

/**
 * 业务错误码。与 platform/docs/02-技术约定.md 第三节保持一致。
 */
public final class ErrorCode {

    public static final int OK = 0;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int CONFLICT = 409;
    public static final int INTERNAL = 500;

    private ErrorCode() {
    }
}
