package cn.firedance.hearth.followup.teacher;

import cn.firedance.hearth.auth.UserContext;
import cn.firedance.hearth.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学员详情 / 提交跟进结果 / 信息有误退回。
 */
@RestController
@RequestMapping("/api/followup")
public class MemberController {

    private final TaskService taskService;

    public MemberController(TaskService taskService) {
        this.taskService = taskService;
    }

    /** id 是快照 id */
    @GetMapping("/members/{id}")
    public ApiResponse<TeacherDtos.MemberDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(taskService.member(id, UserContext.require()));
    }

    @PostMapping("/followups")
    public ApiResponse<String> submit(@RequestBody TeacherDtos.SubmitReq req) {
        taskService.submit(req, UserContext.require());
        return ApiResponse.ok("ok");
    }

    @PostMapping("/members/{memberId}/flag")
    public ApiResponse<String> flag(@PathVariable Long memberId,
                                    @RequestBody TeacherDtos.FlagReq req) {
        TeacherDtos.FlagReq actual = new TeacherDtos.FlagReq(memberId, req == null ? null : req.note());
        taskService.flag(actual, UserContext.require());
        return ApiResponse.ok("ok");
    }
}
