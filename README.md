# kotlin-r8-retrace

Kotlin Multiplatform implementation of the string-to-string part of R8 retrace.

The public surface accepts a ProGuard/R8 mapping string, stack trace text, and a small config, then
returns retraced text. JVM tests use `com.android.tools:r8` only as an oracle; the common runtime
does not depend on R8.

## Modules

- `:retrace` is the Kotlin Multiplatform retrace library.
- `:sample-compose` is a Compose Multiplatform + Material 3 desktop sample.

Run the sample:

```powershell
.\gradlew.bat :sample-compose:run
```

Run all checks:

```powershell
.\gradlew.bat check
```

## Wasm JS

Build the browser ES module distribution:

```powershell
.\gradlew.bat :retrace:wasmJsBrowserDevelopmentExecutableDistribution
```

The generated module exports plain functions:

```javascript
import {
  createRetracer,
  defaultRegex,
  disposeRetracer,
  retraceWith,
} from "./retrace.js";

const retracerId = createRetracer(mappingText, defaultRegex(), false);
const output = retraceWith(retracerId, stackTraceText);
disposeRetracer(retracerId);
```
