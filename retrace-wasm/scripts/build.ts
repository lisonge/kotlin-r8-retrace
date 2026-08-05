import { spawn } from 'node:child_process';
import { cp, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

type SourceMap = {
  sources?: string[];
};

const scriptDir = dirname(fileURLToPath(import.meta.url));
const packageDir = dirname(scriptDir);
const repositoryDir = dirname(packageDir);
const gradleWrapper = join(
  repositoryDir,
  process.platform === 'win32' ? 'gradlew.bat' : 'gradlew',
);
const wasmOutputDir = join(
  repositoryDir,
  'retrace-core',
  'build',
  'compileSync',
  'wasmJs',
  'main',
  'productionExecutable',
  'kotlin',
);
const kotlinSourceDir = join(repositoryDir, 'retrace-core', 'src');
const distDir = join(packageDir, 'dist');
const packageSourceDir = join(packageDir, 'src');
const gradleTask = ':retrace-core:compileProductionExecutableKotlinWasmJs';

await new Promise<void>((resolve, reject) => {
  const command =
    process.platform === 'win32'
      ? (process.env.ComSpec ?? 'cmd.exe')
      : gradleWrapper;
  const args =
    process.platform === 'win32'
      ? ['/d', '/s', '/c', `${gradleWrapper} ${gradleTask}`]
      : [gradleTask];
  const gradle = spawn(
    command,
    args,
    {
      cwd: repositoryDir,
      stdio: 'inherit',
    },
  );
  gradle.on('error', reject);
  gradle.on('exit', (code) => {
    if (code === 0) resolve();
    else reject(new Error(`Gradle exited with code ${code}`));
  });
});

for (const targetDir of [distDir, packageSourceDir]) {
  if (dirname(targetDir) !== packageDir) {
    throw new Error(`Refusing to replace a directory outside ${packageDir}`);
  }
}

await Promise.all([
  rm(distDir, { recursive: true, force: true }),
  rm(packageSourceDir, { recursive: true, force: true }),
]);
await Promise.all([
  cp(wasmOutputDir, distDir, { recursive: true }),
  cp(kotlinSourceDir, packageSourceDir, { recursive: true }),
]);

const mapFileNames = (await readdir(distDir)).filter((name) =>
  name.endsWith('.map'),
);

for (const mapFileName of mapFileNames) {
  const mapFile = join(distDir, mapFileName);
  const sourceMap = JSON.parse(await readFile(mapFile, 'utf8')) as SourceMap;
  let kotlinSourceCount = 0;

  sourceMap.sources = sourceMap.sources?.map((source) => {
    const normalizedSource = source.replaceAll('\\', '/');
    const sourceMarker = '/src/';
    const sourceIndex = normalizedSource.lastIndexOf(sourceMarker);
    if (sourceIndex === -1) return normalizedSource;

    kotlinSourceCount += 1;
    return `../src/${normalizedSource.slice(sourceIndex + sourceMarker.length)}`;
  });

  if (kotlinSourceCount === 0) {
    throw new Error(`No Kotlin source paths found in ${mapFileName}`);
  }

  await writeFile(mapFile, `${JSON.stringify(sourceMap)}\n`, 'utf8');
}

console.log(`Copied Kotlin/Wasm output to ${distDir}`);
console.log(`Copied Kotlin sources to ${packageSourceDir}`);
console.log(`Rewrote ${mapFileNames.length} source map file(s)`);
