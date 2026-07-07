package org.luckyjourney.service.video.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.luckyjourney.entity.video.VideoCommentStar;
import org.luckyjourney.mapper.video.VideoCommentStarMapper;
import org.luckyjourney.service.video.VideoCommentStarService;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class VideoCommentStarServiceImpl extends ServiceImpl<VideoCommentStarMapper, VideoCommentStar> implements VideoCommentStarService {

    @Override
    public boolean starComment(Long commentId, Long userId) {
        VideoCommentStar commentStar = new VideoCommentStar();
        commentStar.setCommentId(commentId);
        commentStar.setUserId(userId);
        try {
            save(commentStar);
        } catch (Exception e) {
            remove(new LambdaQueryWrapper<VideoCommentStar>()
                    .eq(VideoCommentStar::getCommentId, commentId)
                    .eq(VideoCommentStar::getUserId, userId));
            return false;
        }
        return true;
    }

    @Override
    public boolean starState(Long commentId, Long userId) {
        if (commentId == null || userId == null) return false;
        return count(new LambdaQueryWrapper<VideoCommentStar>()
                .eq(VideoCommentStar::getCommentId, commentId)
                .eq(VideoCommentStar::getUserId, userId)) > 0;
    }

    @Override
    public Set<Long> listStarCommentIds(Collection<Long> commentIds, Long userId) {
        if (ObjectUtils.isEmpty(commentIds) || userId == null) return Collections.emptySet();
        return list(new LambdaQueryWrapper<VideoCommentStar>()
                .select(VideoCommentStar::getCommentId)
                .in(VideoCommentStar::getCommentId, commentIds)
                .eq(VideoCommentStar::getUserId, userId))
                .stream()
                .map(VideoCommentStar::getCommentId)
                .collect(Collectors.toSet());
    }
}
