<template>
  <aside
    class="flex flex-col bg-bg-secondary border-r border-border-default relative z-30 transition-transform duration-300"
    :class="[
      // 桌面端：固定显示
      'md:w-[260px] md:min-w-[260px] md:translate-x-0 md:static md:h-screen',
      // 移动端：抽屉式，固定定位，通过 mobileOpen 控制显示
      'max-md:fixed max-md:left-0 max-md:top-0 max-md:h-full max-md:w-[280px]',
      mobileOpen ? 'max-md:translate-x-0' : 'max-md:-translate-x-full',
    ]"
  >
    <!-- Header -->
    <div class="px-4 pt-5 pb-3 flex items-center gap-3">
      <div
        class="w-9 h-9 rounded-xl bg-linear-to-br from-accent to-[#5ebbfc] flex items-center justify-center text-lg font-bold text-text-on-accent shrink-0 shadow-[0_0_20px_var(--color-accent-glow)]"
      >
        AI
      </div>
      <span
        class="text-base font-semibold text-text-primary tracking-tight flex-1"
        >AI Chat</span
      >
      <!-- 移动端关闭按钮 -->
      <button
        class="md:hidden w-7 h-7 rounded-lg flex items-center justify-center text-text-tertiary hover:text-text-primary hover:bg-bg-hover transition-colors"
        @click="$emit('close')"
      >
        ✕
      </button>
    </div>

    <!-- New Chat Button -->
    <button
      id="new-chat-btn"
      class="mx-4 mb-4 px-4 py-2.5 border border-dashed border-border-hover rounded-xl bg-transparent text-text-secondary text-sm cursor-pointer flex items-center gap-2 transition-all duration-250 ease-in-out hover:bg-glass-bg hover:border-accent hover:text-accent-light hover:shadow-[0_0_20px_var(--color-accent-glow)]"
      @click="$emit('new-chat')"
    >
      <span class="text-lg font-light">＋</span>
      新建对话
    </button>

    <!-- Session List -->
    <div class="flex-1 overflow-y-auto px-2">
      <div
        v-for="session in sessions"
        :key="session.id"
        :id="'session-' + session.id"
        class="group relative px-3 py-2.5 mb-0.5 rounded-lg cursor-pointer flex items-center gap-2.5 transition-all duration-150 ease-in-out"
        :class="
          session.id === activeSessionId
            ? 'bg-accent-glow'
            : 'hover:bg-bg-hover'
        "
        @click="$emit('select-session', session.id)"
      >
        <!-- Active indicator -->
        <span
          v-if="session.id === activeSessionId"
          class="absolute left-0 top-1/2 -translate-y-1/2 w-[3px] h-5 bg-accent rounded-full"
        ></span>

        <span class="text-base opacity-50 shrink-0">💬</span>
        <div class="flex-1 overflow-hidden">
          <div class="text-[13px] font-medium text-text-primary truncate">
            {{ session.title || "新对话" }}
          </div>
          <div class="text-[11px] text-text-tertiary mt-0.5">
            {{ formatTime(session.createTime) }}
          </div>
        </div>
        <button
          class="w-6 h-6 rounded-lg border-none bg-transparent text-text-tertiary cursor-pointer flex items-center justify-center text-sm opacity-0 group-hover:opacity-100 transition-all duration-150 hover:bg-[rgba(255,80,80,0.15)] hover:text-[#ff5050]"
          @click.stop="$emit('delete-session', session.id)"
          title="删除会话"
        >
          ✕
        </button>
      </div>

      <!-- Empty state -->
      <div
        v-if="sessions.length === 0"
        class="flex flex-col items-center justify-center py-10 px-4 text-center"
      >
        <div class="text-[32px] mb-2 opacity-30">💭</div>
        <div class="text-text-tertiary text-[13px]">暂无对话</div>
        <div class="text-text-tertiary text-xs mt-1">点击「新建对话」开始</div>
      </div>
    </div>
  </aside>
</template>

<script setup>
defineProps({
  sessions: {
    type: Array,
    default: () => [],
  },
  activeSessionId: {
    type: String,
    default: null,
  },
  mobileOpen: {
    type: Boolean,
    default: false,
  },
});

defineEmits(["new-chat", "select-session", "delete-session", "close"]);

function formatTime(timeStr) {
  if (!timeStr) return "";
  const date = new Date(timeStr);
  const now = new Date();
  const isToday = date.toDateString() === now.toDateString();
  if (isToday) {
    return date.toLocaleTimeString("zh-CN", {
      hour: "2-digit",
      minute: "2-digit",
    });
  }
  return date.toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit" });
}
</script>
