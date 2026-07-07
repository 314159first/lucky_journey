package org.luckyjourney.entity.mq;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

@Data
@NoArgsConstructor
public class VideoBehaviorEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String TYPE_STAR = "STAR";
    public static final String TYPE_FAVORITE = "FAVORITE";
    public static final String TYPE_HISTORY = "HISTORY";
    public static final String TYPE_SHARE = "SHARE";

    private String eventId;

    private String eventType;

    private Long userId;

    private Long videoId;

    private Long favoritesId;

    private Long delta;

    private Date occurredAt;

    public static VideoBehaviorEvent build(String eventType, Long userId, Long videoId, Long favoritesId, Long delta) {
        VideoBehaviorEvent event = new VideoBehaviorEvent();
        event.setEventId(eventType + ":" + userId + ":" + videoId + ":" + UUID.randomUUID());
        event.setEventType(eventType);
        event.setUserId(userId);
        event.setVideoId(videoId);
        event.setFavoritesId(favoritesId);
        event.setDelta(delta);
        event.setOccurredAt(new Date());
        return event;
    }
}
