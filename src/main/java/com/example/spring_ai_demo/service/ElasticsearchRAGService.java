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
                new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!", Map.of("meta1", "meta1")),
                new Document("The World is Big and Salvation Lurks Around the Corner"),
                new Document("You walk forward facing the past and you turn back toward the future.", Map.of("meta2", "meta2")));

        // Add the documents to Elasticsearch
        try {
            vectorStore.add(documents);
        } catch (UnknownContentTypeException ex) {
            String rawResponse = ex.getResponseBodyAsString();
            System.out.println("API错误响应: " + rawResponse);
        }
    }

    public String directRag(String question) {
        // Query the vector store for documents related to the question
        List<Document> vectorStoreResult =
                vectorStore.doSimilaritySearch(SearchRequest.builder().query(question).topK(5).build());

        // Merging the documents into a single string
        String documents = vectorStoreResult.stream()
                .map(Document::getText)
                .collect(Collectors.joining(System.lineSeparator()));

        // Exit if the vector search didn't find any results
        if (documents.isEmpty()) {
            return "No relevant context found. Please change your question.";
        }

        // Setting the prompt with the context
        String prompt = """
           Use the information from the DOCUMENTS section to provide accurate answers to the
           question in the QUESTION section.
           If unsure, simply state that you don't know.
          
           DOCUMENTS:
           """ + documents
                + """
           QUESTION:
           """ + question;


        // Calling the chat model with the question
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();

    }
}
