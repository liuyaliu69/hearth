package cn.firedance.hearth.followup.teacher;

import cn.firedance.hearth.auth.UserContext;
import cn.firedance.hearth.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 老师端：今日待办 + 进度统计。
 */
@RestController
@RequestMapping("/api/followup")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/tasks")
    public ApiResponse<TeacherDtos.TaskListVO> tasks(
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(taskService.tasks(UserContext.require(), limit));
    }

    @GetMapping("/stats")
    public ApiResponse<TeacherDtos.StatsVO> stats() {
        return ApiResponse.ok(taskService.stats(UserContext.require()));
    }
}
