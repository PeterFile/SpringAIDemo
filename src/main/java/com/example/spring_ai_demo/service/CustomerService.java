package com.example.spring_ai_demo.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CustomerService {

    // 本地会话记忆存储（Spring AI 提供）
    private final ChatMemoryRepository chatMemoryRep = new InMemoryChatMemoryRepository();

    ChatMemory chatMemory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(chatMemoryRep)
            .maxMessages(10)
            .build();
    // 记忆过期设置（可按需调整）
    private static final Duration TTL = Duration.ofMinutes(15);

    // 会话过期时间记录
    private final Map<String, Instant> expireAtMap = new ConcurrentHashMap<>();

    // 基于 ChatClient + Memory Advisor 的对话客户端（Spring AI）
    private final ChatClient chatClient;

    public CustomerService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        你是一个专业的客服助手，请遵循以下原则：
                        1. 友好、耐心地回答用户问题
                        2. 如果不确定答案，请诚实说明并建议联系人工客服
                        3. 保持专业和礼貌的语调
                        4. 回答要简洁明了
                        """)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    public String chat(String sessionId, String userMessage) {
        refreshOrResetIfExpired(sessionId);

        String answer = chatClient
                .prompt()
                // 关键：把会话 id 传给 Memory Advisor，自动存取历史
                .advisors(a -> a.param("conversationId", sessionId))
                .user(userMessage)
                .call()
                .content();

        // 刷新过期时间
        expireAtMap.put(sessionId, Instant.now().plus(TTL));
        return answer;
    }

    public void clear(String sessionId) {
        chatMemory.clear(sessionId);
        expireAtMap.remove(sessionId);
    }

    private void refreshOrResetIfExpired(String sessionId) {
        Instant now = Instant.now();
        Instant expireAt = expireAtMap.get(sessionId);
        if (expireAt == null) {
            expireAtMap.put(sessionId, now.plus(TTL));
            return;
        }
        if (now.isAfter(expireAt)) {
            // 到期：清空该会话在 Spring AI 记忆中的历史
            chatMemory.clear(sessionId);
            expireAtMap.put(sessionId, now.plus(TTL));
        }
    }
}