<template>
  <main class="flex-1 flex flex-col h-screen min-w-0 relative bg-bg-primary">
    <!-- Header -->
    <header
      class="h-[60px] min-h-[60px] flex items-center px-6 border-b border-border-default bg-bg-primary/80 backdrop-blur-xl z-5"
    >
      <h1 class="text-[15px] font-semibold text-text-primary">
        {{ sessionTitle }}
      </h1>
      <span
        v-if="sessionId"
        class="ml-2.5 text-[11px] px-2 py-0.5 rounded-full bg-accent-glow text-accent-light font-medium"
      >
        对话中
      </span>
    </header>

    <!-- Messages or Welcome -->
    <div
      class="flex-1 overflow-y-auto p-6 flex flex-col gap-5"
      ref="messagesContainer"
    >
      <!-- Welcome Screen -->
      <div
        v-if="!sessionId || (messages.length === 0 && !isStreaming)"
        class="flex-1 flex flex-col items-center justify-center gap-5 animate-fade-in-up"
      >
        <div
          class="w-[72px] h-[72px] rounded-2xl bg-gradient-to-br from-accent to-[#5ebbfc] flex items-center justify-center text-[32px] shadow-[0_0_20px_var(--color-accent-glow),0_8px_32px_rgba(0,0,0,0.5)] animate-float"
        >
          ✨
        </div>
        <h2 class="text-2xl font-bold text-text-primary tracking-tighter">
          你好，有什么可以帮你的？
        </h2>
        <p
          class="text-sm text-text-secondary text-center max-w-[400px] leading-7"
        >
          我是你的 AI
          智能助手，可以帮你回答问题、编写代码、分析数据等。开始一段对话吧！
        </p>
        <div class="flex gap-3 mt-3 flex-wrap justify-center">
          <div
            class="px-[18px] py-3 rounded-xl bg-glass-bg border border-glass-border text-text-secondary text-[13px] cursor-pointer max-w-[200px] text-center transition-all duration-250 hover:bg-accent-glow hover:border-accent hover:text-accent-light hover:-translate-y-0.5 hover:shadow-[0_0_20px_var(--color-accent-glow)]"
            @click="$emit('send-tip', '帮我解释一下什么是 Vue 3 的组合式 API')"
          >
            💡 解释 Vue 3 组合式 API
          </div>
          <div
            class="px-[18px] py-3 rounded-xl bg-glass-bg border border-glass-border text-text-secondary text-[13px] cursor-pointer max-w-[200px] text-center transition-all duration-250 hover:bg-accent-glow hover:border-accent hover:text-accent-light hover:-translate-y-0.5 hover:shadow-[0_0_20px_var(--color-accent-glow)]"
            @click="$emit('send-tip', '用 Java 写一个冒泡排序算法')"
          >
            🧑‍💻 写一个排序算法
          </div>
          <div
            class="px-[18px] py-3 rounded-xl bg-glass-bg border border-glass-border text-text-secondary text-[13px] cursor-pointer max-w-[200px] text-center transition-all duration-250 hover:bg-accent-glow hover:border-accent hover:text-accent-light hover:-translate-y-0.5 hover:shadow-[0_0_20px_var(--color-accent-glow)]"
            @click="$emit('send-tip', '推荐几本经典的编程书籍')"
          >
            📚 推荐编程书籍
          </div>
        </div>
      </div>

      <!-- Message List -->
      <template v-if="messages.length > 0 || isStreaming">
        <MessageBubble
          v-for="msg in messages"
          :key="msg.id"
          :role="msg.role"
          :content="msg.content"
          :createTime="msg.createTime"
        />
        <!-- Streaming message -->
        <MessageBubble
          v-if="isStreaming"
          role="assistant"
          :content="streamingContent"
          :isStreaming="true"
        />
      </template>
    </div>

    <!-- Input -->
    <MessageInput
      :isStreaming="isStreaming"
      @send="$emit('send-message', $event)"
      @stop="$emit('stop-streaming')"
    />
  </main>
</template>

<script setup>
import { ref, watch, nextTick, onMounted, onUnmounted } from "vue";
import MessageBubble from "./MessageBubble.vue";
import MessageInput from "./MessageInput.vue";

const props = defineProps({
  sessionId: {
    type: String,
    default: null,
  },
  sessionTitle: {
    type: String,
    default: "AI Chat",
  },
  messages: {
    type: Array,
    default: () => [],
  },
  isStreaming: {
    type: Boolean,
    default: false,
  },
  streamingContent: {
    type: String,
    default: "",
  },
});

defineEmits(["send-message", "send-tip", "stop-streaming"]);

const messagesContainer = ref(null);
// 用户是否已向上滚动（暂停自动滚动）
const userScrolled = ref(false);

function isNearBottom() {
  const el = messagesContainer.value;
  if (!el) return true;
  // 距底部 80px 以内视为「在底部」
  return el.scrollHeight - el.scrollTop - el.clientHeight < 80;
}

function handleScroll() {
  userScrolled.value = !isNearBottom();
}

function scrollToBottom(force = false) {
  nextTick(() => {
    if ((force || !userScrolled.value) && messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
  });
}

onMounted(() => {
  messagesContainer.value?.addEventListener("scroll", handleScroll, {
    passive: true,
  });
});
onUnmounted(() => {
  messagesContainer.value?.removeEventListener("scroll", handleScroll);
});

// 新消息（用户发送）时强制滚动到底
watch(
  () => props.messages.length,
  () => scrollToBottom(true),
);
// 流式内容更新时根据用户滚动状态决定
watch(
  () => props.streamingContent,
  () => scrollToBottom(false),
);
// 开始流式时强制滚到底
watch(
  () => props.isStreaming,
  (val) => {
    if (val) {
      userScrolled.value = false;
      scrollToBottom(true);
    }
  },
);
</script>
