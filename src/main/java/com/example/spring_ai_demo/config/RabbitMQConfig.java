package com.example.spring_ai_demo.config;

import com.example.spring_ai_demo.constants.MQConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class RabbitMQConfig {

    /**
     * JSON消息转换器
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate配置
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }

    /**
     * 监听器容器工厂配置
     */
    @Bean
    public RabbitListenerContainerFactory<?> rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        
        // 设置并发消费者数量
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(5);
        
        // 设置预取数量，控制消费速度
        factory.setPrefetchCount(1);
        
        // 启用手动确认模式
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        
        return factory;
    }

    /**
     * 同步ES的直连交换机
     */
    @Bean
    public DirectExchange syncEsExchange() {
        return ExchangeBuilder
                .directExchange(MQConstants.SYNC_ES_EXCHANGE_NAME)
                .durable(true)
                .build();
    }

    /**
     * ES同步队列
     */
    @Bean
    public Queue syncEsQueue() {
        return QueueBuilder
                .durable(MQConstants.SYNC_ES_QUEUE_NAME)
                .build();
    }

    /**
     * 绑定队列到交换机 - 创建事件
     */
    @Bean
    public Binding syncEsCreateBinding() {
        return BindingBuilder
                .bind(syncEsQueue())
                .to(syncEsExchange())
                .with(MQConstants.SYNC_ES_CREATE_KEY);
    }

    /**
     * 绑定队列到交换机 - 删除事件
     */
    @Bean
    public Binding syncEsDeleteBinding() {
        return BindingBuilder
                .bind(syncEsQueue())
                .to(syncEsExchange())
                .with(MQConstants.SYNC_ES_DELETE_KEY);
    }
}