package org.luckyjourney.entity.video;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.luckyjourney.entity.BaseEntity;
import org.luckyjourney.entity.vo.UserVO;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class VideoComment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "videoId不能为空")
    private Long videoId;

    private Long userId;

    @NotBlank(message = "评论内容不能为空")
    private String content;

    private Long parentId;

    private Long rootId;

    private Long replyToUserId;

    private Long likeCount;

    private Long replyCount;

    private Integer status;

    @TableField(exist = false)
    private UserVO user;

    @TableField(exist = false)
    private UserVO replyToUser;

    @TableField(exist = false)
    private Boolean liked;

    @TableField(exist = false)
    private Boolean author;

    @TableField(exist = false)
    private List<VideoComment> replies;
}
