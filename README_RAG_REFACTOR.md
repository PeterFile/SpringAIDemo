# ElasticsearchRAGService 重构说明

## 概述
本次重构将 ElasticsearchRAGService 扩展为支持从 item-service 微服务获取商品数据并填充到 Elasticsearch 向量数据库中。

## 主要变更

### 1. 添加依赖
在 `pom.xml` 中添加了 OpenFeign 依赖：
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

### 2. 启用 OpenFeign
在主应用类 `SpringAiDemoApplication` 中添加了 `@EnableFeignClients` 注解。

### 3. 创建 DTO 类
- `PageQuery.java`: 分页查询参数
- `ItemDTO.java`: 商品数据传输对象，包含商品ID、名称、价格、库存、图片、分类、品牌、规格、销量、评论数、广告标识、状态等字段
- `PageDTO.java`: 分页结果包装类

### 4. 创建 OpenFeign 客户端
`ItemServiceClient.java` 定义了与 item-service 通信的接口：
```java
@FeignClient(name = "item-service")
public interface ItemServiceClient {
    @GetMapping("/page")
    PageDTO<ItemDTO> queryItemByPage(PageQuery query, 
                                   @RequestHeader(value = "truth", required = false) String truth);
}
```

### 5. 重构 ElasticsearchRAGService
添加了以下新功能：
- `loadAllItemsIntoVectorStore()`: 从 item-service 加载所有商品数据
- `convertItemsToDocuments()`: 将商品数据转换为向量文档
- `convertItemToDocument()`: 单个商品转换逻辑
- 优化了 `directRag()` 方法的提示词，专门针对商品推荐场景

### 6. 创建控制器
`ItemRAGController.java` 提供了以下 API：
- `POST /api/item-rag/load-items`: 加载商品数据到向量数据库
- `GET /api/item-rag/query?question=xxx`: 基于商品数据进行 RAG 问答

### 7. 配置优化
- 添加了 `FeignConfig.java` 配置类
- 在 `application.yml` 中添加了 OpenFeign 相关配置

## 使用方法

### 1. 启动服务
确保以下服务已启动：
- Nacos 注册中心 (192.168.85.129:8848)
- Elasticsearch (192.168.85.129:9200)
- item-service 微服务

### 2. 加载商品数据

#### 启动新的加载任务
```bash
# 使用默认配置
curl -X POST http://localhost:8180/api/item-rag/load-items

# 自定义页面大小和批处理大小
curl -X POST "http://localhost:8180/api/item-rag/load-items?pageSize=200&batchSize=100"
```

#### 查看加载进度
```bash
# 查看特定任务进度
curl http://localhost:8180/api/item-rag/progress/{taskId}

# 查看所有任务进度
curl http://localhost:8180/api/item-rag/progress
```

#### 断点续传
```bash
# 恢复指定任务
curl -X POST http://localhost:8180/api/item-rag/resume-load/{taskId}
```

#### 任务管理
```bash
# 暂停任务
curl -X POST http://localhost:8180/api/item-rag/pause/{taskId}

# 删除任务记录
curl -X DELETE http://localhost:8180/api/item-rag/task/{taskId}
```

### 3. 进行商品查询
```bash
curl "http://localhost:8180/api/item-rag/query?question=推荐一些手机"
```

## 核心特性

### 分批处理与内存优化
- 分页获取数据，默认每页 100 条
- 分批处理向量化，默认每批 50 条
- 避免大量数据同时加载到内存
- 支持自定义页面大小和批处理大小

### 断点续传
- 每个加载任务都有唯一的任务ID
- 实时记录加载进度（当前页、已处理条数等）
- 支持任务中断后从断点继续加载
- 任务状态管理（运行中、已完成、失败、暂停）

### 数据转换
- 将商品信息转换为结构化的文本描述，包含名称、分类、品牌、价格、规格、库存、销量、评论数等信息
- 保留商品的完整元数据（ID、名称、分类、品牌、价格、库存、图片、规格、销量、评论数、广告标识、状态等）
- 为每个商品添加 "product" 类型标识

### 智能问答
- 专门针对商品推荐场景优化的提示词
- 支持基于商品名称、分类、品牌、价格范围等多维度查询
- 返回包含价格、库存、销量、评论数等关键信息的推荐结果
- 根据销量和评论数判断商品受欢迎程度
- 优先推荐有库存的商品

## 错误处理
- 完整的异常处理和日志记录
- 网络超时配置（连接超时 10 秒，读取超时 60 秒）
- 优雅的错误提示信息

## 性能优化特性

### 内存管理
- 分批处理避免内存溢出
- 及时释放已处理的数据
- 支持大规模数据集处理

### 任务监控
- 实时进度跟踪
- 详细的任务状态信息
- 错误信息记录和恢复

### 灵活配置
- 可调整的页面大小和批处理大小
- 支持根据系统资源动态调整参数

## 扩展性
该架构支持：
- 添加更多微服务数据源
- 扩展商品属性和元数据
- 自定义向量化策略
- 个性化推荐算法集成
- 分布式任务处理
- 持久化进度存储（可扩展到数据库）