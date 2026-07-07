package org.luckyjourney.service.mq;

import org.luckyjourney.constant.RedisConstant;
import org.luckyjourney.entity.vo.Model;
import org.luckyjourney.entity.vo.UserVideoHistoryRecord;
import org.luckyjourney.util.RedisCacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UserDataPersistenceService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisCacheUtil redisCacheUtil;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public void initTables() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS user_interest_model ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                + "user_id BIGINT NOT NULL,"
                + "label_name VARCHAR(64) NOT NULL,"
                + "score DOUBLE NOT NULL DEFAULT 0,"
                + "last_video_id BIGINT NULL,"
                + "created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "UNIQUE KEY uk_user_interest_label (user_id, label_name),"
                + "KEY idx_user_interest_user (user_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS user_video_history ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                + "user_id BIGINT NOT NULL,"
                + "video_id BIGINT NOT NULL,"
                + "view_count BIGINT NOT NULL DEFAULT 1,"
                + "first_view_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "last_view_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "UNIQUE KEY uk_user_video_history (user_id, video_id),"
                + "KEY idx_user_history_time (user_id, last_view_time)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    public void replaceUserInterestModel(Long userId, Map<Object, Object> modelMap) {
        if (userId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM user_interest_model WHERE user_id = ?", userId);
        if (ObjectUtils.isEmpty(modelMap)) {
            return;
        }
        modelMap.forEach((label, score) -> {
            if (label != null && score != null && StringUtils.hasText(label.toString())) {
                upsertUserInterestModel(userId, label.toString(), toDouble(score), null);
            }
        });
    }

    public void saveUserInterestModel(Long userId, Map<Object, Object> modelMap, List<Model> changedModels) {
        if (userId == null || ObjectUtils.isEmpty(modelMap)) {
            return;
        }
        Map<String, Long> lastVideoIdMap = new HashMap<>();
        if (!ObjectUtils.isEmpty(changedModels)) {
            for (Model model : changedModels) {
                if (model != null && StringUtils.hasText(model.getLabel())) {
                    lastVideoIdMap.put(model.getLabel(), model.getVideoId());
                }
            }
        }
        modelMap.forEach((label, score) -> {
            if (label != null && score != null && StringUtils.hasText(label.toString())) {
                upsertUserInterestModel(userId, label.toString(), toDouble(score), lastVideoIdMap.get(label.toString()));
            }
        });
    }

    public void saveVideoHistory(Long userId, Long videoId) {
        if (userId == null || videoId == null) {
            return;
        }
        jdbcTemplate.update("INSERT INTO user_video_history (user_id, video_id, view_count, first_view_time, last_view_time) "
                        + "VALUES (?, ?, 1, NOW(), NOW()) "
                        + "ON DUPLICATE KEY UPDATE view_count = view_count + 1, last_view_time = NOW()",
                userId,
                videoId);
    }

    public List<UserVideoHistoryRecord> listVideoHistory(Long userId, long page, long limit) {
        long safePage = page <= 0 ? 1 : page;
        long safeLimit = limit <= 0 ? 10 : limit;
        long offset = (safePage - 1) * safeLimit;
        return jdbcTemplate.query("SELECT video_id,last_view_time FROM user_video_history "
                        + "WHERE user_id = ? ORDER BY last_view_time DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new UserVideoHistoryRecord(rs.getLong("video_id"), rs.getTimestamp("last_view_time")),
                userId,
                safeLimit,
                offset);
    }

    public void restoreAllUserInterestModelToRedis() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT user_id,label_name,score FROM user_interest_model ORDER BY user_id");
        Long currentUserId = null;
        Map<Object, Object> modelMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long userId = ((Number) row.get("user_id")).longValue();
            if (currentUserId != null && !currentUserId.equals(userId)) {
                restoreUserModelToRedis(currentUserId, modelMap);
                modelMap = new HashMap<>();
            }
            currentUserId = userId;
            modelMap.put(row.get("label_name"), row.get("score"));
        }
        if (currentUserId != null) {
            restoreUserModelToRedis(currentUserId, modelMap);
        }
    }

    public void backfillUserInterestModelFromRedis() {
        Set<String> keys = redisTemplate.keys(RedisConstant.USER_MODEL + "*");
        if (ObjectUtils.isEmpty(keys)) {
            return;
        }
        for (String key : keys) {
            String userIdText = key.substring(RedisConstant.USER_MODEL.length());
            if (!StringUtils.hasText(userIdText)) {
                continue;
            }
            try {
                Long userId = Long.valueOf(userIdText);
                Map<Object, Object> modelMap = redisTemplate.opsForHash().entries(key);
                if (!ObjectUtils.isEmpty(modelMap)) {
                    replaceUserInterestModel(userId, modelMap);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public Map<Object, Object> restoreUserInterestModelToRedis(Long userId) {
        if (userId == null) {
            return new HashMap<>();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT label_name,score FROM user_interest_model WHERE user_id = ?",
                userId);
        Map<Object, Object> modelMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            modelMap.put(row.get("label_name"), row.get("score"));
        }
        if (!ObjectUtils.isEmpty(modelMap)) {
            restoreUserModelToRedis(userId, modelMap);
        }
        return modelMap;
    }

    private void restoreUserModelToRedis(Long userId, Map<Object, Object> modelMap) {
        String key = RedisConstant.USER_MODEL + userId;
        redisCacheUtil.del(key);
        redisCacheUtil.hmset(key, modelMap);
    }

    private void upsertUserInterestModel(Long userId, String labelName, Double score, Long lastVideoId) {
        jdbcTemplate.update("INSERT INTO user_interest_model (user_id,label_name,score,last_video_id) "
                        + "VALUES (?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE score = VALUES(score), "
                        + "last_video_id = IFNULL(VALUES(last_video_id), last_video_id)",
                userId,
                labelName,
                score,
                lastVideoId);
    }

    private Double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }
}
