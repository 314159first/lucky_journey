package org.luckyjourney.service.video;

import com.baomidou.mybatisplus.extension.service.IService;
import org.luckyjourney.entity.video.VideoCommentStar;

import java.util.Collection;
import java.util.Set;

public interface VideoCommentStarService extends IService<VideoCommentStar> {

    boolean starComment(Long commentId, Long userId);

    boolean starState(Long commentId, Long userId);

    Set<Long> listStarCommentIds(Collection<Long> commentIds, Long userId);
}
