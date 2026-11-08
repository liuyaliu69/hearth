package cn.firedance.hearth.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 账号与角色查询。
 *
 * <p>SQL 在 resources/mapper/AuthMapper.xml。
 */
@Mapper
public interface AuthMapper {

    /** 按 openid 查用户（含员工资料），不判断状态 */
    UserAuthDO findByOpenid(@Param("openid") String openid);

    /** 按 id 查用户（含员工资料），用于每次请求校验状态 */
    UserAuthDO findById(@Param("id") Long id);

    /** 角色 code 列表，如 ["owner"] */
    List<String> findRoleCodes(@Param("userId") Long userId);

    /** 更新最后登录时间；失败不影响登录 */
    int touchLastLogin(@Param("userId") Long userId);
}
