<template>
  <div class="flex h-screen w-screen overflow-hidden bg-bg-primary">
    <Sidebar
      :sessions="sessions"
      :activeSessionId="activeSessionId"
      @new-chat="handleNewChat"
      @select-session="handleSelectSession"
      @delete-session="handleDeleteSession"
    />
    <ChatWindow
      :sessionId="activeSessionId"
      :sessionTitle="activeSessionTitle"
      :messages="currentMessages"
      :isStreaming="isStreaming"
      :streamingContent="streamingContent"
      @send-message="handleSendMessage"
      @send-tip="handleSendTip"
      @stop-streaming="handleStopStreaming"
    />
  </div>
</template>

<script setup>
import { ref, computed } from "vue";
import Sidebar from "./components/Sidebar.vue";
import ChatWindow from "./components/ChatWindow.vue";
import { createSession, getMessages, sendMessageStream } from "./api/chat.js";

// ========== 会话管理 ==========
const sessions = ref([]);
const activeSessionId = ref(null);
const messagesMap = ref({}); // sessionId -> messages[]
const isStreaming = ref(false);
const streamingContent = ref("");
let abortController = null; // 用于中止流式回复

const activeSessionTitle = computed(() => {
  const s = sessions.value.find((s) => s.id === activeSessionId.value);
  return s?.title || "AI Chat";
});

const currentMessages = computed(() => {
  if (!activeSessionId.value) return [];
  return messagesMap.value[activeSessionId.value] || [];
});

// 创建新会话
async function handleNewChat() {
  try {
    const sessionId = await createSession("新对话");
    const newSession = {
      id: sessionId,
      title: "新对话",
      createTime: new Date().toISOString(),
    };
    sessions.value.unshift(newSession);
    messagesMap.value[sessionId] = [];
    activeSessionId.value = sessionId;
  } catch (err) {
    console.error("创建会话失败:", err);
  }
}

// 选择会话
async function handleSelectSession(sessionId) {
  activeSessionId.value = sessionId;
  if (!messagesMap.value[sessionId]) {
    try {
      const messages = await getMessages(sessionId);
      messagesMap.value[sessionId] = messages;
    } catch (err) {
      console.error("获取消息失败:", err);
      messagesMap.value[sessionId] = [];
    }
  }
}

// 删除会话（仅前端删除）
function handleDeleteSession(sessionId) {
  sessions.value = sessions.value.filter((s) => s.id !== sessionId);
  delete messagesMap.value[sessionId];
  if (activeSessionId.value === sessionId) {
    activeSessionId.value = sessions.value[0]?.id || null;
  }
}

// 中止流式回复
function handleStopStreaming() {
  if (abortController) {
    abortController.abort();
    abortController = null;
  }
}

// 发送消息
async function handleSendMessage(content) {
  if (isStreaming.value) return;

  // 如果没有会话，自动创建一个
  if (!activeSessionId.value) {
    await handleNewChat();
  }

  const sessionId = activeSessionId.value;
  if (!sessionId) return; // 创建失败时的保护

  if (!messagesMap.value[sessionId]) {
    messagesMap.value[sessionId] = [];
  }
  messagesMap.value[sessionId].push({
    id: Date.now().toString(),
    role: "user",
    content: content,
    createTime: new Date().toISOString(),
  });

  // 更新会话标题（如果是第一条消息）
  if (messagesMap.value[sessionId].length === 1) {
    const session = sessions.value.find((s) => s.id === sessionId);
    if (session) {
      session.title = content.slice(0, 30) + (content.length > 30 ? "..." : "");
    }
  }

  isStreaming.value = true;
  streamingContent.value = "";
  abortController = new AbortController();

  await sendMessageStream(sessionId, content, {
    signal: abortController.signal,
    onChunk(chunk) {
      streamingContent.value += chunk;
    },
    onDone() {
      // 将已接收的内容保存为消息（即使是中止的）
      if (streamingContent.value) {
        messagesMap.value[sessionId].push({
          id: Date.now().toString() + "_ai",
          role: "assistant",
          content: streamingContent.value,
          createTime: new Date().toISOString(),
        });
      }
      streamingContent.value = "";
      isStreaming.value = false;
      abortController = null;
    },
    onError(err) {
      console.error("流式回复出错:", err);
      // 如果是用户主动中止，保存已接收的内容
      if (err.name === "AbortError" && streamingContent.value) {
        messagesMap.value[sessionId].push({
          id: Date.now().toString() + "_ai",
          role: "assistant",
          content: streamingContent.value + "\n\n_(已中止)_",
          createTime: new Date().toISOString(),
        });
      }
      streamingContent.value = "";
      isStreaming.value = false;
      abortController = null;
    },
  });
}

function handleSendTip(tip) {
  handleSendMessage(tip);
}
</script>
