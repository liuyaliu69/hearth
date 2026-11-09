package cn.firedance.hearth.auth.admin;

import cn.firedance.hearth.auth.UserContext;
import cn.firedance.hearth.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 账号管理接口。整段路径在 SecurityConfig 里限定为 owner。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminDtos.UserRow>> list() {
        return ApiResponse.ok(adminUserService.list());
    }

    @PostMapping("/users")
    public ApiResponse<AdminDtos.UserRow> create(@RequestBody AdminDtos.CreateReq req) {
        return ApiResponse.ok(adminUserService.create(req));
    }

    @PatchMapping("/users/{id}")
    public ApiResponse<AdminDtos.UserRow> update(@PathVariable Long id,
                                                 @RequestBody AdminDtos.UpdateReq req) {
        Long currentUserId = UserContext.require().userId();
        return ApiResponse.ok(adminUserService.update(id, req, currentUserId));
    }

    /** 员工下拉：开通账号时选人用 */
    @GetMapping("/staff")
    public ApiResponse<List<StaffDO>> staff() {
        return ApiResponse.ok(adminUserService.staffList());
    }
}
