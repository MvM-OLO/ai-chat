<template>
  <div
    class="flex gap-2 md:gap-3 w-full mx-auto animate-fade-in-up"
    :class="role === 'user' ? 'flex-row-reverse' : ''"
  >
    <!-- Avatar -->
    <div
      class="w-8 h-8 rounded-lg flex items-center justify-center text-sm shrink-0 font-semibold mt-1"
      :class="
        role === 'user'
          ? 'bg-bg-elevated text-text-secondary'
          : 'bg-linear-to-br from-accent to-[#5ebbfc] text-text-on-accent shadow-[0_0_20px_var(--color-accent-glow)]'
      "
    >
      {{ role === "user" ? "🧑" : "🤖" }}
    </div>

    <!-- Bubble -->
    <div
      class="flex-1 min-w-0"
      :class="role === 'user' ? 'flex flex-col items-end' : ''"
    >
      <!-- User message: plain text -->
      <div
        v-if="role === 'user'"
        class="inline-block px-4 py-3 rounded-2xl rounded-tr-sm text-sm leading-7 max-w-[85%] md:max-w-[600px] wrap-break-word whitespace-pre-wrap bg-accent text-text-on-accent shadow-[0_0_20px_var(--color-accent-glow)]"
      >
        {{ content }}
      </div>

      <!-- AI message -->
      <div v-else class="w-full flex flex-col gap-2">
        <!-- 思考过程 (可折叠) -->
        <div
          v-if="
            parsed.thinking || (isStreaming && !parsed.answerStarted && content)
          "
          class="rounded-xl border border-border-default overflow-hidden"
        >
          <button
            class="w-full flex items-center gap-2 px-4 py-2.5 bg-bg-tertiary text-text-secondary text-[13px] cursor-pointer hover:bg-bg-hover transition-colors duration-150 text-left"
            @click="thinkingOpen = !thinkingOpen"
          >
            <!-- 思考中 spinner 或 完成图标 -->
            <span
              v-if="isStreaming && !parsed.answerStarted"
              class="w-3.5 h-3.5 border-2 border-accent border-t-transparent rounded-full animate-spin shrink-0"
            ></span>
            <span v-else class="text-accent shrink-0">✦</span>
            <span class="flex-1 font-medium">
              {{
                isStreaming && !parsed.answerStarted
                  ? "思考中..."
                  : "查看思考过程"
              }}
            </span>
            <span
              class="text-text-tertiary text-xs transition-transform duration-200"
              :class="thinkingOpen ? 'rotate-180' : ''"
              >▼</span
            >
          </button>

          <div
            v-show="thinkingOpen"
            class="px-4 py-3 text-[13px] text-text-secondary leading-6 bg-bg-secondary border-t border-border-default markdown-body thinking-content"
            v-html="renderedThinking"
          ></div>
        </div>

        <!-- 正文回答 -->
        <div
          v-if="parsed.answer"
          class="px-4 py-3 rounded-2xl rounded-tl-sm text-sm leading-7 bg-bg-tertiary border border-border-default text-text-primary markdown-body"
          v-html="renderedAnswer"
        ></div>

        <!-- 流式等待（还没有任何内容时）-->
        <div
          v-if="isStreaming && !content"
          class="px-4 py-3 rounded-2xl rounded-tl-sm bg-bg-tertiary border border-border-default"
        >
          <div class="flex gap-1 py-1">
            <span
              class="w-1.5 h-1.5 rounded-full bg-accent-light animate-typing-bounce"
            ></span>
            <span
              class="w-1.5 h-1.5 rounded-full bg-accent-light animate-typing-bounce [animation-delay:0.2s]"
            ></span>
            <span
              class="w-1.5 h-1.5 rounded-full bg-accent-light animate-typing-bounce [animation-delay:0.4s]"
            ></span>
          </div>
        </div>
      </div>

      <!-- 时间戳 -->
      <div
        v-if="createTime"
        class="text-[11px] text-text-tertiary mt-1.5 px-1"
        :class="role === 'user' ? 'text-right' : ''"
      >
        {{ formatMessageTime(createTime) }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from "vue";
import { Marked } from "marked";
import { markedHighlight } from "marked-highlight";
import hljs from "highlight.js";

// ── marked 实例 ──────────────────────────────────────────────
const marked = new Marked(
  markedHighlight({
    langPrefix: "hljs language-",
    highlight(code, lang) {
      if (lang && hljs.getLanguage(lang)) {
        try {
          return hljs.highlight(code, { language: lang }).value;
        } catch {}
      }
      return hljs.highlightAuto(code).value;
    },
  }),
);
marked.setOptions({ breaks: true, gfm: true });

// ── Props ────────────────────────────────────────────────────
const props = defineProps({
  role: {
    type: String,
    required: true,
    validator: (v) => ["user", "assistant"].includes(v),
  },
  content: { type: String, default: "" },
  createTime: { type: String, default: null },
  isStreaming: { type: Boolean, default: false },
});

// 思考过程默认展开（流式期间自动展开）
const thinkingOpen = ref(true);

// ── 解析 <think>...</think> ──────────────────────────────────
/**
 * 将 content 拆分为：
 *   thinking  — <think>...</think> 之间的内容（可能未闭合）
 *   answer    — </think> 之后的内容
 *   answerStarted — 是否已跨过 </think> 标签
 */
const parsed = computed(() => {
  const text = props.content || "";
  const openTag = "<think>";
  const closeTag = "</think>";

  const openIdx = text.indexOf(openTag);
  if (openIdx === -1) {
    // 没有 <think>，全是正文
    return { thinking: "", answer: text, answerStarted: true };
  }

  const thinkStart = openIdx + openTag.length;
  const closeIdx = text.indexOf(closeTag, thinkStart);

  if (closeIdx === -1) {
    // <think> 还未闭合（仍在流式传输思考过程）
    return {
      thinking: text.slice(thinkStart),
      answer: "",
      answerStarted: false,
    };
  }

  // <think> 已闭合
  return {
    thinking: text.slice(thinkStart, closeIdx),
    answer: text.slice(closeIdx + closeTag.length).trimStart(),
    answerStarted: true,
  };
});

const renderMd = (str) => {
  if (!str) return "";
  try {
    return marked.parse(str);
  } catch {
    return str;
  }
};

const renderedThinking = computed(() => renderMd(parsed.value.thinking));
const renderedAnswer = computed(() => renderMd(parsed.value.answer));

// ── 工具函数 ─────────────────────────────────────────────────
function formatMessageTime(timeStr) {
  if (!timeStr) return "";
  const d = new Date(timeStr);
  return d.toLocaleTimeString("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}
</script>

<style>
/* ============================
   Markdown 通用样式
   ============================ */
.markdown-body {
  line-height: 1.7;
  word-wrap: break-word;
}
.markdown-body p {
  margin: 0.5em 0;
}
.markdown-body p:first-child {
  margin-top: 0;
}
.markdown-body p:last-child {
  margin-bottom: 0;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4,
.markdown-body h5,
.markdown-body h6 {
  margin: 1em 0 0.5em;
  font-weight: 600;
  line-height: 1.4;
  color: var(--color-text-primary);
}
.markdown-body h1 {
  font-size: 1.5em;
}
.markdown-body h2 {
  font-size: 1.3em;
}
.markdown-body h3 {
  font-size: 1.15em;
}
.markdown-body h4 {
  font-size: 1.05em;
}

.markdown-body strong {
  font-weight: 600;
  color: var(--color-text-primary);
}
.markdown-body em {
  font-style: italic;
  color: var(--color-text-secondary);
}

.markdown-body code:not(pre code) {
  background: rgba(124, 92, 252, 0.15);
  color: var(--color-accent-light);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.88em;
  font-family: "JetBrains Mono", "Fira Code", monospace;
}

.markdown-body pre {
  margin: 0.75em 0;
  border-radius: 10px;
  overflow: hidden;
  background: #0d0d14 !important;
  border: 1px solid rgba(255, 255, 255, 0.06);
}
.markdown-body pre code {
  display: block;
  padding: 14px 16px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.6;
  font-family: "JetBrains Mono", "Fira Code", monospace;
  color: #e8e8f0;
  background: transparent !important;
}

.markdown-body ul,
.markdown-body ol {
  margin: 0.5em 0;
  padding-left: 1.5em;
}
.markdown-body ul {
  list-style-type: disc;
}
.markdown-body ol {
  list-style-type: decimal;
}
.markdown-body li {
  margin: 0.25em 0;
}

.markdown-body blockquote {
  margin: 0.75em 0;
  padding: 8px 14px;
  border-left: 3px solid var(--color-accent);
  background: rgba(124, 92, 252, 0.06);
  border-radius: 0 8px 8px 0;
  color: var(--color-text-secondary);
}

.markdown-body hr {
  margin: 1em 0;
  border: none;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.markdown-body a {
  color: var(--color-accent-light);
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: border-color 0.2s;
}
.markdown-body a:hover {
  border-bottom-color: var(--color-accent-light);
}

.markdown-body table {
  width: 100%;
  margin: 0.75em 0;
  border-collapse: collapse;
  font-size: 13px;
}
.markdown-body th,
.markdown-body td {
  padding: 8px 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  text-align: left;
}
.markdown-body th {
  background: rgba(124, 92, 252, 0.1);
  font-weight: 600;
}
.markdown-body tr:nth-child(even) td {
  background: rgba(255, 255, 255, 0.02);
}

/* 思考内容区域：字体稍小，颜色更淡 */
.thinking-content {
  font-size: 12.5px;
  opacity: 0.85;
}

/* ============================
   Highlight.js 暗色主题
   ============================ */
.hljs-keyword,
.hljs-selector-tag,
.hljs-built_in,
.hljs-name,
.hljs-tag {
  color: #c678dd;
}
.hljs-string,
.hljs-title,
.hljs-section,
.hljs-attribute,
.hljs-literal,
.hljs-template-tag,
.hljs-template-variable,
.hljs-type,
.hljs-addition {
  color: #98c379;
}
.hljs-deletion,
.hljs-selector-attr,
.hljs-selector-pseudo,
.hljs-meta {
  color: #e06c75;
}
.hljs-doctag {
  color: #7c5cfc;
}
.hljs-attr,
.hljs-symbol,
.hljs-bullet,
.hljs-link {
  color: #61aeee;
}
.hljs-number {
  color: #d19a66;
}
.hljs-comment,
.hljs-quote,
.hljs-formula {
  color: #5c6370;
  font-style: italic;
}
.hljs-params {
  color: #abb2bf;
}
.hljs-subst {
  color: #e8e8f0;
}
.hljs-regexp {
  color: #56b6c2;
}
</style>
