package com.example.spring_ai_demo.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingModel;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ZhipuAiConfig {

    @Value("${spring.ai.zhipuai.api-key}")
    private String apiKey;

    @Bean
    public ZhiPuAiApi zhiPuAiApi() {
        return new ZhiPuAiApi(apiKey);
    }

    @Bean
    @Primary
    public EmbeddingModel zhipuAiEmbeddingModel(ZhiPuAiApi zhiPuAiApi) {
        return new ZhiPuAiEmbeddingModel(zhiPuAiApi, MetadataMode.EMBED,
                ZhiPuAiEmbeddingOptions.builder()
                        .model("embedding-3")
                        .dimensions(1536)
                        .build());
    }
}