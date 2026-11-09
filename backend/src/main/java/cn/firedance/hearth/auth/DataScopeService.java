package cn.firedance.hearth.auth;

import cn.firedance.hearth.common.BizException;
import org.springframework.stereotype.Service;

/**
 * 数据范围的唯一入口。
 *
 * <p>业务代码要用数据范围时**只调这里**，不自己判断角色、不自己拼条件。
 * 这样「老师只能看自己」这条规则只有一处实现，改起来也只有一处。
 */
@Service
public class DataScopeService {

    public DataScope current() {
        CurrentUser user = UserContext.require();
        return scopeOf(user);
    }

    public DataScope scopeOf(CurrentUser user) {
        if (user.isOwner()) {
            return DataScope.allScope();
        }
        if (user.staffId() == null) {
            // 有账号但没绑员工 —— 无法确定范围，宁可拒绝也不放开
            throw new BizException(403, "账号未绑定员工，无法确定数据范围");
        }
        return DataScope.self(user.staffId());
    }
}
