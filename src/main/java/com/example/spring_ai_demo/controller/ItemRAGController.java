package com.example.spring_ai_demo.controller;

import com.example.spring_ai_demo.dto.LoadProgress;
import com.example.spring_ai_demo.service.ElasticsearchRAGService;
import com.example.spring_ai_demo.service.MultiThreadElasticsearchRAGService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/item-rag")
@RequiredArgsConstructor
public class ItemRAGController {

    private final ElasticsearchRAGService elasticsearchRAGService;
    private final MultiThreadElasticsearchRAGService multiThreadService;

    /**
     * 从 item-service 加载所有商品数据到向量数据库（新任务）
     */
    @PostMapping("/load-items")
    public Map<String, Object> loadItems(@RequestParam(required = false) Integer pageSize,
                                       @RequestParam(required = false) Integer batchSize) {
        Map<String, Object> result = new HashMap<>();
        try {
            String taskId = elasticsearchRAGService.loadAllItemsIntoVectorStore(null, pageSize, batchSize);
            result.put("success", true);
            result.put("taskId", taskId);
            result.put("message", "商品数据加载任务已启动");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "商品数据加载失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 恢复加载任务（断点续传）
     */
    @PostMapping("/resume-load/{taskId}")
    public Map<String, Object> resumeLoad(@PathVariable String taskId,
                                        @RequestParam(required = false) Integer pageSize,
                                        @RequestParam(required = false) Integer batchSize) {
        Map<String, Object> result = new HashMap<>();
        try {
            String resumedTaskId = elasticsearchRAGService.loadAllItemsIntoVectorStore(taskId, pageSize, batchSize);
            result.put("success", true);
            result.put("taskId", resumedTaskId);
            result.put("message", "商品数据加载任务已恢复");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "恢复加载任务失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取加载进度
     */
    @GetMapping("/progress/{taskId}")
    public LoadProgress getProgress(@PathVariable String taskId) {
        return elasticsearchRAGService.getLoadProgress(taskId);
    }

    /**
     * 获取所有任务进度
     */
    @GetMapping("/progress")
    public Map<String, LoadProgress> getAllProgress() {
        return elasticsearchRAGService.getAllLoadProgress();
    }

    /**
     * 暂停任务
     */
    @PostMapping("/pause/{taskId}")
    public Map<String, Object> pauseTask(@PathVariable String taskId) {
        Map<String, Object> result = new HashMap<>();
        boolean success = elasticsearchRAGService.pauseTask(taskId);
        result.put("success", success);
        result.put("message", success ? "任务已暂停" : "暂停任务失败");
        return result;
    }

    /**
     * 删除任务记录
     */
    @DeleteMapping("/task/{taskId}")
    public Map<String, Object> removeTask(@PathVariable String taskId) {
        Map<String, Object> result = new HashMap<>();
        boolean success = elasticsearchRAGService.removeTask(taskId);
        result.put("success", success);
        result.put("message", success ? "任务记录已删除" : "删除任务记录失败");
        return result;
    }

    /**
     * 基于商品数据进行RAG问答
     */
    @GetMapping("/query")
    public String queryItems(@RequestParam String question) {
        return elasticsearchRAGService.directRag(question);
    }

    /**
     * 安全模式加载 - 使用更小的批处理大小和更多重试
     */
    @PostMapping("/safe-load-items")
    public Map<String, Object> safeLoadItems() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 使用更小的批处理大小：每页20条，每批3条
            String taskId = elasticsearchRAGService.loadAllItemsIntoVectorStore(null, 20, 3);
            result.put("success", true);
            result.put("taskId", taskId);
            result.put("message", "安全模式加载任务已启动（小批量处理）");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "安全模式加载失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 超安全模式加载 - 单条处理，最大稳定性
     */
    @PostMapping("/ultra-safe-load-items")
    public Map<String, Object> ultraSafeLoadItems() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 超小批量：每页10条，每批1条（实际上是单条处理）
            String taskId = elasticsearchRAGService.loadAllItemsIntoVectorStore(null, 10, 1);
            result.put("success", true);
            result.put("taskId", taskId);
            result.put("message", "超安全模式加载任务已启动（单条处理，最大稳定性）");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "超安全模式加载失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 多线程加载 - 高性能模式
     */
    @PostMapping("/multi-thread-load-items")
    public Map<String, Object> multiThreadLoadItems(@RequestParam(required = false) Integer pageSize,
                                                   @RequestParam(required = false) Integer batchSize,
                                                   @RequestParam(required = false) Integer threadCount) {
        Map<String, Object> result = new HashMap<>();
        try {
            String taskId = multiThreadService.loadAllItemsIntoVectorStoreMultiThread(null, pageSize, batchSize, threadCount);
            result.put("success", true);
            result.put("taskId", taskId);
            result.put("message", "多线程加载任务已启动（高性能模式）");
            result.put("threadCount", threadCount != null ? threadCount : 3);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "多线程加载失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 恢复多线程加载任务
     */
    @PostMapping("/resume-multi-thread-load/{taskId}")
    public Map<String, Object> resumeMultiThreadLoad(@PathVariable String taskId,
                                                    @RequestParam(required = false) Integer pageSize,
                                                    @RequestParam(required = false) Integer batchSize,
                                                    @RequestParam(required = false) Integer threadCount) {
        Map<String, Object> result = new HashMap<>();
        try {
            String resumedTaskId = multiThreadService.loadAllItemsIntoVectorStoreMultiThread(taskId, pageSize, batchSize, threadCount);
            result.put("success", true);
            result.put("taskId", resumedTaskId);
            result.put("message", "多线程加载任务已恢复");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "恢复多线程加载任务失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取多线程任务进度
     */
    @GetMapping("/multi-thread-progress/{taskId}")
    public LoadProgress getMultiThreadProgress(@PathVariable String taskId) {
        return multiThreadService.getLoadProgress(taskId);
    }

    /**
     * 获取所有多线程任务进度
     */
    @GetMapping("/multi-thread-progress")
    public Map<String, LoadProgress> getAllMultiThreadProgress() {
        return multiThreadService.getAllLoadProgress();
    }

    /**
     * 删除多线程任务记录
     */
    @DeleteMapping("/multi-thread-task/{taskId}")
    public Map<String, Object> removeMultiThreadTask(@PathVariable String taskId) {
        Map<String, Object> result = new HashMap<>();
        boolean success = multiThreadService.removeTask(taskId);
        result.put("success", success);
        result.put("message", success ? "多线程任务记录已删除" : "删除多线程任务记录失败");
        return result;
    }
}