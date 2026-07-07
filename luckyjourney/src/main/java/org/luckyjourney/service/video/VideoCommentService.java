package org.luckyjourney.service.video;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.luckyjourney.entity.video.VideoComment;
import org.luckyjourney.entity.vo.BasePage;
import org.luckyjourney.entity.vo.CommentPageVO;

public interface VideoCommentService extends IService<VideoComment> {

    CommentPageVO listRootComments(Long videoId, String sort, BasePage basePage, Long currentUserId);

    IPage<VideoComment> listReplies(Long commentId, BasePage basePage, Long currentUserId);

    VideoComment publish(VideoComment comment, Long currentUserId);

    boolean starComment(Long commentId, Long currentUserId);

    void deleteComment(Long commentId, Long currentUserId);
}
