package org.luckyjourney.service.video.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.luckyjourney.entity.user.User;
import org.luckyjourney.entity.video.Video;
import org.luckyjourney.entity.video.VideoComment;
import org.luckyjourney.entity.vo.BasePage;
import org.luckyjourney.entity.vo.CommentPageVO;
import org.luckyjourney.entity.vo.UserVO;
import org.luckyjourney.exception.BaseException;
import org.luckyjourney.mapper.video.VideoCommentMapper;
import org.luckyjourney.service.user.UserService;
import org.luckyjourney.service.video.VideoCommentService;
import org.luckyjourney.service.video.VideoCommentStarService;
import org.luckyjourney.service.video.VideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class VideoCommentServiceImpl extends ServiceImpl<VideoCommentMapper, VideoComment> implements VideoCommentService {

    private static final int NORMAL = 0;
    private static final int PREVIEW_REPLY_LIMIT = 2;

    @Autowired
    private VideoService videoService;

    @Autowired
    private UserService userService;

    @Autowired
    private VideoCommentMapper videoCommentMapper;

    @Autowired
    private VideoCommentStarService videoCommentStarService;

    @Override
    public CommentPageVO listRootComments(Long videoId, String sort, BasePage basePage, Long currentUserId) {
        Video video = checkVideo(videoId);

        LambdaQueryWrapper<VideoComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VideoComment::getVideoId, videoId)
                .eq(VideoComment::getParentId, 0L)
                .eq(VideoComment::getStatus, NORMAL);

        if ("author".equals(sort)) {
            wrapper.last("ORDER BY CASE WHEN user_id = " + video.getUserId() + " THEN 0 ELSE 1 END, like_count DESC, reply_count DESC, gmt_created DESC");
        } else if ("latest".equals(sort)) {
            wrapper.orderByDesc(VideoComment::getGmtCreated);
        } else {
            wrapper.orderByDesc(VideoComment::getLikeCount)
                    .orderByDesc(VideoComment::getReplyCount)
                    .orderByDesc(VideoComment::getGmtCreated);
        }

        IPage<VideoComment> page = page(basePage.page(), wrapper);
        attachCommentInfo(page.getRecords(), video, currentUserId, true);

        CommentPageVO result = new CommentPageVO();
        result.setComments(page);
        result.setTotal(video.getCommentCount() == null ? 0L : video.getCommentCount());
        return result;
    }

    @Override
    public IPage<VideoComment> listReplies(Long commentId, BasePage basePage, Long currentUserId) {
        VideoComment root = checkComment(commentId);
        Long rootId = root.getRootId() == null || root.getRootId() == 0 ? root.getId() : root.getRootId();
        Video video = checkVideo(root.getVideoId());

        IPage<VideoComment> page = page(basePage.page(), new LambdaQueryWrapper<VideoComment>()
                .eq(VideoComment::getVideoId, root.getVideoId())
                .eq(VideoComment::getRootId, rootId)
                .ne(VideoComment::getParentId, 0L)
                .eq(VideoComment::getStatus, NORMAL)
                .orderByAsc(VideoComment::getGmtCreated));
        attachCommentInfo(page.getRecords(), video, currentUserId, false);
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoComment publish(VideoComment comment, Long currentUserId) {
        Video video = checkVideo(comment.getVideoId());
        if (!StringUtils.hasText(comment.getContent())) {
            throw new BaseException("评论内容不能为空");
        }

        comment.setContent(comment.getContent().trim());
        if (comment.getContent().length() > 500) {
            throw new BaseException("评论内容不能超过500字");
        }

        Long parentId = comment.getParentId() == null ? 0L : comment.getParentId();
        comment.setUserId(currentUserId);
        comment.setParentId(parentId);
        comment.setLikeCount(0L);
        comment.setReplyCount(0L);
        comment.setStatus(NORMAL);

        if (parentId == 0L) {
            comment.setRootId(0L);
            comment.setReplyToUserId(null);
        } else {
            VideoComment parent = checkComment(parentId);
            if (!Objects.equals(parent.getVideoId(), comment.getVideoId())) {
                throw new BaseException("回复的评论不属于当前视频");
            }
            Long rootId = parent.getRootId() == null || parent.getRootId() == 0 ? parent.getId() : parent.getRootId();
            comment.setRootId(rootId);
            if (comment.getReplyToUserId() == null) {
                comment.setReplyToUserId(parent.getUserId());
            }
        }

        save(comment);

        if (comment.getParentId() != 0L) {
            videoCommentMapper.updateReplyCount(comment.getRootId(), 1L);
        }
        updateVideoCommentCount(video, 1L);

        attachCommentInfo(Collections.singletonList(comment), video, currentUserId, false);
        return comment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean starComment(Long commentId, Long currentUserId) {
        checkComment(commentId);
        boolean result = videoCommentStarService.starComment(commentId, currentUserId);
        videoCommentMapper.updateLikeCount(commentId, result ? 1L : -1L);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long commentId, Long currentUserId) {
        VideoComment comment = checkComment(commentId);
        Video video = checkVideo(comment.getVideoId());
        if (!Objects.equals(comment.getUserId(), currentUserId) && !Objects.equals(video.getUserId(), currentUserId)) {
            throw new BaseException("没有权限删除该评论");
        }

        List<VideoComment> comments = list(new LambdaQueryWrapper<VideoComment>()
                .eq(VideoComment::getStatus, NORMAL)
                .and(wrapper -> wrapper.eq(VideoComment::getId, commentId).or().eq(VideoComment::getRootId, commentId)));
        if (ObjectUtils.isEmpty(comments)) return;

        List<Long> ids = comments.stream().map(VideoComment::getId).collect(Collectors.toList());
        removeByIds(ids);

        if (comment.getParentId() != null && comment.getParentId() != 0L) {
            videoCommentMapper.updateReplyCount(comment.getRootId(), -1L);
        }
        updateVideoCommentCount(video, -Long.valueOf(ids.size()));
    }

    private Video checkVideo(Long videoId) {
        Video video = videoService.getById(videoId);
        if (video == null) throw new BaseException("视频不存在");
        return video;
    }

    private VideoComment checkComment(Long commentId) {
        VideoComment comment = getById(commentId);
        if (comment == null || !Objects.equals(comment.getStatus(), NORMAL)) {
            throw new BaseException("评论不存在");
        }
        return comment;
    }

    private void updateVideoCommentCount(Video video, Long value) {
        UpdateWrapper<Video> updateWrapper = new UpdateWrapper<>();
        updateWrapper.setSql("comment_count = GREATEST(comment_count + " + value + ", 0)");
        updateWrapper.lambda().eq(Video::getId, video.getId());
        videoService.update(video, updateWrapper);
    }

    private void attachCommentInfo(List<VideoComment> comments, Video video, Long currentUserId, boolean withPreviewReplies) {
        if (ObjectUtils.isEmpty(comments)) return;

        List<VideoComment> allComments = new ArrayList<>(comments);
        if (withPreviewReplies) {
            List<Long> rootIds = comments.stream()
                    .filter(comment -> comment.getReplyCount() != null && comment.getReplyCount() > 0)
                    .map(VideoComment::getId)
                    .collect(Collectors.toList());
            if (!ObjectUtils.isEmpty(rootIds)) {
                List<VideoComment> replies = list(new LambdaQueryWrapper<VideoComment>()
                        .in(VideoComment::getRootId, rootIds)
                        .ne(VideoComment::getParentId, 0L)
                        .eq(VideoComment::getStatus, NORMAL)
                        .orderByAsc(VideoComment::getGmtCreated));
                Map<Long, List<VideoComment>> replyMap = new HashMap<>();
                for (VideoComment reply : replies) {
                    List<VideoComment> list = replyMap.computeIfAbsent(reply.getRootId(), k -> new ArrayList<>());
                    if (list.size() < PREVIEW_REPLY_LIMIT) {
                        list.add(reply);
                        allComments.add(reply);
                    }
                }
                for (VideoComment comment : comments) {
                    comment.setReplies(replyMap.getOrDefault(comment.getId(), Collections.emptyList()));
                }
            }
        }

        Set<Long> userIds = new HashSet<>();
        for (VideoComment comment : allComments) {
            userIds.add(comment.getUserId());
            if (comment.getReplyToUserId() != null) {
                userIds.add(comment.getReplyToUserId());
            }
        }

        Map<Long, User> userMap = userService.list(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        Set<Long> likedIds = videoCommentStarService.listStarCommentIds(
                allComments.stream().map(VideoComment::getId).collect(Collectors.toList()), currentUserId);

        for (VideoComment comment : allComments) {
            comment.setUser(buildUserVO(userMap.get(comment.getUserId())));
            comment.setReplyToUser(buildUserVO(userMap.get(comment.getReplyToUserId())));
            comment.setLiked(likedIds.contains(comment.getId()));
            comment.setAuthor(Objects.equals(comment.getUserId(), video.getUserId()));
        }
    }

    private UserVO buildUserVO(User user) {
        if (user == null) return null;
        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setNickName(user.getNickName());
        userVO.setAvatar(user.getAvatar());
        userVO.setSex(user.getSex());
        userVO.setDescription(user.getDescription());
        return userVO;
    }
}
