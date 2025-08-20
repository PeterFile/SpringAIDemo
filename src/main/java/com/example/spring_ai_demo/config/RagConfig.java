package com.example.spring_ai_demo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class RagConfig {

    @Bean
    public RetrievalAugmentationAdvisor productAdvisor(MilvusVectorStore vectorStore) {

        log.info("当前注入的 VectorStore 实现类是: {}", vectorStore.getClass().getName());

        // 1. 定义文档检索器 (Retriever)
        // 这一步负责从VectorStore中根据用户问题查询相关文档
        var documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(0.5)
                .topK(6)
                .build();

        PromptTemplate customPromptTemplate = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template("""
                        你是一个智能商品推荐助手。
                        你的任务是：严格根据下面【商品信息】部分提供的内容，用中文回答【问题】。
                                           
                        回答规则：
                        - 只使用【商品信息】中的信息，不要依赖任何外部知识。
                        - 如果【商品信息】内容无法回答【问题】，就直接说："根据现有商品信息，我无法回答这个问题。"
                        - 当推荐商品时，请包含商品的名称、价格、品牌、分类、库存、销量、评论数等关键信息。
                        - 你的回答应简洁、准确并完全使用中文。
                        - 如果涉及多个商品，请按相关性排序展示。
                        - 可以根据销量和评论数来判断商品的受欢迎程度。
                        - 注意库存情况，优先推荐有库存的商品。
                                        
                        【商品信息】:
                        <question_answer_context>
                                        
                        【问题】:
                        <query>
                        """)
                .build();

        // 2. 定义查询增强器 (Augmenter)
        // 这一步负责将检索到的文档(context)和用户问题(query)组装成最终的Prompt
        // 我们将你原来的PromptTemplate用在这里
        var contextualQueryAugmenter = ContextualQueryAugmenter.builder().promptTemplate(customPromptTemplate).build();

        // 3. 构建并返回 RetrievalAugmentationAdvisor
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(contextualQueryAugmenter)
                .build();
    }
}
