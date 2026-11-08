package cn.firedance.hearth.auth;

import cn.firedance.hearth.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录与当前用户。放在 /api/auth/**，属于平台级。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.LoginVO> login(@RequestBody @Valid AuthDtos.LoginReq req) {
        return ApiResponse.ok(authService.login(req.code()));
    }

    @GetMapping("/me")
    public ApiResponse<AuthDtos.MeVO> me() {
        return ApiResponse.ok(authService.me(UserContext.require()));
    }
}
