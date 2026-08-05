# kotlin-r8-retrace

[Website](https://r8-retrace.pages.dev/)

Kotlin Multiplatform implementation of the string-to-string part of R8 retrace, with desktop and browser clients.

## Modules

- `:retrace-core` is the Kotlin Multiplatform retrace library.
- `:retrace-desktop` is the Compose Multiplatform desktop client.
- `retrace-wasm` publishes the production Kotlin/Wasm module to npm.
- `retrace-website` is the Vite + Vue browser client.

The pnpm workspace contains only `retrace-wasm` and `retrace-website`.

## Build

```powershell
pnpm install
pnpm build
```

`pnpm build` compiles the production Wasm module, copies its Kotlin sources for source maps, and then builds the website.

Run the website locally:

```powershell
pnpm dev
```

Run the desktop client:

```powershell
.\gradlew.bat :retrace-desktop:run
```

Run all Kotlin checks:

```powershell
.\gradlew.bat check
```
