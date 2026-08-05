import {
  computed,
  nextTick,
  onBeforeUnmount,
  shallowRef,
  watch,
} from 'vue';

type RetraceModule = typeof import('retrace-wasm');
type BusyStage = 'idle' | 'reading' | 'indexing' | 'retracing';
type RetraceModuleLoadResult = {
  module: RetraceModule | undefined;
  error: string;
};

const sampleMapping = `com.example.Foo -> a:
# {'id':'sourceFile','fileName':'Foo.kt'}
    1:1:void bar():42:42 -> b`;

const sampleStackTrace = `java.lang.IllegalStateException: Something went wrong
    at a.b(SourceFile:1)`;

const retraceModulePromise: Promise<RetraceModuleLoadResult> = import(
  'retrace-wasm'
).then(
  (module) => ({ module, error: '' }),
  (cause: unknown) => ({
    module: undefined,
    error:
      cause instanceof Error
        ? cause.message
        : 'This browser could not load the WebAssembly module.',
  }),
);

export function useRetrace() {
  const retraceModule = shallowRef<RetraceModule>();
  const mappingFile = shallowRef<File>();
  const mappingInput = shallowRef<HTMLInputElement>();
  const sampleMappingActive = shallowRef(false);
  const retracerId = shallowRef<number>();
  const stackTrace = shallowRef('');
  const regex = shallowRef('');
  const verbose = shallowRef(false);
  const result = shallowRef('');
  const error = shallowRef('');
  const moduleLoadError = shallowRef('');
  const copied = shallowRef(false);
  const loading = shallowRef(true);
  const busyStage = shallowRef<BusyStage>('idle');
  let active = true;

  const hasMapping = computed(
    () => mappingFile.value !== undefined || sampleMappingActive.value,
  );
  const isBusy = computed(() => busyStage.value !== 'idle');
  const canRetrace = computed(
    () =>
      !loading.value &&
      retraceModule.value !== undefined &&
      !isBusy.value &&
      hasMapping.value &&
      stackTrace.value.trim().length > 0,
  );
  const mappingName = computed(() =>
    mappingFile.value?.name ??
      (sampleMappingActive.value ? 'Built-in sample' : ''),
  );
  const mappingDetails = computed(() => {
    if (!hasMapping.value) return '';
    const source = mappingFile.value
      ? formatFileSize(mappingFile.value.size)
      : 'Example mapping';
    return `${source} · ${retracerId.value === undefined ? 'Ready' : 'Indexed'}`;
  });
  const statusLabel = computed(() => {
    if (loading.value) return 'Loading Wasm';
    if (busyStage.value === 'reading') return 'Reading mapping file';
    if (busyStage.value === 'indexing') return 'Indexing mapping';
    if (busyStage.value === 'retracing') return 'Retracing stack trace';
    if (error.value) return 'Check your input';
    return 'Retrace engine ready';
  });
  const actionLabel = computed(() => {
    if (busyStage.value === 'reading') return 'Reading file…';
    if (busyStage.value === 'indexing') return 'Indexing…';
    if (busyStage.value === 'retracing') return 'Retracing…';
    return 'Retrace';
  });
  const stackLines = computed(() => countLines(stackTrace.value));
  const resultLines = computed(() => countLines(result.value));

  void retraceModulePromise.then(({ module, error: loadError }) => {
    if (!active) return;
    retraceModule.value = module;
    moduleLoadError.value = loadError;
    error.value = loadError;
    regex.value = module?.defaultRegex() ?? '';
    loading.value = false;
  });

  watch([regex, verbose], disposeActiveRetracer);
  onBeforeUnmount(() => {
    active = false;
    disposeActiveRetracer();
  });

  function disposeActiveRetracer(): void {
    if (retracerId.value === undefined) return;
    retraceModule.value?.disposeRetracer(retracerId.value);
    retracerId.value = undefined;
  }

  function resetOutput(): void {
    result.value = '';
    error.value = moduleLoadError.value;
    copied.value = false;
  }

  function selectMappingFile(file: File): void {
    if (isBusy.value) return;
    disposeActiveRetracer();
    mappingFile.value = file;
    sampleMappingActive.value = false;
    resetOutput();
  }

  function onMappingFileChange(event: Event): void {
    const input = event.currentTarget as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file) selectMappingFile(file);
  }

  function onMappingDrop(event: DragEvent): void {
    const file = event.dataTransfer?.files[0];
    if (file) selectMappingFile(file);
  }

  function clearMapping(): void {
    if (isBusy.value) return;
    disposeActiveRetracer();
    mappingFile.value = undefined;
    sampleMappingActive.value = false;
    if (mappingInput.value) mappingInput.value.value = '';
    resetOutput();
  }

  async function runRetrace(): Promise<void> {
    const module = retraceModule.value;
    if (!canRetrace.value || !module) return;

    resetOutput();
    try {
      if (retracerId.value === undefined) {
        let mappingText: string;
        if (mappingFile.value) {
          busyStage.value = 'reading';
          mappingText = await mappingFile.value.text();
        } else {
          mappingText = sampleMapping;
        }

        busyStage.value = 'indexing';
        await renderBusyState();
        retracerId.value = module.createRetracer(
          mappingText,
          regex.value,
          verbose.value,
        );
      }

      busyStage.value = 'retracing';
      await renderBusyState();
      result.value = module.retraceWith(
        retracerId.value,
        stackTrace.value,
      );
    } catch (cause) {
      result.value = '';
      error.value = cause instanceof Error ? cause.message : String(cause);
    } finally {
      busyStage.value = 'idle';
    }
  }

  function loadSample(): void {
    if (isBusy.value) return;
    disposeActiveRetracer();
    mappingFile.value = undefined;
    sampleMappingActive.value = true;
    stackTrace.value = sampleStackTrace;
    resetOutput();
    void runRetrace();
  }

  function clearAll(): void {
    if (isBusy.value) return;
    clearMapping();
    stackTrace.value = '';
  }

  async function copyResult(): Promise<void> {
    if (!result.value) return;
    try {
      await navigator.clipboard.writeText(result.value);
      copied.value = true;
      window.setTimeout(() => (copied.value = false), 1600);
    } catch {
      error.value =
        'Clipboard access was denied. Please copy the result manually.';
    }
  }

  return {
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
  };
}

function countLines(value: string): number {
  return value.length === 0 ? 0 : value.split(/\r?\n/).length;
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1024;
  let unit = units[0];
  for (let index = 1; index < units.length && value >= 1024; index += 1) {
    value /= 1024;
    unit = units[index];
  }
  return `${value.toFixed(value >= 10 ? 1 : 2)} ${unit}`;
}

async function renderBusyState(): Promise<void> {
  await nextTick();
  await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
}
