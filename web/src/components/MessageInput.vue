<template>
  <div
    class="px-3 pt-3 pb-4 md:px-6 md:pt-4 md:pb-6 border-t border-border-default bg-bg-primary/60 backdrop-blur-xl"
  >
    <div
      class="max-w-[800px] mx-auto flex items-end gap-2.5 bg-bg-tertiary border border-border-default rounded-2xl px-4 pr-2 py-2 transition-all duration-250 focus-within:border-accent focus-within:shadow-[0_0_20px_var(--color-accent-glow)]"
    >
      <textarea
        id="chat-input"
        ref="inputRef"
        v-model="inputText"
        :disabled="isStreaming"
        placeholder="输入你的问题... 按 Enter 发送"
        rows="1"
        class="flex-1 bg-transparent border-none outline-none text-text-primary text-sm leading-[22px] resize-none h-[22px] max-h-[120px] placeholder:text-text-tertiary"
        @keydown="handleKeydown"
        @input="autoResize"
      ></textarea>

      <!-- 发送按钮 -->
      <button
        v-if="!isStreaming"
        id="send-btn"
        class="w-10 h-10 rounded-xl border-none bg-linear-to-br from-accent to-[#5ebbfc] text-text-on-accent text-lg cursor-pointer flex items-center justify-center shrink-0 transition-all duration-250 hover:not-disabled:scale-105 hover:not-disabled:shadow-[0_0_20px_var(--color-accent-glow)] active:not-disabled:scale-95 disabled:opacity-40 disabled:cursor-not-allowed"
        :disabled="!inputText.trim()"
        @click="send"
        title="发送消息"
      >
        ➤
      </button>

      <!-- 中止按钮 -->
      <button
        v-else
        id="stop-btn"
        class="w-10 h-10 rounded-xl border-none bg-[#ff4757] text-text-on-accent text-base cursor-pointer flex items-center justify-center shrink-0 transition-all duration-250 hover:scale-105 hover:shadow-[0_0_20px_rgba(255,71,87,0.4)] active:scale-95 animate-pulse"
        @click="$emit('stop')"
        title="停止生成"
      >
        ■
      </button>
    </div>
    <div
      class="text-center text-[11px] text-text-tertiary mt-2 max-w-[800px] mx-auto"
    >
      <span class="hidden sm:inline">AI 生成的内容仅供参考，请注意甄别</span>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from "vue";

const props = defineProps({
  disabled: {
    type: Boolean,
    default: false,
  },
  isStreaming: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(["send", "stop"]);

const inputText = ref("");
const inputRef = ref(null);

function send() {
  const text = inputText.value.trim();
  if (!text || props.isStreaming) return;
  emit("send", text);
  inputText.value = "";
  nextTick(() => {
    if (inputRef.value) {
      inputRef.value.style.height = "auto";
    }
  });
}

function handleKeydown(e) {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    send();
  }
}

function autoResize() {
  const el = inputRef.value;
  if (!el) return;
  el.style.height = "auto";
  el.style.height = Math.min(el.scrollHeight, 120) + "px";
}
</script>
