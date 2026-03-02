<template>
  <div class="flex h-screen w-screen overflow-hidden bg-bg-primary">
    <!-- 移动端遮罩层 -->
    <div
      v-if="sidebarOpen"
      class="fixed inset-0 bg-black/50 z-20 md:hidden"
      @click="sidebarOpen = false"
    />

    <Sidebar
      :sessions="sessions"
      :activeSessionId="activeSessionId"
      :mobileOpen="sidebarOpen"
      @new-chat="handleNewChat"
      @select-session="handleSelectSession"
      @delete-session="handleDeleteSession"
      @close="sidebarOpen = false"
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
      @toggle-sidebar="sidebarOpen = !sidebarOpen"
    />
  </div>
</template>

<script setup>
import { ref, computed } from "vue";
import Sidebar from "./components/Sidebar.vue";
import ChatWindow from "./components/ChatWindow.vue";
import { createSession, getMessages, sendMessageStream } from "./api/chat.js";

// ========== 侧边栏状态（移动端） ==========
const sidebarOpen = ref(false);

// ========== 会话管理 ==========
const sessions = ref([]);
const activeSessionId = ref(null);
const messagesMap = ref({});
const isStreaming = ref(false);
const streamingContent = ref("");
let abortController = null;

const activeSessionTitle = computed(() => {
  const s = sessions.value.find((s) => s.id === activeSessionId.value);
  return s?.title || "AI Chat";
});

const currentMessages = computed(() => {
  if (!activeSessionId.value) return [];
  return messagesMap.value[activeSessionId.value] || [];
});

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
    sidebarOpen.value = false; // 移动端新建后关闭侧边栏
  } catch (err) {
    console.error("创建会话失败:", err);
  }
}

async function handleSelectSession(sessionId) {
  activeSessionId.value = sessionId;
  sidebarOpen.value = false; // 移动端选择后关闭侧边栏
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

function handleDeleteSession(sessionId) {
  sessions.value = sessions.value.filter((s) => s.id !== sessionId);
  delete messagesMap.value[sessionId];
  if (activeSessionId.value === sessionId) {
    activeSessionId.value = sessions.value[0]?.id || null;
  }
}

function handleStopStreaming() {
  if (abortController) {
    abortController.abort();
    abortController = null;
  }
}

async function handleSendMessage(content) {
  if (isStreaming.value) return;

  if (!activeSessionId.value) {
    await handleNewChat();
  }

  const sessionId = activeSessionId.value;
  if (!sessionId) return;

  if (!messagesMap.value[sessionId]) {
    messagesMap.value[sessionId] = [];
  }
  messagesMap.value[sessionId].push({
    id: Date.now().toString(),
    role: "user",
    content: content,
    createTime: new Date().toISOString(),
  });

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
