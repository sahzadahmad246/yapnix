import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

if (!process.env.YAPNIX_TEST_DATABASE_URL) {
  console.error('Set YAPNIX_TEST_DATABASE_URL to an empty disposable PostgreSQL database (superuser connection).');
  process.exit(1);
}
const result = spawnSync('psql', [
  '--no-psqlrc', '--set', 'ON_ERROR_STOP=1',
  '--dbname', process.env.YAPNIX_TEST_DATABASE_URL,
  '--file', fileURLToPath(new URL('./migrations.sql', import.meta.url)),
], {
  stdio: 'inherit',
  env: process.env,
});
if (result.error) console.error('Database checks require the PostgreSQL psql client:', result.error.code);
process.exit(result.status ?? 1);
