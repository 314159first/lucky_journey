package org.luckyjourney.controller;

import org.luckyjourney.entity.video.VideoComment;
import org.luckyjourney.entity.vo.BasePage;
import org.luckyjourney.holder.UserHolder;
import org.luckyjourney.service.video.VideoCommentService;
import org.luckyjourney.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/luckyjourney/comment")
public class VideoCommentController {

    @Autowired
    private VideoCommentService videoCommentService;

    @GetMapping("/video/{videoId}")
    public R listRootComments(@PathVariable Long videoId,
                              @RequestParam(required = false, defaultValue = "hot") String sort,
                              BasePage basePage) {
        return R.ok().data(videoCommentService.listRootComments(videoId, sort, basePage, UserHolder.get()));
    }

    @GetMapping("/reply/{commentId}")
    public R listReplies(@PathVariable Long commentId, BasePage basePage) {
        return R.ok().data(videoCommentService.listReplies(commentId, basePage, UserHolder.get()));
    }

    @PostMapping
    public R publish(@RequestBody @Validated VideoComment comment) {
        return R.ok().data(videoCommentService.publish(comment, UserHolder.get())).message("评论成功");
    }

    @PostMapping("/star/{commentId}")
    public R starComment(@PathVariable Long commentId) {
        boolean result = videoCommentService.starComment(commentId, UserHolder.get());
        return R.ok().message(result ? "已点赞" : "取消点赞").data(result);
    }

    @DeleteMapping("/{commentId}")
    public R deleteComment(@PathVariable Long commentId) {
        videoCommentService.deleteComment(commentId, UserHolder.get());
        return R.ok().message("删除成功");
    }
}
