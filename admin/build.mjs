import { createRequire } from 'node:module';
import { spawnSync } from 'node:child_process';

// Resolve from this workspace whether npm hoists Next.js or installs it locally.
const require = createRequire(import.meta.url);
const result = spawnSync(process.execPath, [
  '--max-old-space-size=1024', require.resolve('next/dist/bin/next'), 'build', '--webpack',
], { cwd: new URL('.', import.meta.url), stdio: 'inherit', env: process.env });
if (result.error) throw result.error;
process.exit(result.status ?? 1);
