package org.luckyjourney.entity.video;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.luckyjourney.entity.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = false)
public class VideoCommentStar extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long commentId;

    private Long userId;
}
