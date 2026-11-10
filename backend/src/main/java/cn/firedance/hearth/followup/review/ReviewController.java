package cn.firedance.hearth.followup.review;

import cn.firedance.hearth.auth.UserContext;
import cn.firedance.hearth.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 核实工作台。整段路径在 SecurityConfig 里限定为 owner。
 *
 * <p>老师永远看不到这里 —— 未核实的数据不该出现在老师端（C1 的核心规则）。
 */
@RestController
@RequestMapping("/api/followup/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/summary")
    public ApiResponse<ReviewDtos.SummaryVO> summary() {
        return ApiResponse.ok(reviewService.summary());
    }

    @GetMapping("/items")
    public ApiResponse<ReviewDtos.ItemsVO> items(
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reviewService.items(reason, page, size));
    }

    @PostMapping("/act")
    public ApiResponse<ReviewDtos.ActVO> act(@RequestBody ReviewDtos.ActReq req) {
        return ApiResponse.ok(reviewService.act(req, UserContext.require()));
    }

    @PostMapping("/batch")
    public ApiResponse<Integer> batch(@RequestBody ReviewDtos.BatchReq req) {
        return ApiResponse.ok(reviewService.batch(req, UserContext.require()));
    }
}
