package cn.firedance.hearth.common;

import java.util.regex.Pattern;

/**
 * 手机号脱敏。**全项目唯一实现**，任何地方不许再写第二份正则。
 *
 * <p>规则（从严）：
 * <ol>
 *   <li>null → null；空白 → ""</li>
 *   <li>已经是 {@code 136****5177} 形式 → 原样返回（幂等，防止重复脱敏把号码变成 ***）</li>
 *   <li>不是「纯数字且长度 ≥ 7」→ 返回 {@code ***}。
 *       宁可全遮蔽，也不从不认识的数据里截片段出来</li>
 *   <li>其余 → 前 3 + {@code ****} + 后 4</li>
 * </ol>
 *
 * <p><b>不做</b>的事情：不剥离 {@code +86}、不校验号段、不判断号码合法性。
 * 带国家码或含分隔符的输入会被整体遮蔽 —— 我们的数据源是三体导出的 11 位纯数字，
 * 遇到其它形式说明数据脏，此时「全遮蔽」比「聪明地拼一个」安全。
 */
public final class MaskUtil {

    private static final String STAR = "****";
    private static final String FULL_MASK = "***";

    /** 已脱敏形式：3 位数字 + 4 个星 + 4 位数字 */
    private static final Pattern ALREADY_MASKED = Pattern.compile("^\\d{3}\\*{4}\\d{4}$");

    private MaskUtil() {
    }

    public static String maskMobile(String mobile) {
        if (mobile == null) {
            return null;
        }
        String s = mobile.trim();
        if (s.isEmpty()) {
            return "";
        }
        if (ALREADY_MASKED.matcher(s).matches()) {
            return s;
        }
        if (s.length() < 7 || !isAllDigits(s)) {
            return FULL_MASK;
        }
        return s.substring(0, 3) + STAR + s.substring(s.length() - 4);
    }

    private static boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
