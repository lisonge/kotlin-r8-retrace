<script setup lang="ts">
import { useRetrace } from './useRetrace';

const {
  actionLabel,
  canRetrace,
  clearAll,
  clearMapping,
  copied,
  copyResult,
  error,
  hasMapping,
  isBusy,
  loadSample,
  loading,
  mappingDetails,
  mappingInput,
  mappingName,
  onMappingDrop,
  onMappingFileChange,
  regex,
  resetOutput,
  result,
  resultLines,
  runRetrace,
  stackLines,
  stackTrace,
  statusLabel,
  verbose,
} = useRetrace();
</script>

<template>
  <main
    class="mx-auto w-full max-w-[1480px] px-3 pb-8 pt-2 min-[641px]:px-6 min-[641px]:pt-3"
    @keydown.ctrl.enter.prevent="runRetrace"
  >
    <header
      class="flex min-h-14 items-center justify-between border-b border-[#c8c5bb]"
    >
      <a
        class="flex items-center gap-3 text-inherit no-underline"
        href="./"
        aria-label="R8 Retrace home"
      >
        <span
          class="grid h-9 w-9 place-items-center rounded-md bg-[#1f6656] font-mono text-xs font-medium text-[#f8f5ed]"
        >R8</span>
        <span class="grid gap-px">
          <strong class="text-base tracking-[-0.02em]">Retrace</strong>
          <small
            class="font-mono text-[11px] uppercase tracking-[0.04em] text-[#666b66]"
          >Kotlin / Wasm</small>
        </span>
      </a>

      <a
        class="font-mono text-[11px] uppercase tracking-[0.04em] text-[#666b66] no-underline transition-colors hover:text-[#1f6656]"
        href="https://github.com/lisonge/kotlin-r8-retrace"
        target="_blank"
        rel="noreferrer"
      >
        GitHub ↗
      </a>
    </header>

    <details
      class="my-2 rounded-md border border-[#c8c5bb] bg-[#e9e6dd]"
    >
      <summary class="cursor-pointer px-3 py-2.5 text-xs font-bold">
        Advanced options
      </summary>
      <div
        class="grid grid-cols-1 gap-3 px-3 pb-3 min-[901px]:grid-cols-[minmax(0,1fr)_260px]"
      >
        <label>
          <span class="mb-1 block text-[11px] font-bold">Stack trace regular expression</span>
          <input
            v-model="regex"
            class="h-9 w-full rounded border border-[#bbb8ae] bg-[#f8f5ed] px-2.5 font-mono text-xs text-[#27302c]"
            type="text"
            spellcheck="false"
          />
        </label>
        <label class="flex items-center gap-3">
          <input
            v-model="verbose"
            class="h-4 w-4 accent-[#1f6656]"
            type="checkbox"
          />
          <span>
            <strong class="block text-[11px] font-bold">Verbose mode</strong>
            <small class="text-[10px] text-[#686d68]">
              Show complete method signatures in the output
            </small>
          </span>
        </label>
      </div>
    </details>

    <section
      class="flex flex-col items-stretch justify-between gap-3 rounded-t-lg border border-[#c8c5bb] bg-[#e9e6dd] px-3 py-2 min-[641px]:flex-row min-[641px]:items-center"
      aria-label="Retrace controls"
    >
      <div class="flex items-center gap-[9px] text-[13px] font-semibold text-[#555d58]">
        <span
          :class="[
            'inline-block h-2 w-2 rounded-full',
            loading || isBusy
              ? 'animate-pulse bg-[#dc592e] shadow-[0_0_0_4px_rgba(220,89,46,0.12)]'
              : error
                ? 'bg-[#b43f26] shadow-[0_0_0_4px_rgba(180,63,38,0.12)]'
              : 'bg-[#1f806a] shadow-[0_0_0_4px_rgba(31,128,106,0.12)]',
          ]"
          aria-hidden="true"
        ></span>
        {{ statusLabel }}
      </div>
      <div class="grid grid-cols-2 items-center gap-2 min-[641px]:flex">
        <button
          class="min-h-9 cursor-pointer rounded-md border-0 bg-transparent px-3 text-[13px] font-bold text-inherit transition-[transform,background,color] duration-140 hover:-translate-y-px hover:bg-[#dcd8cd] disabled:cursor-not-allowed disabled:opacity-42"
          type="button"
          :disabled="isBusy"
          @click="loadSample"
        >
          Load sample
        </button>
        <button
          class="min-h-9 cursor-pointer rounded-md border-0 bg-transparent px-3 text-[13px] font-bold text-inherit transition-[transform,background,color] duration-140 hover:-translate-y-px hover:bg-[#dcd8cd] disabled:cursor-not-allowed disabled:opacity-42"
          type="button"
          :disabled="isBusy"
          @click="clearAll"
        >
          Clear
        </button>
        <button
          class="col-span-2 min-h-9 cursor-pointer rounded-md border-0 bg-[#1d5f51] px-3 text-[13px] font-bold text-[#fffaf1] transition-[transform,background,color] duration-140 hover:-translate-y-px hover:bg-[#164c41] disabled:cursor-not-allowed disabled:opacity-42 min-[641px]:col-auto"
          type="button"
          :disabled="!canRetrace"
          @click="runRetrace"
        >
          {{ actionLabel }}
          <kbd
            v-if="!isBusy"
            class="ml-2.5 rounded border border-white/24 px-1.5 py-[3px] font-mono text-[10px]"
          >Ctrl ↵</kbd>
        </button>
      </div>
    </section>

    <section
      class="grid grid-cols-1 overflow-hidden rounded-b-lg border-x border-b border-[#c8c5bb] bg-[#fbf9f3] min-[901px]:grid-cols-2"
      aria-label="Retrace workspace"
    >
      <article
        class="min-w-0 border-b border-[#c8c5bb] min-[901px]:border-b-0 min-[901px]:border-r"
      >
        <header
          class="flex min-h-12 items-center justify-between border-b border-[#d8d4ca] px-4"
        >
          <div class="flex items-center gap-3">
            <span class="font-mono text-[11px] text-[#c34d29]">01</span>
            <h2 class="m-0 text-sm font-bold">Mapping file</h2>
          </div>
          <span
            class="font-mono text-[11px] uppercase tracking-[0.04em] text-[#666b66]"
          >
            Local file
          </span>
        </header>
        <div
          class="min-h-[220px] p-4 min-[641px]:min-h-[280px]"
          @dragover.prevent
          @drop.prevent="onMappingDrop"
        >
          <input
            id="mapping-file"
            ref="mappingInput"
            class="sr-only"
            type="file"
            accept=".txt,text/plain"
            :disabled="isBusy"
            @change="onMappingFileChange"
          />

          <label
            v-if="!hasMapping"
            for="mapping-file"
            :class="[
              'flex min-h-[188px] cursor-pointer flex-col items-center justify-center rounded-lg border border-dashed border-[#aaa79d] bg-[#f5f2ea] px-5 text-center transition-colors hover:border-[#1f6656] hover:bg-[#f0eee6] min-[641px]:min-h-[248px]',
              isBusy ? 'pointer-events-none opacity-50' : '',
            ]"
          >
            <span
              class="mb-3 grid h-10 w-10 place-items-center rounded-md bg-[#1f6656] font-mono text-xs font-bold text-white"
            >TXT</span>
            <strong class="text-sm">Choose mapping.txt</strong>
            <span class="mt-1.5 text-xs leading-5 text-[#666b66]">
              Or drop it here. Large files are supported and their contents are not displayed.
            </span>
          </label>

          <div
            v-else
            class="flex min-h-[188px] flex-col justify-between rounded-lg border border-[#c8c5bb] bg-[#f5f2ea] p-4 min-[641px]:min-h-[248px]"
          >
            <div class="flex min-w-0 items-start gap-3">
              <span
                class="grid h-10 w-10 shrink-0 place-items-center rounded-md bg-[#1f6656] font-mono text-xs font-bold text-white"
              >TXT</span>
              <div class="min-w-0 pt-0.5">
                <strong class="block truncate text-sm" :title="mappingName">
                  {{ mappingName }}
                </strong>
                <span class="mt-1 block font-mono text-[11px] text-[#666b66]">
                  {{ mappingDetails }}
                </span>
              </div>
            </div>

            <p class="my-4 text-xs leading-5 text-[#666b66]">
              The mapping is read and indexed only when you retrace. File contents remain hidden.
            </p>

            <div class="flex items-center gap-2">
              <label
                for="mapping-file"
                :class="[
                  'cursor-pointer rounded-md border border-[#bbb8ae] bg-[#fbf9f3] px-3 py-2 text-xs font-bold transition-colors hover:bg-white',
                  isBusy ? 'pointer-events-none opacity-50' : '',
                ]"
              >Replace file</label>
              <button
                class="cursor-pointer rounded-md border-0 bg-transparent px-3 py-2 text-xs font-bold text-[#8b3c28] hover:bg-[#eadfd8] disabled:cursor-not-allowed disabled:opacity-50"
                type="button"
                :disabled="isBusy"
                @click="clearMapping"
              >Remove</button>
            </div>
          </div>
        </div>
      </article>

      <article class="min-w-0">
        <header
          class="flex min-h-12 items-center justify-between border-b border-[#d8d4ca] px-4"
        >
          <div class="flex items-center gap-3">
            <span class="font-mono text-[11px] text-[#c34d29]">02</span>
            <h2 class="m-0 text-sm font-bold">Obfuscated stack trace</h2>
          </div>
          <span
            class="font-mono text-[11px] uppercase tracking-[0.04em] text-[#666b66]"
          >
            {{ stackLines }} {{ stackLines === 1 ? 'line' : 'lines' }}
          </span>
        </header>
        <label class="sr-only" for="stack-trace">Stack trace to retrace</label>
        <textarea
          id="stack-trace"
          v-model="stackTrace"
          class="block min-h-[220px] w-full resize-y border-0 bg-transparent p-4 font-mono text-[13px] leading-[1.65] text-[#27302c] placeholder:text-[#aaa99f] min-[641px]:min-h-[280px]"
          spellcheck="false"
          placeholder="java.lang.IllegalStateException&#10;    at a.b(SourceFile:1)"
        ></textarea>
      </article>

      <article class="min-w-0 border-t border-[#c8c5bb] bg-[#19211e] min-[901px]:col-span-2">
        <header
          class="flex min-h-12 items-center justify-between border-b border-[#343d39] px-4 text-[#f4f0e6]"
        >
          <div class="flex items-center gap-3">
            <span class="font-mono text-[11px] text-[#c34d29]">03</span>
            <h2 class="m-0 text-sm font-bold">Retraced output</h2>
          </div>
          <div class="flex items-center gap-2">
            <span
              class="font-mono text-[11px] uppercase tracking-[0.04em] text-[#9da59f]"
            >
              {{ resultLines }} {{ resultLines === 1 ? 'line' : 'lines' }}
            </span>
            <button
              class="min-h-8 cursor-pointer rounded-lg border-0 bg-[#303b36] px-[11px] text-[13px] font-bold text-[#dce7e0] transition-[transform,background,color] duration-140 hover:-translate-y-px hover:bg-[#3b4842] disabled:cursor-not-allowed disabled:opacity-42"
              type="button"
              :disabled="!result"
              @click="copyResult"
            >
              {{ copied ? 'Copied' : 'Copy' }}
            </button>
            <button
              class="min-h-8 cursor-pointer rounded-lg border border-[#46514c] bg-transparent px-[11px] text-[13px] font-bold text-[#c2cbc5] transition-[transform,background,color] duration-140 hover:-translate-y-px hover:bg-[#303b36] hover:text-[#eef3ef] disabled:cursor-not-allowed disabled:opacity-42"
              type="button"
              :disabled="!result && !error"
              @click="resetOutput"
            >Clear</button>
          </div>
        </header>
        <pre
          v-if="result"
          class="m-0 max-h-[520px] min-h-[180px] overflow-auto whitespace-pre-wrap p-4 font-mono text-[13px] leading-[1.65] text-[#dfe9e2]"
          tabindex="0"
        >{{ result }}</pre>
        <div
          v-else
          class="grid min-h-[180px] place-content-center text-center text-[#7d8982]"
        >
          <span class="font-mono text-[28px]" aria-hidden="true">↳</span>
          <p class="mb-0 mt-2 text-[13px]">Your retraced output will appear here</p>
        </div>
        <p
          v-if="error"
          class="m-0 bg-[#7e2f1e] px-5 py-3 font-mono text-xs text-[#ffd9ca]"
          role="alert"
        >{{ error }}</p>
      </article>
    </section>

  </main>
</template>
