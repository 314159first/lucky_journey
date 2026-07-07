package org.luckyjourney.service.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.luckyjourney.constant.AuditStatus;
import org.luckyjourney.constant.RabbitMqConstant;
import org.luckyjourney.constant.RedisConstant;
import org.luckyjourney.entity.mq.VideoBehaviorEvent;
import org.luckyjourney.entity.video.Video;
import org.luckyjourney.entity.vo.HotVideo;
import org.luckyjourney.mapper.video.VideoMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Component
public class VideoBehaviorConsumer {

    @Autowired
    private BehaviorEventLogService behaviorEventLogService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserDataPersistenceService userDataPersistenceService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @RabbitListener(queues = RabbitMqConstant.VIDEO_BEHAVIOR_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    @Transactional(rollbackFor = Exception.class)
    public void consume(VideoBehaviorEvent event, Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            if (event == null || ObjectUtils.isEmpty(event.getEventId()) || event.getVideoId() == null) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            if (!behaviorEventLogService.tryStart(event)) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            updateVideoCounter(event);
            if (VideoBehaviorEvent.TYPE_HISTORY.equals(event.getEventType())) {
                userDataPersistenceService.saveVideoHistory(event.getUserId(), event.getVideoId());
            }
            updateHotCache(event);
            behaviorEventLogService.markSuccess(event.getEventId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            if (event != null && !ObjectUtils.isEmpty(event.getEventId())) {
                behaviorEventLogService.markFailed(event.getEventId());
            }
            channel.basicNack(deliveryTag, false, false);
            throw e;
        }
    }

    private void updateVideoCounter(VideoBehaviorEvent event) {
        Long delta = event.getDelta();
        if (delta == null || delta == 0) {
            return;
        }
        String column = counterColumn(event.getEventType());
        if (column == null) {
            return;
        }
        jdbcTemplate.update("UPDATE video SET " + column + " = GREATEST(IFNULL(" + column + ", 0) + ?, 0) WHERE id = ?",
                delta,
                event.getVideoId());
    }

    private String counterColumn(String eventType) {
        if (VideoBehaviorEvent.TYPE_STAR.equals(eventType)) {
            return "start_count";
        }
        if (VideoBehaviorEvent.TYPE_FAVORITE.equals(eventType)) {
            return "favorites_count";
        }
        if (VideoBehaviorEvent.TYPE_HISTORY.equals(eventType)) {
            return "history_count";
        }
        if (VideoBehaviorEvent.TYPE_SHARE.equals(eventType)) {
            return "share_count";
        }
        return null;
    }

    private void updateHotCache(VideoBehaviorEvent event) throws JsonProcessingException {
        double hotDelta = hotDelta(event);
        if (hotDelta <= 0) {
            return;
        }
        Video video = videoMapper.selectById(event.getVideoId());
        if (video == null || !AuditStatus.SUCCESS.equals(video.getAuditStatus()) || Boolean.TRUE.equals(video.getOpen())) {
            return;
        }
        HotVideo hotVideo = new HotVideo(null, video.getId(), video.getTitle());
        redisTemplate.opsForZSet().incrementScore(RedisConstant.HOT_RANK, objectMapper.writeValueAsString(hotVideo), hotDelta);

        Calendar calendar = Calendar.getInstance();
        int today = calendar.get(Calendar.DATE);
        String key = RedisConstant.HOT_VIDEO + today;
        redisTemplate.opsForSet().add(key, video.getId());
        redisTemplate.expire(key, 3, TimeUnit.DAYS);
    }

    private double hotDelta(VideoBehaviorEvent event) {
        Long delta = event.getDelta();
        if (delta == null || delta <= 0) {
            return 0;
        }
        double base;
        if (VideoBehaviorEvent.TYPE_FAVORITE.equals(event.getEventType())) {
            base = 1.5;
        } else if (VideoBehaviorEvent.TYPE_HISTORY.equals(event.getEventType())) {
            base = 0.8;
        } else {
            base = 1.0;
        }
        Video video = videoMapper.selectById(event.getVideoId());
        if (video == null || video.getGmtCreated() == null) {
            return base * delta;
        }
        long age = new Date().getTime() - video.getGmtCreated().getTime();
        double days = TimeUnit.MILLISECONDS.toDays(age);
        return base * delta * Math.exp(-0.011 * days);
    }
}
