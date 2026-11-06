package cn.firedance.hearth.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 脱敏是「只测三处」里的第一处 —— 错了直接泄露隐私。
 *
 * <p>这组用例曾经抓到一次规则没想清楚：非数字输入按位置遮蔽会露出片段。
 * 现在规则改成「认不出就全遮蔽」。
 */
class MaskUtilTest {

    @Test
    @DisplayName("标准 11 位手机号：前3 + 4星 + 后4")
    void masksStandardMobile() {
        assertThat(MaskUtil.maskMobile("13812345678")).isEqualTo("138****5678");
    }

    @Test
    @DisplayName("前后空白会被裁掉，不产生错位")
    void trimsWhitespace() {
        assertThat(MaskUtil.maskMobile(" 13812345678 ")).isEqualTo("138****5678");
    }

    @Test
    @DisplayName("幂等：已脱敏的再脱一次不变，不会退化成 ***")
    void isIdempotent() {
        String once = MaskUtil.maskMobile("13660675177");
        assertThat(once).isEqualTo("136****5177");
        assertThat(MaskUtil.maskMobile(once)).isEqualTo(once);
    }

    @Test
    @DisplayName("7 位边界：刚好能取到前3后4")
    void handlesSevenDigits() {
        assertThat(MaskUtil.maskMobile("1381234")).isEqualTo("138****1234");
    }

    @Test
    @DisplayName("6 位及以下：全部遮蔽，不泄露任何片段")
    void masksShortInputCompletely() {
        assertThat(MaskUtil.maskMobile("138123")).isEqualTo("***");
        assertThat(MaskUtil.maskMobile("1")).isEqualTo("***");
    }

    @Test
    @DisplayName("空值不抛异常")
    void handlesNullAndEmpty() {
        assertThat(MaskUtil.maskMobile(null)).isNull();
        assertThat(MaskUtil.maskMobile("")).isEmpty();
        assertThat(MaskUtil.maskMobile("   ")).isEmpty();
    }

    @Test
    @DisplayName("非纯数字（脏数据 / 带国家码 / 带分隔符）：全遮蔽，不截片段")
    void masksNonNumericCompletely() {
        assertThat(MaskUtil.maskMobile("abc-defg-hij")).isEqualTo("***");
        assertThat(MaskUtil.maskMobile("+8613812345678")).isEqualTo("***");
        assertThat(MaskUtil.maskMobile("138 1234 5678")).isEqualTo("***");
    }
}
