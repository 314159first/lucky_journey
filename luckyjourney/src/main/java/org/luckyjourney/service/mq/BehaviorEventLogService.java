package org.luckyjourney.service.mq;

import org.luckyjourney.entity.mq.VideoBehaviorEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BehaviorEventLogService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void initTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS behavior_event_log ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                + "event_id VARCHAR(128) NOT NULL,"
                + "event_type VARCHAR(32) NOT NULL,"
                + "user_id BIGINT NULL,"
                + "video_id BIGINT NULL,"
                + "favorites_id BIGINT NULL,"
                + "delta_value BIGINT NULL,"
                + "status TINYINT NOT NULL DEFAULT 0,"
                + "created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "UNIQUE KEY uk_behavior_event_id (event_id),"
                + "KEY idx_behavior_video_type (video_id, event_type)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    public boolean tryStart(VideoBehaviorEvent event) {
        int inserted = jdbcTemplate.update("INSERT IGNORE INTO behavior_event_log "
                        + "(event_id,event_type,user_id,video_id,favorites_id,delta_value,status) "
                        + "VALUES (?,?,?,?,?,?,0)",
                event.getEventId(),
                event.getEventType(),
                event.getUserId(),
                event.getVideoId(),
                event.getFavoritesId(),
                event.getDelta());
        if (inserted > 0) {
            return true;
        }
        Integer status = jdbcTemplate.queryForObject(
                "SELECT status FROM behavior_event_log WHERE event_id = ?",
                Integer.class,
                event.getEventId());
        return status == null || status != 1;
    }

    public void markSuccess(String eventId) {
        jdbcTemplate.update("UPDATE behavior_event_log SET status = 1 WHERE event_id = ?", eventId);
    }

    public void markFailed(String eventId) {
        jdbcTemplate.update("UPDATE behavior_event_log SET status = 2 WHERE event_id = ?", eventId);
    }
}
