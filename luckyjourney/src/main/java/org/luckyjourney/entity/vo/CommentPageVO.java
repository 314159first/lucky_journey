package org.luckyjourney.entity.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;
import org.luckyjourney.entity.video.VideoComment;

@Data
public class CommentPageVO {

    private IPage<VideoComment> comments;

    private Long total;
}
