package com.example.spring_ai_demo.service;

import com.example.spring_ai_demo.client.ItemServiceClient;
import com.example.spring_ai_demo.dto.ItemDTO;
import com.example.spring_ai_demo.dto.LoadProgress;
import com.example.spring_ai_demo.dto.PageDTO;
import com.example.spring_ai_demo.dto.PageQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MultiThreadElasticsearchRAGService {

    private final ElasticsearchVectorStore vectorStore;
    private final ItemServiceClient itemServiceClient;
    private final Executor vectorProcessExecutor;
    private final Executor dataFetchExecutor;
    
    // 用于存储加载进度的内存缓存
    private final Map<String, LoadProgress> progressCache = new ConcurrentHashMap<>();
    
    // 限流器：控制向量化API的调用频率
    private final Semaphore vectorApiSemaphore = new Semaphore(3); // 最多3个并发向量化请求
    
    // 默认配置
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int DEFAULT_BATCH_SIZE = 5;
    private static final int DEFAULT_THREAD_COUNT = 3;

    public MultiThreadElasticsearchRAGService(ElasticsearchVectorStore vectorStore,
                                            ItemServiceClient itemServiceClient,
                                            @Qualifier("vectorProcessExecutor") Executor vectorProcessExecutor,
                                            @Qualifier("dataFetchExecutor") Executor dataFetchExecutor) {
        this.vectorStore = vectorStore;
        this.itemServiceClient = itemServiceClient;
        this.vectorProcessExecutor = vectorProcessExecutor;
        this.dataFetchExecutor = dataFetchExecutor;
    }

    /**
     * 多线程加载所有商品数据到向量数据库
     */
    public String loadAllItemsIntoVectorStoreMultiThread(String resumeTaskId, Integer pageSize, Integer batchSize, Integer threadCount) {
        String taskId = resumeTaskId != null ? resumeTaskId : UUID.randomUUID().toString();
        
        LoadProgress progress = progressCache.getOrDefault(taskId, new LoadProgress());
        progress.setTaskId(taskId);
        progress.setBatchSize(batchSize != null ? batchSize : DEFAULT_BATCH_SIZE);
        
        if (resumeTaskId == null) {
            progress.setCurrentPage(1);
            progress.setProcessedItems(0);
            log.info("开始多线程商品数据加载任务，任务ID: {}，线程数: {}", taskId, threadCount != null ? threadCount : DEFAULT_THREAD_COUNT);
        } else {
            log.info("恢复多线程商品数据加载任务，任务ID: {}，从第 {} 页开始", taskId, progress.getCurrentPage());
        }
        
        progressCache.put(taskId, progress);
        
        // 异步执行多线程加载
        CompletableFuture.runAsync(() -> {
            try {
                executeMultiThreadLoad(taskId, pageSize, batchSize, threadCount);
            } catch (Exception e) {
                log.error("多线程加载任务 {} 执行失败", taskId, e);
                LoadProgress failedProgress = progressCache.get(taskId);
                if (failedProgress != null) {
                    failedProgress.setStatus("FAILED");
                    failedProgress.setErrorMessage(e.getMessage());
                    failedProgress.setLastUpdateTime(LocalDateTime.now());
                    progressCache.put(taskId, failedProgress);
                }
            }
        }, dataFetchExecutor);
        
        return taskId;
    }

    /**
     * 执行多线程加载逻辑
     */
    private void executeMultiThreadLoad(String taskId, Integer pageSize, Integer batchSize, Integer threadCount) {
        LoadProgress progress = progressCache.get(taskId);
        if (progress == null) return;
        
        int currentPageSize = pageSize != null ? pageSize : DEFAULT_PAGE_SIZE;
        int currentBatchSize = batchSize != null ? batchSize : DEFAULT_BATCH_SIZE;
        int currentThreadCount = threadCount != null ? threadCount : DEFAULT_THREAD_COUNT;
        
        // 首先获取总页数
        int totalPages = getTotalPages(currentPageSize);
        progress.setTotalPages(totalPages);
        progressCache.put(taskId, progress);
        
        log.info("任务 {} - 总共 {} 页数据需要处理", taskId, totalPages);
        
        // 创建页面处理任务队列
        BlockingQueue<Integer> pageQueue = new LinkedBlockingQueue<>();
        for (int page = progress.getCurrentPage(); page <= totalPages; page++) {
            pageQueue.offer(page);
        }
        
        // 创建线程池处理页面
        ExecutorService pageProcessorPool = Executors.newFixedThreadPool(currentThreadCount);
        CountDownLatch latch = new CountDownLatch(currentThreadCount);
        AtomicInteger processedPages = new AtomicInteger(0);
        
        // 启动工作线程
        for (int i = 0; i < currentThreadCount; i++) {
            final int threadIndex = i;
            pageProcessorPool.submit(() -> {
                try {
                    processPages(taskId, pageQueue, currentPageSize, currentBatchSize, threadIndex, processedPages);
                } catch (Exception e) {
                    log.error("任务 {} - 线程 {} 处理失败", taskId, threadIndex, e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            // 等待所有线程完成
            latch.await();
            
            // 任务完成
            progress.setStatus("COMPLETED");
            progress.setLastUpdateTime(LocalDateTime.now());
            progressCache.put(taskId, progress);
            
            log.info("任务 {} - 多线程加载完成，共处理 {} 条数据", taskId, progress.getProcessedItems());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("任务 {} - 等待线程完成时被中断", taskId);
            progress.setStatus("FAILED");
            progress.setErrorMessage("任务被中断");
            progressCache.put(taskId, progress);
        } finally {
            pageProcessorPool.shutdown();
        }
    }

    /**
     * 处理页面队列中的页面
     */
    private void processPages(String taskId, BlockingQueue<Integer> pageQueue, int pageSize, int batchSize, int threadIndex, AtomicInteger processedPages) {
        while (!pageQueue.isEmpty()) {
            Integer pageNo = pageQueue.poll();
            if (pageNo == null) break;
            
            try {
                log.debug("任务 {} - 线程 {} 开始处理第 {} 页", taskId, threadIndex, pageNo);
                
                // 获取页面数据
                PageQuery pageQuery = new PageQuery();
                pageQuery.setPageNo(pageNo);
                pageQuery.setPageSize(pageSize);
                
                PageDTO<ItemDTO> pageResult = itemServiceClient.queryItemByPage(
                    pageQuery.getPageNo(),
                    pageQuery.getPageSize(),
                    pageQuery.getSortBy(),
                    pageQuery.getIsAsc(),
                    null
                );
                
                if (pageResult != null && pageResult.getList() != null && !pageResult.getList().isEmpty()) {
                    // 处理当前页的数据
                    processPageDataMultiThread(taskId, pageResult.getList(), batchSize, threadIndex);
                    
                    // 更新进度
                    LoadProgress progress = progressCache.get(taskId);
                    if (progress != null) {
                        progress.setCurrentPage(Math.max(progress.getCurrentPage(), pageNo));
                        progress.setLastUpdateTime(LocalDateTime.now());
                        progressCache.put(taskId, progress);
                    }
                    
                    int completed = processedPages.incrementAndGet();
                    log.info("任务 {} - 线程 {} 完成第 {} 页，总进度: {}", taskId, threadIndex, pageNo, completed);
                } else {
                    log.debug("任务 {} - 线程 {} 第 {} 页无数据", taskId, threadIndex, pageNo);
                }
                
            } catch (Exception e) {
                log.error("任务 {} - 线程 {} 处理第 {} 页失败: {}", taskId, threadIndex, pageNo, e.getMessage());
                // 将失败的页面重新放回队列末尾重试
                if (!pageQueue.offer(pageNo)) {
                    log.warn("任务 {} - 无法重新排队页面 {}", taskId, pageNo);
                }
            }
        }
    }

    /**
     * 多线程处理页面数据
     */
    private void processPageDataMultiThread(String taskId, List<ItemDTO> items, int batchSize, int threadIndex) {
        // 将数据分批
        List<List<ItemDTO>> batches = partitionList(items, batchSize);
        
        // 并发处理每个批次
        List<CompletableFuture<Void>> futures = batches.stream()
            .map(batch -> CompletableFuture.runAsync(() -> {
                try {
                    // 获取向量化API许可
                    vectorApiSemaphore.acquire();
                    try {
                        processBatchWithRetry(taskId, batch, threadIndex);
                    } finally {
                        vectorApiSemaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("任务 {} - 线程 {} 获取API许可被中断", taskId, threadIndex);
                } catch (Exception e) {
                    log.error("任务 {} - 线程 {} 批次处理失败", taskId, threadIndex, e);
                }
            }, vectorProcessExecutor))
            .collect(Collectors.toList());
        
        // 等待所有批次完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * 带重试的批次处理
     */
    private void processBatchWithRetry(String taskId, List<ItemDTO> items, int threadIndex) {
        List<Document> documents = items.stream()
            .map(this::convertItemToDocument)
            .collect(Collectors.toList());
        
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                vectorStore.add(documents);
                
                // 更新进度
                LoadProgress progress = progressCache.get(taskId);
                if (progress != null) {
                    progress.setProcessedItems(progress.getProcessedItems() + documents.size());
                    progress.setLastUpdateTime(LocalDateTime.now());
                    progressCache.put(taskId, progress);
                }
                
                log.debug("任务 {} - 线程 {} 成功处理 {} 条数据", taskId, threadIndex, documents.size());
                return;
                
            } catch (Exception e) {
                retryCount++;
                log.warn("任务 {} - 线程 {} 批次处理失败，第 {} 次重试: {}", taskId, threadIndex, retryCount, e.getMessage());
                
                if (retryCount >= maxRetries) {
                    log.error("任务 {} - 线程 {} 批次处理最终失败，跳过 {} 条数据", taskId, threadIndex, documents.size());
                    return;
                }
                
                try {
                    Thread.sleep(1000 * retryCount); // 递增等待
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * 获取总页数
     */
    private int getTotalPages(int pageSize) {
        try {
            PageQuery pageQuery = new PageQuery();
            pageQuery.setPageNo(1);
            pageQuery.setPageSize(pageSize);
            
            PageDTO<ItemDTO> firstPage = itemServiceClient.queryItemByPage(
                pageQuery.getPageNo(),
                pageQuery.getPageSize(),
                pageQuery.getSortBy(),
                pageQuery.getIsAsc(),
                null
            );
            
            if (firstPage != null && firstPage.getPages() != null) {
                return firstPage.getPages();
            }
        } catch (Exception e) {
            log.error("获取总页数失败", e);
        }
        return 100; // 默认值
    }

    /**
     * 将列表分批
     */
    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return partitions;
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
        metadata.put("type", "product");
        
        return new Document(contentBuilder.toString().trim(), metadata);
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
     * 删除任务记录
     */
    public boolean removeTask(String taskId) {
        LoadProgress removed = progressCache.remove(taskId);
        if (removed != null) {
            log.info("任务 {} 的进度记录已删除", taskId);
            return true;
        }
        return false;
    }
}