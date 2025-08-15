package com.example.spring_ai_demo.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.client.UnknownContentTypeException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ElasticsearchRAGService {

    private final ElasticsearchVectorStore vectorStore;
    private final ChatClient chatClient;

    private final EmbeddingModel embeddingModel;

    public ElasticsearchRAGService(ElasticsearchVectorStore  vectorStore, ChatClient.Builder chatClient, EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient.build();
        this.embeddingModel = embeddingModel;
    }


    public void ingest() {
        Document testDoc = new Document("维度验证文本");

        float[] testEmbedding = this.embeddingModel.embed(testDoc);
        int actualDims = testEmbedding.length;
        System.out.println("实际嵌入维度: " + actualDims + " | 配置维度: 1536");

        List <Document> documents = List.of(
                new Document("苹果公司在2023年秋季发布会上推出了iPhone 15系列手机，该系列采用了USB-C接口，并对摄像头系统进行了升级。", Map.of("category", "科技")),
                new Document("马里亚纳海沟是地球上已知最深的海沟，其最深处挑战者深渊的深度约为11000米。", Map.of("category", "地理")),
                new Document("梵高的名画《星夜》目前收藏于纽约现代艺术博物馆（MoMA）。这幅画以其独特的漩涡状笔触和生动的色彩而闻名于世。", Map.of("category", "艺术")),
                new Document("光合作用是植物、藻类和某些细菌利用光能将二氧化碳和水转化为有机物并释放氧气的过程。", Map.of("category", "生物")),
                new Document("日本的京都是一座以其古老的寺庙、美丽的花园和传统的艺伎区而闻名的城市。", Map.of("category", "旅游")));

        // Add the documents to Elasticsearch
        try {
            vectorStore.add(documents);
        } catch (UnknownContentTypeException ex) {
            String rawResponse = ex.getResponseBodyAsString();
            System.out.println("API错误响应: " + rawResponse);
        }
    }

    public String directRag(String question) {
        List<Document> vectorStoreResult =
                vectorStore.doSimilaritySearch(SearchRequest.builder().query(question).topK(5).build());

        String documents = vectorStoreResult.stream()
                .map(Document::getText)
                .collect(Collectors.joining(System.lineSeparator()));

        if (documents.isEmpty()) {
            return "No relevant context found. Please change your question.";
        }
        System.out.println("获取到的文档：" + documents);
        String prompt = """
           你是一个智能问答助手。
           你的任务是：严格根据下面【文档】部分提供的内容，用中文回答【问题】。
           - 只使用【文档】中的信息，不要依赖任何外部知识。
           - 如果【文档】内容无法回答【问题】，就直接说：“根据提供的文档，我无法回答这个问题。”
           - 你的回答应简洁、准确并完全使用中文。
        
           【文档】:
           """ + documents+ """
        
           【问题】:
           """ + question;

        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();

    }
}
