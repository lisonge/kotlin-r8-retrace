# retrace-wasm

R8 retrace compiled to Kotlin/Wasm. The package has no runtime dependencies.

```ts
import { retraceDefault } from 'retrace-wasm';

const retraced = retraceDefault(mappingText, stackTraceText);
```
