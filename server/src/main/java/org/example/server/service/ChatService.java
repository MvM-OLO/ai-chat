package org.example.server.service;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.server.config.MiniMaxConfig;
import org.example.server.domain.ChatMessage;
import org.example.server.domain.ChatSession;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final EasyEntityQuery entityQuery;
    private final MiniMaxConfig miniMaxConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 1. 创建新会话
     */
    public String createSession(String title) {
        String sessionId = UUID.randomUUID().toString();
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setTitle(title == null ? "新对话" : title);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());

        entityQuery.insertable(session).executeRows();
        return sessionId;
    }

    /**
     * 2. 获取指定会话的历史消息
     */
    public List<ChatMessage> getHistoryMessages(String sessionId) {
        return entityQuery.queryable(ChatMessage.class)
                .where(m -> m.sessionId().eq(sessionId))
                .orderBy(m -> m.createTime().asc())
                .toList();
    }

    /**
     * 3. 发送消息并获取流式回复 (🔥 核心逻辑 - 接入 MiniMax)
     */
    public SseEmitter chatStream(String sessionId, String userContent) {
        // 1. 先把用户的提问存入数据库
        ChatMessage userMessage = new ChatMessage();
        userMessage.setId(UUID.randomUUID().toString());
        userMessage.setSessionId(sessionId);
        userMessage.setRole("user");
        userMessage.setContent(userContent);
        userMessage.setCreateTime(LocalDateTime.now());
        entityQuery.insertable(userMessage).executeRows();

        // 2. 创建 SseEmitter (0L 表示永不超时)
        SseEmitter emitter = new SseEmitter(0L);

        // 3. 异步调用 MiniMax API
        CompletableFuture.runAsync(() -> {
            try {
                // 构建历史消息列表 (用于多轮对话上下文)
                List<ChatMessage> history = getHistoryMessages(sessionId);
                List<Map<String, String>> messages = new ArrayList<>();

                // 添加系统提示
                messages.add(Map.of("role", "system", "content", "你是一个有用的 AI 助手，请用中文回答用户的问题。"));

                // 添加历史消息（最多保留最近 20 条，避免 token 超限）
                int start = Math.max(0, history.size() - 20);
                for (int i = start; i < history.size(); i++) {
                    ChatMessage msg = history.get(i);
                    messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
                }

                // 构建请求体 (OpenAI 兼容格式)
                Map<String, Object> requestBody = new LinkedHashMap<>();
                requestBody.put("model", miniMaxConfig.getModel());
                requestBody.put("messages", messages);
                requestBody.put("stream", true);
                requestBody.put("temperature", 0.8);
                requestBody.put("max_tokens", 2048);

                String requestJson = objectMapper.writeValueAsString(requestBody);

                log.info("🚀 调用 MiniMax API, model={}, sessionId={}", miniMaxConfig.getModel(), sessionId);

                // 使用 WebClient 发起流式请求
                WebClient webClient = WebClient.builder()
                        .baseUrl(miniMaxConfig.getBaseUrl())
                        .defaultHeader("Authorization", "Bearer " + miniMaxConfig.getApiKey())
                        .defaultHeader("Content-Type", "application/json")
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                        .build();

                // 用于跟踪是否在 <think> 块内（跨 chunk 状态）
                StringBuilder fullContent = new StringBuilder();

                webClient.post()
                        .uri("/chat/completions")
                        .bodyValue(requestJson)
                        .retrieve()
                        .bodyToFlux(String.class)
                        .doOnNext(chunk -> {
                            try {
                                String data = chunk.trim();
                                if (data.isEmpty()) return;

                                // 可能一个 chunk 里包含多行
                                for (String line : data.split("\n")) {
                                    line = line.trim();
                                    if (line.startsWith("data: ")) {
                                        line = line.substring(6);
                                    }
                                    if (line.equals("[DONE]") || line.isEmpty()) continue;

                                    // 解析 JSON
                                    JsonNode json = objectMapper.readTree(line);
                                    JsonNode choices = json.get("choices");
                                    if (choices != null && choices.isArray() && !choices.isEmpty()) {
                                        JsonNode delta = choices.get(0).get("delta");
                                        if (delta != null && delta.has("content")) {
                                            String content = delta.get("content").asText("");
                                            if (!content.isEmpty()) {
                                                // 直接发送原始内容（含 <think> 标签，前端负责分离展示）
                                                fullContent.append(content);
                                                // 使用 event() builder 明确设置内容类型，避免代理缓冲
                                                emitter.send(
                                                    SseEmitter.event()
                                                        .data(content, MediaType.TEXT_PLAIN)
                                                        .build()
                                                );
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("解析 chunk 失败: {}", e.getMessage());
                            }
                        })
                        .doOnComplete(() -> {
                            try {
                                // 将完整的 AI 回复存入数据库
                                if (!fullContent.isEmpty()) {
                                    ChatMessage aiMessage = new ChatMessage();
                                    aiMessage.setId(UUID.randomUUID().toString());
                                    aiMessage.setSessionId(sessionId);
                                    aiMessage.setRole("assistant");
                                    aiMessage.setContent(fullContent.toString());
                                    aiMessage.setCreateTime(LocalDateTime.now());
                                    entityQuery.insertable(aiMessage).executeRows();
                                    log.info("✅ AI 回复已保存, sessionId={}, length={}", sessionId, fullContent.length());
                                }
                                emitter.complete();
                            } catch (Exception e) {
                                log.error("完成时出错", e);
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnError(error -> {
                            log.error("❌ MiniMax API 调用失败", error);
                            try {
                                emitter.send("抱歉，AI 服务暂时不可用，请稍后再试。");
                            } catch (Exception ignored) {}
                            emitter.completeWithError(error);
                        })
                        .subscribe(); // 启动订阅

            } catch (Exception e) {
                log.error("❌ 构建请求失败", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
    /**
     * 过滤 <think>...</think> 标签及其内容
     * 
     * 处理各种情况：
     * - "<think>\n思考内容" → 全部过滤
     * - "思考内容\n</think>\n\n正文" → 只保留正文
     * - "<think>思考</think>正文" → 只保留正文
     * - 普通文本（无标签）→ 根据 insideThink 状态决定保留或过滤
     */
    private String filterThinkContent(String content, boolean[] insideThink) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = content.length();

        while (i < len) {
            if (insideThink[0]) {
                // 在 <think> 块内，寻找 </think>
                int closeIdx = content.indexOf("</think>", i);
                if (closeIdx == -1) {
                    // 整个剩余内容都在 think 块内，全部跳过
                    break;
                } else {
                    // 找到 </think>，跳过标签，切换状态
                    insideThink[0] = false;
                    i = closeIdx + "</think>".length();
                }
            } else {
                // 不在 <think> 块内，寻找 <think>
                int openIdx = content.indexOf("<think>", i);
                if (openIdx == -1) {
                    // 没有 <think>，剩余内容全部保留
                    result.append(content, i, len);
                    break;
                } else {
                    // 保留 <think> 之前的内容
                    if (openIdx > i) {
                        result.append(content, i, openIdx);
                    }
                    // 进入 think 块
                    insideThink[0] = true;
                    i = openIdx + "<think>".length();
                }
            }
        }

        return result.toString();
    }
}
