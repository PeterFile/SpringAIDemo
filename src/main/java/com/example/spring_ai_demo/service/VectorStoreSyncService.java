package com.example.spring_ai_demo.service;

import com.example.spring_ai_demo.client.ItemServiceClient;
import com.example.spring_ai_demo.dto.ItemDTO;
import com.example.spring_ai_demo.dto.ItemSyncEvent;
import com.example.spring_ai_demo.dto.PageDTO;
import com.example.spring_ai_demo.dto.PageQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class VectorStoreSyncService {

    private final MilvusVectorStore vectorStore;
    private final ItemServiceClient itemServiceClient;

    /**
     * 处理商品创建事件
     */
    public void handleItemCreate(ItemSyncEvent event) {
        try {
            log.info("处理商品创建事件，商品ID: {}", event.getItemId());
            
            ItemDTO itemData = event.getItemData();
            if (itemData == null) {
                // 如果事件中没有商品数据，从item-service获取
                itemData = fetchItemById(event.getItemId());
            }
            
            if (itemData != null) {
                Document document = convertItemToDocument(itemData);
                vectorStore.add(List.of(document));
                log.info("成功添加商品 {} 到向量数据库", event.getItemId());
            } else {
                log.warn("无法获取商品 {} 的数据，跳过创建", event.getItemId());
            }
            
        } catch (Exception e) {
            log.error("处理商品创建事件失败，商品ID: {}", event.getItemId(), e);
            throw new RuntimeException("商品创建同步失败", e);
        }
    }

    /**
     * 处理商品更新事件
     */
    public void handleItemUpdate(ItemSyncEvent event) {
        try {
            log.info("处理商品更新事件，商品ID: {}", event.getItemId());
            
            // 先删除旧数据
            deleteItemFromVectorStore(event.getItemId());
            
            // 再添加新数据
            ItemDTO itemData = event.getItemData();
            if (itemData == null) {
                itemData = fetchItemById(event.getItemId());
            }
            
            if (itemData != null) {
                Document document = convertItemToDocument(itemData);
                vectorStore.add(List.of(document));
                log.info("成功更新商品 {} 在向量数据库中的数据", event.getItemId());
            } else {
                log.warn("无法获取商品 {} 的数据，跳过更新", event.getItemId());
            }
            
        } catch (Exception e) {
            log.error("处理商品更新事件失败，商品ID: {}", event.getItemId(), e);
            throw new RuntimeException("商品更新同步失败", e);
        }
    }

    /**
     * 处理商品删除事件
     */
    public void handleItemDelete(ItemSyncEvent event) {
        try {
            log.info("处理商品删除事件，商品ID: {}", event.getItemId());
            
            deleteItemFromVectorStore(event.getItemId());
            log.info("成功从向量数据库删除商品 {}", event.getItemId());
            
        } catch (Exception e) {
            log.error("处理商品删除事件失败，商品ID: {}", event.getItemId(), e);
            throw new RuntimeException("商品删除同步失败", e);
        }
    }

    /**
     * 从向量数据库删除商品
     */
    private void deleteItemFromVectorStore(Long itemId) {
        try {
            // 通过商品ID查找对应的文档
            SearchRequest searchRequest = SearchRequest.builder()
                    .query("id:" + itemId)
                    .topK(10)
                    .build();
            
            List<Document> documents = vectorStore.doSimilaritySearch(searchRequest);
            
            if (!documents.isEmpty()) {
                // 删除找到的文档
                for (Document doc : documents) {
                    String docId = (String) doc.getMetadata().get("id");
                    if (itemId.toString().equals(docId)) {
                        vectorStore.delete(List.of(doc.getId()));
                        log.debug("删除向量文档，商品ID: {}, 文档ID: {}", itemId, doc.getId());
                    }
                }
            } else {
                log.debug("未找到商品 {} 对应的向量文档", itemId);
            }
            
        } catch (Exception e) {
            log.warn("删除商品 {} 的向量数据时出错: {}", itemId, e.getMessage());
            // 删除操作失败不抛出异常，避免影响其他操作
        }
    }

    /**
     * 从item-service获取商品数据
     */
    private ItemDTO fetchItemById(Long itemId) {
        try {
            // 直接通过ID查询商品
            return itemServiceClient.queryItemById(itemId);
        } catch (Exception e) {
            log.error("从item-service获取商品 {} 数据失败", itemId, e);
            return null;
        }
    }

    /**
     * 将ItemDTO转换为Document
     */
    private Document convertItemToDocument(ItemDTO item) {
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("商品名称：").append(item.getName()).append("\n");
        
        if (item.getCategory() != null) {
            contentBuilder.append("商品分类：").append(item.getCategory()).append("\n");
        }
        
        if (item.getBrand() != null) {
            contentBuilder.append("品牌：").append(item.getBrand()).append("\n");
        }
        
        if (item.getPrice() != null) {
            contentBuilder.append("价格：").append(item.getPrice()).append("元\n");
        }
        
        if (item.getSpec() != null) {
            contentBuilder.append("规格：").append(item.getSpec()).append("\n");
        }
        
        if (item.getStock() != null) {
            contentBuilder.append("库存：").append(item.getStock()).append("件\n");
        }
        
        if (item.getSold() != null) {
            contentBuilder.append("已售：").append(item.getSold()).append("件\n");
        }
        
        if (item.getCommentCount() != null) {
            contentBuilder.append("评论数：").append(item.getCommentCount()).append("条\n");
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", item.getId().toString());
        metadata.put("name", item.getName());
        metadata.put("category", item.getCategory());
        metadata.put("brand", item.getBrand());
        metadata.put("price", item.getPrice());
        metadata.put("stock", item.getStock());
        metadata.put("image", item.getImage());
        metadata.put("spec", item.getSpec());
        metadata.put("sold", item.getSold());
        metadata.put("commentCount", item.getCommentCount());
        metadata.put("isAD", item.getIsAD());
        metadata.put("status", item.getStatus());
        metadata.put("type", "product");
        
        return new Document(contentBuilder.toString().trim(), metadata);
    }

    /**
     * 批量同步商品数据（用于初始化或修复数据）
     */
    public void batchSyncItems(List<ItemDTO> items) {
        try {
            log.info("开始批量同步 {} 个商品到向量数据库", items.size());
            
            List<Document> documents = items.stream()
                .map(this::convertItemToDocument)
                .toList();
            
            vectorStore.add(documents);
            log.info("成功批量同步 {} 个商品到向量数据库", items.size());
            
        } catch (Exception e) {
            log.error("批量同步商品数据失败", e);
            throw new RuntimeException("批量同步失败", e);
        }
    }
}