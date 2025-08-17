package com.example.spring_ai_demo.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ItemSyncEvent {
    /**
     * 商品ID
     */
    private Long itemId;
    
    /**
     * 事件类型：CREATE, UPDATE, DELETE
     */
    private String eventType;
    
    /**
     * 商品数据（创建和更新时包含）
     */
    private ItemDTO itemData;
    
    /**
     * 事件时间戳
     */
    private LocalDateTime timestamp;
    
    /**
     * 事件来源
     */
    private String source;
    
    /**
     * 操作用户ID
     */
    private String operatorId;
    
    public ItemSyncEvent() {
        this.timestamp = LocalDateTime.now();
    }
    
    public ItemSyncEvent(Long itemId, String eventType, ItemDTO itemData) {
        this();
        this.itemId = itemId;
        this.eventType = eventType;
        this.itemData = itemData;
    }
}