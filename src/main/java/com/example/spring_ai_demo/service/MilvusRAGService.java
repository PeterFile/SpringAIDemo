package com.example.spring_ai_demo.service;

import com.example.spring_ai_demo.client.ItemServiceClient;
import com.example.spring_ai_demo.dto.ItemDTO;
import com.example.spring_ai_demo.dto.LoadProgress;
import com.example.spring_ai_demo.dto.PageDTO;
import com.example.spring_ai_demo.dto.PageQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.client.UnknownContentTypeException;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MilvusRAGService {

    private final MilvusVectorStore vectorStore;
    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final ItemServiceClient itemServiceClient;
//    private final RetrievalAugmentationAdvisor productAdvisor;
    
    // 用于存储加载进度的内存缓存
    private final Map<String, LoadProgress> progressCache = new ConcurrentHashMap<>();

    private final RetrievalAugmentationAdvisor productAdvisor;
    
    // 默认配置
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int DEFAULT_BATCH_SIZE = 10; // 减小批处理大小，避免embedding API超时

    public MilvusRAGService(MilvusVectorStore vectorStore,
                            ChatClient.Builder chatClient,
                            EmbeddingModel embeddingModel,
                            ItemServiceClient itemServiceClient, RetrievalAugmentationAdvisor productAdvisor) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient.build();
        this.embeddingModel = embeddingModel;
        this.itemServiceClient = itemServiceClient;
//        this.productAdvisor = productAdvisor;
        this.productAdvisor = productAdvisor;
    }

    /**
     * 从 item-service 加载所有商品数据到 Milvus 向量数据库（支持断点续传）
     */
    public String loadAllItemsIntoVectorStore() {
        return loadAllItemsIntoVectorStore(null, DEFAULT_PAGE_SIZE, DEFAULT_BATCH_SIZE);
    }
    
    /**
     * 从 item-service 加载所有商品数据到 Milvus 向量数据库（支持断点续传）
     * 
     * @param resumeTaskId 要恢复的任务ID，如果为null则创建新任务
     * @param pageSize 每页获取的数据量
     * @param batchSize 每批处理的数据量
     * @return 任务ID
     */
    public String loadAllItemsIntoVectorStore(String resumeTaskId, Integer pageSize, Integer batchSize) {
        String taskId = resumeTaskId != null ? resumeTaskId : UUID.randomUUID().toString();
        
        LoadProgress progress = progressCache.getOrDefault(taskId, new LoadProgress());
        progress.setTaskId(taskId);
        progress.setBatchSize(batchSize != null ? batchSize : DEFAULT_BATCH_SIZE);
        
        if (resumeTaskId == null) {
            // 新任务
            progress.setCurrentPage(1);
            progress.setProcessedItems(0);
            log.info("开始新的商品数据加载任务，任务ID: {}", taskId);
        } else {
            // 恢复任务
            log.info("恢复商品数据加载任务，任务ID: {}，从第 {} 页开始", taskId, progress.getCurrentPage());
        }
        
        progressCache.put(taskId, progress);
        
        try {
            int currentPageSize = pageSize != null ? pageSize : DEFAULT_PAGE_SIZE;
            int currentBatchSize = progress.getBatchSize();
            
            while (true) {
                PageQuery pageQuery = new PageQuery();
                pageQuery.setPageNo(progress.getCurrentPage());
                pageQuery.setPageSize(currentPageSize);
                
                log.info("任务 {} - 正在获取第 {} 页数据，每页 {} 条", taskId, progress.getCurrentPage(), currentPageSize);
                
                // 调用 item-service 获取分页数据
                PageDTO<ItemDTO> pageResult = itemServiceClient.queryItemByPage(
                    pageQuery.getPageNo(),
                    pageQuery.getPageSize(),
                    pageQuery.getSortBy(),
                    pageQuery.getIsAsc(),
                    null
                );
                
                if (pageResult == null || pageResult.getList() == null || pageResult.getList().isEmpty()) {
                    log.info("任务 {} - 第 {} 页没有数据，数据加载完成", taskId, progress.getCurrentPage());
                    break;
                }
                
                // 更新总页数和总条数（如果有的话）
                if (pageResult.getPages() != null) {
                    progress.setTotalPages(pageResult.getPages());
                }
                if (pageResult.getTotal() != null) {
                    progress.setTotalItems(pageResult.getTotal().intValue());
                }
                
                // 分批处理当前页的数据
                List<ItemDTO> items = pageResult.getList();
                processBatch(items, currentBatchSize, progress, taskId);
                
                log.info("任务 {} - 第 {} 页处理完成，已处理 {} 条数据", 
                    taskId, progress.getCurrentPage(), progress.getProcessedItems());
                
                // 如果当前页数据少于页面大小，说明已经是最后一页
                if (items.size() < currentPageSize) {
                    log.info("任务 {} - 已到达最后一页，数据加载完成", taskId);
                    break;
                }
                
                // 更新进度
                progress.setCurrentPage(progress.getCurrentPage() + 1);
                progress.setLastUpdateTime(LocalDateTime.now());
                progressCache.put(taskId, progress);
            }
            
            // 任务完成
            progress.setStatus("COMPLETED");
            progress.setLastUpdateTime(LocalDateTime.now());
            progressCache.put(taskId, progress);
            
            log.info("任务 {} - 商品数据加载完成，共处理 {} 条数据", taskId, progress.getProcessedItems());
            
        } catch (Exception e) {
            log.error("任务 {} - 加载数据时发生错误", taskId, e);
            progress.setStatus("FAILED");
            progress.setErrorMessage(e.getMessage());
            progress.setLastUpdateTime(LocalDateTime.now());
            progressCache.put(taskId, progress);
            throw new RuntimeException("加载商品数据失败: " + e.getMessage(), e);
        }
        
        return taskId;
    }
    
    /**
     * 分批处理数据（带重试机制和熔断保护）
     */
    private void processBatch(List<ItemDTO> items, int batchSize, LoadProgress progress, String taskId) {
        List<Document> batchDocuments = new ArrayList<>();
        
        for (int i = 0; i < items.size(); i++) {
            ItemDTO item = items.get(i);
            Document document = convertItemToDocument(item);
            batchDocuments.add(document);
            
            // 当达到批处理大小或者是最后一条数据时，执行批量插入
            if (batchDocuments.size() >= batchSize || i == items.size() - 1) {
                boolean success = processDocumentBatch(batchDocuments, progress, taskId);
                
                if (!success) {
                    log.error("任务 {} - 批次处理完全失败，跳过当前批次 {} 条数据", taskId, batchDocuments.size());
                    // 记录失败但继续处理下一批
                }
                
                // 清空当前批次
                batchDocuments.clear();
                
                // 更新进度
                progress.setLastUpdateTime(LocalDateTime.now());
                progressCache.put(taskId, progress);
                
                // 添加批次间延迟，减少API压力
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("任务被中断", ie);
                }
            }
        }
    }
    
    /**
     * 处理单个文档批次（带重试和降级）
     */
    private boolean processDocumentBatch(List<Document> documents, LoadProgress progress, String taskId) {
        int maxRetries = 5;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                // 尝试批量插入
                vectorStore.add(documents);
                progress.setProcessedItems(progress.getProcessedItems() + documents.size());
                log.debug("任务 {} - 批量插入 {} 条数据成功", taskId, documents.size());
                return true;
                
            } catch (Exception e) {
                retryCount++;
                String errorMsg = e.getMessage();
                
                // 检查是否是网络连接问题
                if (errorMsg != null && (errorMsg.contains("GOAWAY") || errorMsg.contains("I/O error") || errorMsg.contains("Connection reset"))) {
                    log.warn("任务 {} - 检测到网络连接问题，第 {} 次重试: {}", taskId, retryCount, errorMsg);
                    
                    if (retryCount >= 3) {
                        // 3次重试后降级到单条处理
                        log.info("任务 {} - 网络不稳定，切换到单条处理模式", taskId);
                        return processSingleItemsWithDelay(documents, progress, taskId);
                    }
                    
                    // 指数退避等待
                    try {
                        long waitTime = (long) Math.pow(2, retryCount) * 1000; // 2s, 4s, 8s, 16s, 32s
                        log.info("任务 {} - 等待 {}ms 后重试", taskId, waitTime);
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("任务被中断", ie);
                    }
                } else {
                    log.error("任务 {} - 非网络错误，第 {} 次重试: {}", taskId, retryCount, errorMsg);
                    if (retryCount >= maxRetries) {
                        break;
                    }
                    try {
                        Thread.sleep(1000 * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("任务被中断", ie);
                    }
                }
            }
        }
        
        // 所有重试都失败，尝试单条处理
        log.warn("任务 {} - 批量处理完全失败，尝试单条处理", taskId);
        return processSingleItemsWithDelay(documents, progress, taskId);
    }
    
    /**
     * 单条处理文档（当批量处理失败时的备选方案）
     */
    private boolean processSingleItems(List<Document> documents, LoadProgress progress, String taskId) {
        return processSingleItemsWithDelay(documents, progress, taskId);
    }
    
    /**
     * 带延迟的单条处理文档
     */
    private boolean processSingleItemsWithDelay(List<Document> documents, LoadProgress progress, String taskId) {
        int successCount = 0;
        int failCount = 0;
        
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            boolean itemSuccess = false;
            int itemRetries = 0;
            int maxItemRetries = 3;
            
            while (!itemSuccess && itemRetries < maxItemRetries) {
                try {
                    vectorStore.add(List.of(document));
                    successCount++;
                    itemSuccess = true;
                    
                    // 更新进度（只在成功时更新）
                    progress.setProcessedItems(progress.getProcessedItems() + 1);
                    
                    log.debug("任务 {} - 单条处理成功 ({}/{})", taskId, i + 1, documents.size());
                    
                } catch (Exception e) {
                    itemRetries++;
                    String errorMsg = e.getMessage();
                    
                    if (errorMsg != null && (errorMsg.contains("GOAWAY") || errorMsg.contains("I/O error"))) {
                        log.debug("任务 {} - 单条处理网络错误，第 {} 次重试: {}", taskId, itemRetries, errorMsg);
                    } else {
                        log.debug("任务 {} - 单条处理失败，第 {} 次重试: {}", taskId, itemRetries, errorMsg);
                    }
                    
                    if (itemRetries >= maxItemRetries) {
                        failCount++;
                        log.warn("任务 {} - 单条数据处理失败，跳过: {}", taskId, errorMsg);
                    } else {
                        // 单条重试间隔
                        try {
                            Thread.sleep(1000 * itemRetries);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("任务被中断", ie);
                        }
                    }
                }
            }
            
            // 每条数据处理后的延迟，避免API限流
            if (itemSuccess) {
                try {
                    Thread.sleep(800); // 增加延迟到800ms
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("任务被中断", ie);
                }
            }
        }
        
        log.info("任务 {} - 单条处理完成，成功 {} 条，失败 {} 条", taskId, successCount, failCount);
        return successCount > 0;
    }

    /**
     * 获取加载进度
     */
    public LoadProgress getLoadProgress(String taskId) {
        return progressCache.get(taskId);
    }
    
    /**
     * 获取所有任务进度
     */
    public Map<String, LoadProgress> getAllLoadProgress() {
        return new HashMap<>(progressCache);
    }
    
    /**
     * 暂停任务
     */
    public boolean pauseTask(String taskId) {
        LoadProgress progress = progressCache.get(taskId);
        if (progress != null && "RUNNING".equals(progress.getStatus())) {
            progress.setStatus("PAUSED");
            progress.setLastUpdateTime(LocalDateTime.now());
            progressCache.put(taskId, progress);
            log.info("任务 {} 已暂停", taskId);
            return true;
        }
        return false;
    }
    
    /**
     * 删除任务进度记录
     */
    public boolean removeTask(String taskId) {
        LoadProgress removed = progressCache.remove(taskId);
        if (removed != null) {
            log.info("任务 {} 的进度记录已删除", taskId);
            return true;
        }
        return false;
    }
    
    /**
     * 将 ItemDTO 列表转换为 Document 列表
     */
    private List<Document> convertItemsToDocuments(List<ItemDTO> items) {
        return items.stream()
                .map(this::convertItemToDocument)
                .collect(Collectors.toList());
    }
    
    /**
     * 将单个 ItemDTO 转换为 Document
     */
    private Document convertItemToDocument(ItemDTO item) {
        // 构建商品的文本描述，用于向量化
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
        
        // 构建元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", item.getId());
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
        metadata.put("type", "product"); // 标识这是商品数据
        
        return new Document(contentBuilder.toString().trim(), metadata);
    }

    public Flux<String> directRag(String question) {

        return chatClient
                .prompt()
                .user(question)
                .advisors(productAdvisor)
                .stream()
                .content();
    }
}