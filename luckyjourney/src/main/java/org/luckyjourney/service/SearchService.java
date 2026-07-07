package org.luckyjourney.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.luckyjourney.entity.user.User;
import org.luckyjourney.entity.video.Video;
import org.luckyjourney.entity.vo.BasePage;
import org.luckyjourney.entity.vo.UserVO;

public interface SearchService {

    IPage<Video> searchVideos(String keyword, BasePage basePage);

    IPage<UserVO> searchUsers(String keyword, BasePage basePage);

    void syncVideo(Video video);

    void syncVideoById(Long videoId);

    void deleteVideo(Long videoId);

    void syncUser(User user);

    void rebuildAll();
}
