package cn.firedance.hearth.auth.admin;

import cn.firedance.hearth.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 账号管理。
 *
 * <p>约束（脚本与接口共用同一套，见 spec 第六节）：
 * <ul>
 *   <li>openid 唯一</li>
 *   <li>staffId 必须存在</li>
 *   <li>roleCodes 必须都在 app_role 里</li>
 *   <li>不能停用自己（防锁死）</li>
 *   <li>账号至少保留一个角色</li>
 * </ul>
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final Set<String> ALLOWED_STATUS = Set.of("ACTIVE", "DISABLED");

    private final AdminMapper adminMapper;

    public AdminUserService(AdminMapper adminMapper) {
        this.adminMapper = adminMapper;
    }

    public List<AdminDtos.UserRow> list() {
        Map<Long, List<String>> rolesByUser = new HashMap<>();
        for (AdminMapper.UserRolePair pair : adminMapper.findAllUserRoles()) {
            rolesByUser.computeIfAbsent(pair.getUserId(), k -> new ArrayList<>()).add(pair.getRoleCode());
        }
        List<AdminUserRow> rows = adminMapper.findAllUsers();
        List<AdminDtos.UserRow> result = new ArrayList<>(rows.size());
        for (AdminUserRow row : rows) {
            result.add(toDto(row, rolesByUser.getOrDefault(row.getId(), List.of())));
        }
        return result;
    }

    public List<StaffDO> staffList() {
        return adminMapper.findAllStaff();
    }

    @Transactional
    public AdminDtos.UserRow create(AdminDtos.CreateReq req) {
        if (req == null || req.staffId() == null) {
            throw new BizException(400, "staffId 不能为空");
        }
        String openid = trim(req.openid());
        if (openid.isEmpty()) {
            throw new BizException(400, "openid 不能为空");
        }

        StaffDO staff = adminMapper.findStaffById(req.staffId());
        if (staff == null) {
            throw new BizException(404, "员工不存在");
        }
        if (adminMapper.countByOpenid(openid) > 0) {
            throw new BizException(409, "该 openid 已开通账号");
        }

        List<String> codes = normalizeCodes(req.roleCodes());
        if (codes.isEmpty()) {
            throw new BizException(400, "至少指定一个角色");
        }
        List<Long> roleIds = resolveRoleIds(codes);

        AdminUserDO user = new AdminUserDO();
        user.setShopId(staff.getShopId());
        user.setStaffId(staff.getId());
        user.setOpenid(openid);
        user.setName(staff.getName());
        user.setStatus("ACTIVE");
        adminMapper.insertUser(user);

        for (Long roleId : roleIds) {
            adminMapper.insertUserRole(user.getId(), roleId);
        }

        log.info("开通账号 userId={} staffId={} roles={}", user.getId(), staff.getId(), codes);
        return detail(user.getId());
    }

    @Transactional
    public AdminDtos.UserRow update(Long id, AdminDtos.UpdateReq req, Long currentUserId) {
        AdminUserRow existing = adminMapper.findUserById(id);
        if (existing == null) {
            throw new BizException(404, "账号不存在");
        }

        if (req != null && req.status() != null) {
            String status = req.status().trim().toUpperCase();
            if (!ALLOWED_STATUS.contains(status)) {
                throw new BizException(400, "status 只能是 ACTIVE 或 DISABLED");
            }
            if ("DISABLED".equals(status) && id.equals(currentUserId)) {
                throw new BizException(400, "不能停用自己的账号");
            }
            adminMapper.updateStatus(id, status);
            log.info("账号状态变更 userId={} -> {}", id, status);
        }

        if (req != null && req.roleCodes() != null) {
            List<String> codes = normalizeCodes(req.roleCodes());
            if (codes.isEmpty()) {
                throw new BizException(400, "账号至少保留一个角色");
            }
            List<Long> roleIds = resolveRoleIds(codes);
            adminMapper.deleteUserRoles(id);
            for (Long roleId : roleIds) {
                adminMapper.insertUserRole(id, roleId);
            }
            log.info("账号角色变更 userId={} -> {}", id, codes);
        }

        return detail(id);
    }

    public AdminDtos.UserRow detail(Long id) {
        AdminUserRow row = adminMapper.findUserById(id);
        if (row == null) {
            throw new BizException(404, "账号不存在");
        }
        List<String> roles = new ArrayList<>();
        for (AdminMapper.UserRolePair pair : adminMapper.findUserRoles(id)) {
            roles.add(pair.getRoleCode());
        }
        return toDto(row, roles);
    }

    // ---------------------------------------------------------------- 内部

    private List<Long> resolveRoleIds(List<String> codes) {
        List<Long> ids = adminMapper.findRoleIdsByCodes(codes);
        if (ids.size() != codes.size()) {
            throw new BizException(400, "角色不存在：" + String.join(",", codes));
        }
        return ids;
    }

    /** 去空白、去重、保序 */
    private static List<String> normalizeCodes(List<String> codes) {
        if (codes == null) {
            return List.of();
        }
        Set<String> set = new LinkedHashSet<>();
        for (String c : codes) {
            String v = trim(c);
            if (!v.isEmpty()) {
                set.add(v.toLowerCase());
            }
        }
        return new ArrayList<>(set);
    }

    private static AdminDtos.UserRow toDto(AdminUserRow row, List<String> roles) {
        boolean needMobile = row.getStaffMobile() == null || row.getStaffMobile().isBlank();
        return new AdminDtos.UserRow(
                row.getId(),
                row.getName(),
                row.getOpenid(),
                row.getStaffId(),
                row.getStaffName(),
                row.getStaffMobile(),
                row.getStatus(),
                roles,
                row.getLastLoginAt(),
                needMobile
        );
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
