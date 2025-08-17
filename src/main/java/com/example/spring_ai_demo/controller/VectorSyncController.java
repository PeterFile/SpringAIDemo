package com.example.spring_ai_demo.controller;

import com.example.spring_ai_demo.constants.MQConstants;
import com.example.spring_ai_demo.dto.ItemDTO;
import com.example.spring_ai_demo.dto.ItemSyncEvent;
import com.example.spring_ai_demo.service.VectorStoreSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vector-sync")
@RequiredArgsConstructor
@Slf4j
public class VectorSyncController {

    private final VectorStoreSyncService vectorStoreSyncService;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 手动触发商品创建同步（用于测试）
     */
    @PostMapping("/test/create/{itemId}")
    public Map<String, Object> testCreateSync(@PathVariable Long itemId, @RequestBody(required = false) ItemDTO itemData) {
        Map<String, Object> result = new HashMap<>();
        try {
            ItemSyncEvent event = new ItemSyncEvent(itemId, "CREATE", itemData);
            event.setSource("manual-test");
            
            // 发送到MQ
            rabbitTemplate.convertAndSend(
                MQConstants.SYNC_ES_EXCHANGE_NAME,
                MQConstants.SYNC_ES_CREATE_KEY,
                event
            );
            
            result.put("success", true);
            result.put("message", "商品创建同步事件已发送");
            result.put("itemId", itemId);
            
        } catch (Exception e) {
            log.error("发送商品创建同步事件失败", e);
            result.put("success", false);
            result.put("message", "发送失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 手动触发商品更新同步（用于测试）
     */
    @PutMapping("/test/update/{itemId}")
    public Map<String, Object> testUpdateSync(@PathVariable Long itemId, @RequestBody(required = false) ItemDTO itemData) {
        Map<String, Object> result = new HashMap<>();
        try {
            ItemSyncEvent event = new ItemSyncEvent(itemId, "UPDATE", itemData);
            event.setSource("manual-test");
            
            // 发送到MQ
            rabbitTemplate.convertAndSend(
                MQConstants.SYNC_ES_EXCHANGE_NAME,
                MQConstants.SYNC_ES_CREATE_KEY,
                event
            );
            
            result.put("success", true);
            result.put("message", "商品更新同步事件已发送");
            result.put("itemId", itemId);
            
        } catch (Exception e) {
            log.error("发送商品更新同步事件失败", e);
            result.put("success", false);
            result.put("message", "发送失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 手动触发商品删除同步（用于测试）
     */
    @DeleteMapping("/test/delete/{itemId}")
    public Map<String, Object> testDeleteSync(@PathVariable Long itemId) {
        Map<String, Object> result = new HashMap<>();
        try {
            ItemSyncEvent event = new ItemSyncEvent(itemId, "DELETE", null);
            event.setSource("manual-test");
            
            // 发送到MQ
            rabbitTemplate.convertAndSend(
                MQConstants.SYNC_ES_EXCHANGE_NAME,
                MQConstants.SYNC_ES_DELETE_KEY,
                event
            );
            
            result.put("success", true);
            result.put("message", "商品删除同步事件已发送");
            result.put("itemId", itemId);
            
        } catch (Exception e) {
            log.error("发送商品删除同步事件失败", e);
            result.put("success", false);
            result.put("message", "发送失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 直接同步商品（不通过MQ）
     */
    @PostMapping("/direct/create")
    public Map<String, Object> directCreateSync(@RequestBody ItemSyncEvent event) {
        Map<String, Object> result = new HashMap<>();
        try {
            vectorStoreSyncService.handleItemCreate(event);
            result.put("success", true);
            result.put("message", "商品直接创建同步成功");
            
        } catch (Exception e) {
            log.error("直接创建同步失败", e);
            result.put("success", false);
            result.put("message", "同步失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 直接更新商品（不通过MQ）
     */
    @PostMapping("/direct/update")
    public Map<String, Object> directUpdateSync(@RequestBody ItemSyncEvent event) {
        Map<String, Object> result = new HashMap<>();
        try {
            vectorStoreSyncService.handleItemUpdate(event);
            result.put("success", true);
            result.put("message", "商品直接更新同步成功");
            
        } catch (Exception e) {
            log.error("直接更新同步失败", e);
            result.put("success", false);
            result.put("message", "同步失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 直接删除商品（不通过MQ）
     */
    @PostMapping("/direct/delete")
    public Map<String, Object> directDeleteSync(@RequestBody ItemSyncEvent event) {
        Map<String, Object> result = new HashMap<>();
        try {
            vectorStoreSyncService.handleItemDelete(event);
            result.put("success", true);
            result.put("message", "商品直接删除同步成功");
            
        } catch (Exception e) {
            log.error("直接删除同步失败", e);
            result.put("success", false);
            result.put("message", "同步失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 批量同步商品
     */
    @PostMapping("/batch")
    public Map<String, Object> batchSync(@RequestBody List<ItemDTO> items) {
        Map<String, Object> result = new HashMap<>();
        try {
            vectorStoreSyncService.batchSyncItems(items);
            result.put("success", true);
            result.put("message", "批量同步成功");
            result.put("count", items.size());
            
        } catch (Exception e) {
            log.error("批量同步失败", e);
            result.put("success", false);
            result.put("message", "批量同步失败: " + e.getMessage());
        }
        return result;
    }
}