package org.luckyjourney.config;

import org.luckyjourney.constant.RabbitMqConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMqConfig {

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter rabbitMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(rabbitMessageConverter);
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                              MessageConverter rabbitMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(rabbitMessageConverter);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean
    public DirectExchange videoBehaviorExchange() {
        return new DirectExchange(RabbitMqConstant.VIDEO_BEHAVIOR_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange videoBehaviorDlx() {
        return new DirectExchange(RabbitMqConstant.VIDEO_BEHAVIOR_DLX, true, false);
    }

    @Bean
    public Queue videoBehaviorQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", RabbitMqConstant.VIDEO_BEHAVIOR_DLX);
        args.put("x-dead-letter-routing-key", RabbitMqConstant.VIDEO_BEHAVIOR_DLQ_ROUTING_KEY);
        return new Queue(RabbitMqConstant.VIDEO_BEHAVIOR_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue videoBehaviorDlq() {
        return new Queue(RabbitMqConstant.VIDEO_BEHAVIOR_DLQ, true);
    }

    @Bean
    public Binding videoBehaviorBinding(Queue videoBehaviorQueue, DirectExchange videoBehaviorExchange) {
        return BindingBuilder.bind(videoBehaviorQueue)
                .to(videoBehaviorExchange)
                .with(RabbitMqConstant.VIDEO_BEHAVIOR_ROUTING_KEY);
    }

    @Bean
    public Binding videoBehaviorDlqBinding(Queue videoBehaviorDlq, DirectExchange videoBehaviorDlx) {
        return BindingBuilder.bind(videoBehaviorDlq)
                .to(videoBehaviorDlx)
                .with(RabbitMqConstant.VIDEO_BEHAVIOR_DLQ_ROUTING_KEY);
    }
}
