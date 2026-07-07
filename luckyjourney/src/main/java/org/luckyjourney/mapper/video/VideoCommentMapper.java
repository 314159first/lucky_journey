package org.luckyjourney.mapper.video;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.luckyjourney.entity.video.VideoComment;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoCommentMapper extends BaseMapper<VideoComment> {

    @Update("UPDATE video_comment SET like_count = GREATEST(like_count + #{value}, 0) WHERE id = #{commentId}")
    int updateLikeCount(@Param("commentId") Long commentId, @Param("value") Long value);

    @Update("UPDATE video_comment SET reply_count = GREATEST(reply_count + #{value}, 0) WHERE id = #{commentId}")
    int updateReplyCount(@Param("commentId") Long commentId, @Param("value") Long value);
}
