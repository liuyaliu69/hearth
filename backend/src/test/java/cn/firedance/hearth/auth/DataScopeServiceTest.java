package cn.firedance.hearth.auth;

import cn.firedance.hearth.common.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 数据范围 —— 「老师只能看自己」这条规则的唯一实现。
 */
class DataScopeServiceTest {

    private final DataScopeService service = new DataScopeService();

    private static CurrentUser user(List<String> roles, Long staffId) {
        return new CurrentUser(1L, "测试", staffId, "测试员工", "13800000000", roles);
    }

    @Test
    @DisplayName("owner 不加过滤")
    void ownerSeesAll() {
        DataScope scope = service.scopeOf(user(List.of("owner"), 5L));
        assertThat(scope.all()).isTrue();
        assertThat(scope.staffId()).isNull();
    }

    @Test
    @DisplayName("teacher 只看自己的 staffId")
    void teacherSeesSelfOnly() {
        DataScope scope = service.scopeOf(user(List.of("teacher"), 7L));
        assertThat(scope.all()).isFalse();
        assertThat(scope.staffId()).isEqualTo(7L);
        assertThat(scope.isSelfOnly()).isTrue();
    }

    @Test
    @DisplayName("有 teacher 角色但没绑员工：拒绝，而不是放开")
    void teacherWithoutStaffIsRejected() {
        assertThatThrownBy(() -> service.scopeOf(user(List.of("teacher"), null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未绑定员工");
    }

    @Test
    @DisplayName("多角色时以 owner 优先（有 owner 就是全量）")
    void ownerWinsAmongMultipleRoles() {
        DataScope scope = service.scopeOf(user(List.of("teacher", "owner"), 9L));
        assertThat(scope.all()).isTrue();
    }
}
