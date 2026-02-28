package org.example.server.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.server.domain.ChatMessage;
import org.example.server.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chart")
@RequiredArgsConstructor
public class ChartController {
    private final ChatService chatService;

    @PostMapping("/sessions")
    public String createSession(@RequestBody(required = false) Map<String, String> body) {
        String title = body != null ? body.get("title") : null;
        return chatService.createSession(title);
    }

    @GetMapping("/sessions/messages")
    public List<ChatMessage> getMessages(String sessionId) {
        return chatService.getHistoryMessages(sessionId);
    }

    // 3. 发送消息，接收 SSE 流 (POST /api/chat/messages/stream)
    // 注意：这里的 produces 必须指定为 text/event-stream，否则前端无法按流接收
    @PostMapping(value = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        return chatService.chatStream(request.getSessionId(), request.getContent());
    }

    // 定义一个内部类用来接收前端 POST 过来的 JSON 参数
    @Data
    public static class ChatRequest {
        private String sessionId;
        private String content;
    }
}
