package org.luckyjourney.service.mq;

import org.luckyjourney.constant.RabbitMqConstant;
import org.luckyjourney.entity.mq.VideoBehaviorEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VideoBehaviorProducer {

    private static final Logger log = LoggerFactory.getLogger(VideoBehaviorProducer.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public boolean send(VideoBehaviorEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConstant.VIDEO_BEHAVIOR_EXCHANGE,
                    RabbitMqConstant.VIDEO_BEHAVIOR_ROUTING_KEY,
                    event);
            return true;
        } catch (AmqpException e) {
            log.warn("Send video behavior event failed, fallback to sync update. eventId={}, type={}",
                    event.getEventId(), event.getEventType(), e);
            return false;
        }
    }
}
