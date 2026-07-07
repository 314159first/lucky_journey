package org.luckyjourney.constant;

public interface RabbitMqConstant {

    String VIDEO_BEHAVIOR_EXCHANGE = "lucky.video.behavior.exchange";

    String VIDEO_BEHAVIOR_QUEUE = "lucky.video.behavior.queue";

    String VIDEO_BEHAVIOR_ROUTING_KEY = "lucky.video.behavior";

    String VIDEO_BEHAVIOR_DLX = "lucky.video.behavior.dlx";

    String VIDEO_BEHAVIOR_DLQ = "lucky.video.behavior.dlq";

    String VIDEO_BEHAVIOR_DLQ_ROUTING_KEY = "lucky.video.behavior.dead";
}
