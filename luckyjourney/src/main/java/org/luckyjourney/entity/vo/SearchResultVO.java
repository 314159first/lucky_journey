package org.luckyjourney.entity.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;
import org.luckyjourney.entity.video.Video;

@Data
public class SearchResultVO {

    private IPage<Video> videos;

    private IPage<UserVO> users;
}
