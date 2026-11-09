package cn.firedance.hearth.auth.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 账号管理用的查询与写入。SQL 在 resources/mapper/AdminMapper.xml。
 */
@Mapper
public interface AdminMapper {

    List<AdminUserRow> findAllUsers();

    AdminUserRow findUserById(@Param("id") Long id);

    /** 所有「用户-角色」对，服务层按 userId 分组 */
    List<UserRolePair> findAllUserRoles();

    List<UserRolePair> findUserRoles(@Param("userId") Long userId);

    StaffDO findStaffById(@Param("id") Long id);

    List<StaffDO> findAllStaff();

    int countByOpenid(@Param("openid") String openid);

    /** 新建用户，回填 id */
    int insertUser(AdminUserDO user);

    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    int deleteUserRoles(@Param("userId") Long userId);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 按 code 查角色 id；非法 code 不会被返回，服务层据此报错 */
    List<Long> findRoleIdsByCodes(@Param("codes") List<String> codes);

    /** 用户-角色对 */
    class UserRolePair {
        private Long userId;
        private String roleCode;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getRoleCode() {
            return roleCode;
        }

        public void setRoleCode(String roleCode) {
            this.roleCode = roleCode;
        }
    }
}
