package com.example.spring_ai_demo.listener;

import com.example.spring_ai_demo.constants.MQConstants;
import com.example.spring_ai_demo.dto.ItemSyncEvent;
import com.example.spring_ai_demo.service.VectorStoreSyncService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class ItemSyncListener {

    private final VectorStoreSyncService vectorStoreSyncService;

    /**
     * 监听商品创建事件
     */
    @RabbitListener(queues = MQConstants.SYNC_ES_QUEUE_NAME)
    public void handleItemCreateEvent(ItemSyncEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        
        try {
            log.info("接收到商品同步事件: 商品ID={}, 事件类型={}, 路由键={}", 
                event.getItemId(), event.getEventType(), message.getMessageProperties().getReceivedRoutingKey());
            
            String routingKey = message.getMessageProperties().getReceivedRoutingKey();
            
            // 根据路由键和事件类型处理不同的事件
            if (MQConstants.SYNC_ES_CREATE_KEY.equals(routingKey)) {
                handleCreateOrUpdateEvent(event);
            } else if (MQConstants.SYNC_ES_DELETE_KEY.equals(routingKey)) {
                handleDeleteEvent(event);
            } else {
                log.warn("未知的路由键: {}", routingKey);
            }
            
            // 手动确认消息
            channel.basicAck(deliveryTag, false);
            log.debug("消息处理成功，已确认: 商品ID={}", event.getItemId());
            
        } catch (Exception e) {
            log.error("处理商品同步事件失败: 商品ID={}, 错误={}", event.getItemId(), e.getMessage(), e);
            
            try {
                // 检查是否已经重试过
                Integer retryCount = (Integer) message.getMessageProperties().getHeaders().get("x-retry-count");
                if (retryCount == null) {
                    retryCount = 0;
                }
                
                if (retryCount < 3) {
                    // 重试次数未达到上限，拒绝消息并重新入队
                    log.info("消息处理失败，进行第 {} 次重试: 商品ID={}", retryCount + 1, event.getItemId());
                    channel.basicNack(deliveryTag, false, true);
                } else {
                    // 重试次数已达上限，拒绝消息但不重新入队（进入死信队列或丢弃）
                    log.error("消息处理失败，已达最大重试次数，丢弃消息: 商品ID={}", event.getItemId());
                    channel.basicNack(deliveryTag, false, false);
                }
            } catch (IOException ioException) {
                log.error("确认消息失败", ioException);
            }
        }
    }

    /**
     * 处理创建或更新事件
     */
    private void handleCreateOrUpdateEvent(ItemSyncEvent event) {
        String eventType = event.getEventType();
        
        if ("CREATE".equalsIgnoreCase(eventType)) {
            vectorStoreSyncService.handleItemCreate(event);
        } else if ("UPDATE".equalsIgnoreCase(eventType)) {
            vectorStoreSyncService.handleItemUpdate(event);
        } else {
            // 默认按创建处理
            log.info("事件类型为空或未知，按创建事件处理: 商品ID={}", event.getItemId());
            vectorStoreSyncService.handleItemCreate(event);
        }
    }

    /**
     * 处理删除事件
     */
    private void handleDeleteEvent(ItemSyncEvent event) {
        vectorStoreSyncService.handleItemDelete(event);
    }
}