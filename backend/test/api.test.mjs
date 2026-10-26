import 'reflect-metadata';
import assert from 'node:assert/strict';
import sharp from 'sharp';
import { after, before, beforeEach, test } from 'node:test';
import { Test } from '@nestjs/testing';
import { AdminModule } from '../dist/admin/admin.module.js';
import { GamesModule } from '../dist/games/games.module.js';
import { ProgressModule } from '../dist/progress/progress.module.js';
import { SupabaseService } from '../dist/supabase/supabase.service.js';
import { configureApp } from '../dist/configure-app.js';

// Exercise the real HTTP routes, guards, DTO pipes and services. Only the remote
// Supabase boundary is replaced; database RLS is covered separately by SQL checks.
const ids = {
  game: '11111111-1111-4111-8111-111111111111',
  draftGame: '22222222-2222-4222-8222-222222222222',
  level: '33333333-3333-4333-8333-333333333333',
  draftLevel: '44444444-4444-4444-8444-444444444444',
  hiddenLevel: '55555555-5555-4555-8555-555555555555',
  player: '66666666-6666-4666-8666-666666666666',
  question: '77777777-7777-4777-8777-777777777777',
};
const games = [
  { id: ids.game, name: 'Published game', is_published: true, prompt_key: 'guess_animal' },
  { id: ids.draftGame, name: 'Draft game', is_published: false },
];
const levels = [
  { id: ids.level, game_id: ids.game, name: 'Published level', is_published: true, level_questions: [] },
  { id: ids.draftLevel, game_id: ids.game, name: 'Draft level', is_published: false, level_questions: [] },
  { id: ids.hiddenLevel, game_id: ids.draftGame, name: 'Hidden parent', is_published: true, level_questions: [] },
];
let calls;
let saved;
let questionUpdate;
let signedPaths;
let uploadedPaths;
let legacyIconSchema = false;

function query(table, scope) {
  calls.push({ table, scope });
  const filters = [];
  let inserted;
  let selection = '';
  const result = () => {
    if (legacyIconSchema && table === 'games' && selection.includes('icon_key')) return { data: null, error: { code: '42703', message: 'column games.icon_key does not exist' }, count: 0 };
    let rows = table === 'games' ? games : table === 'levels' ? levels : [];
    if (table === 'admin_users') {
      const user = filters.find(([key]) => key === 'supabase_user_id')?.[1];
      rows = user === 'admin' ? [{ role: 'content_admin', approved: true }]
        : user === 'unapproved' ? [{ role: 'super_admin', approved: false }] : [];
    } else if (table === 'media_records') {
      rows = inserted ? [{ id: 'media-id', ...inserted }] : [];
    } else if (table === 'player_progress') {
      rows = inserted ? [inserted] : [{ level_id: ids.level, score: scope === 'player-token' ? 2 : 7 }];
    } else {
      rows = rows.filter(row => filters.every(([key, value]) => row[key] === value));
    }
    return { data: rows, error: null, count: rows.length };
  };
  const builder = {
    select(columns) { selection = columns; return builder; },
    delete() { calls.push({ table, scope, operation: 'delete', filters }); return builder; },
    insert(value) { inserted = value; return builder; },
    eq(key, value) { filters.push([key, value]); return builder; },
    order() { return builder; },
    limit() { return builder; },
    upsert(value, options) { saved = { value, options, scope }; inserted = value; return builder; },
    maybeSingle() { return Promise.resolve({ ...result(), data: result().data[0] ?? null }); },
    single() { return builder.maybeSingle(); },
    then(resolve, reject) { return Promise.resolve(result()).then(resolve, reject); },
  };
  return builder;
}

const supabase = {
  client: {
    from: table => query(table, 'public'),
    auth: {
      async getUser(token) {
        const user = { 'admin-token': 'admin', 'unapproved-token': 'unapproved', 'player-token': ids.player, 'other-token': 'other' }[token];
        return { data: { user: user ? { id: user } : null }, error: user ? null : new Error('Invalid token') };
      },
    },
  },
  adminClient: {
    storage: {
      getBucket: async () => ({ data: { public: false }, error: null }),
      from: () => ({
        upload: async path => { uploadedPaths.push(path); return { error: null }; },
        remove: async () => ({ error: null }),
        createSignedUrl: async path => ({ data: { signedUrl: `https://storage.test/${path}?token=private` }, error: null }),
        createSignedUrls: async paths => { signedPaths.push(...paths); return { data: paths.map(path => ({ path, signedUrl: `https://storage.test/${path}?token=private` })), error: null }; },
      }),
    },
    from: table => query(table, 'service-role'),
    async rpc(name, args) {
      // Match the JSON boundary used by the real Supabase HTTP client.
      questionUpdate = JSON.parse(JSON.stringify({ name, args }));
      if (name === 'create_standalone_game') return { data: { id: ids.game, ...args.content }, error: null };
      if (name === 'update_standalone_game') return args.target_game_id === ids.game ? { data: { id: ids.game, ...args.changes }, error: null } : { data: null, error: { code: 'P0002' } };
      if (name === 'set_game_questions') return { data: null, error: null };
      if (args.target_question_id !== ids.question) return { data: null, error: { code: 'P0002' } };
      return { data: { id: ids.question, ...args.changes }, error: null };
    },
  },
  createUserClient: token => ({
    from: table => query(table, token),
    async rpc(name, { entry }) {
      saved = { name, entry: JSON.parse(JSON.stringify(entry)), scope: token };
      return { data: { level_id: entry.levelId, score: entry.score }, error: null };
    },
  }),
};
let app;
let base;
before(async () => {
  const module = await Test.createTestingModule({ imports: [AdminModule, GamesModule, ProgressModule] })
    .overrideProvider(SupabaseService).useValue(supabase).compile();
  app = module.createNestApplication({ logger: false });
  configureApp(app);
  await app.listen(0, '127.0.0.1');
  base = await app.getUrl();
});
after(async () => { await app?.close(); });
beforeEach(() => { calls = []; saved = undefined; questionUpdate = undefined; signedPaths = []; uploadedPaths = []; legacyIconSchema = false; });

async function request(path, token, body, method = body ? 'POST' : 'GET') {
  const response = await fetch(`${base}/api${path}`, {
    method,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), 'Content-Type': 'application/json' },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  return { status: response.status, body: await response.json() };
}

test('guest gameplay lists published content without authentication or a privileged client', async () => {
  const response = await request('/games');
  assert.equal(response.status, 200);
  assert.deepEqual(response.body.map(row => row.id), [ids.game]);
  assert.ok(calls.every(call => call.scope === 'public'));
});

test('public level listing excludes drafts', async () => {
  const response = await request(`/games/${ids.game}/levels`);
  assert.equal(response.status, 200);
  assert.deepEqual(response.body.map(row => row.id), [ids.level]);
});

test('published child content is unavailable through a draft parent', async () => {
  assert.equal((await request(`/games/${ids.draftGame}/levels`)).status, 404);
  assert.equal((await request(`/games/${ids.draftGame}/levels/${ids.hiddenLevel}`)).status, 404);
});

test('draft levels and levels requested under the wrong game are unavailable', async () => {
  assert.equal((await request(`/games/${ids.game}/levels/${ids.draftLevel}`)).status, 404);
  assert.equal((await request(`/games/${ids.game}/levels/${ids.hiddenLevel}`)).status, 404);
});

test('only playable published questions receive signed image URLs', async () => {
  levels[0].level_questions = [{ order_index: 0, questions: {
    id: ids.question, image_storage_path: 'published.webp', primary_answer: 'bike',
    question_answers: [{ answer: 'bike' }],
  } }];
  try {
    const response = await request(`/games/${ids.game}/levels/${ids.level}`);
    assert.equal(response.status, 200);
    assert.match(response.body.questions[0].image_url, /token=private/);
    assert.deepEqual(signedPaths, ['published.webp']);
    await request(`/games/${ids.game}/levels/${ids.draftLevel}`);
    assert.deepEqual(signedPaths, ['published.webp']);
  } finally { levels[0].level_questions = []; }
});

test('HTTP path validation rejects malformed content IDs', async () => {
  assert.equal((await request('/games/not-a-uuid/levels')).status, 400);
  assert.equal(calls.length, 0);
});

test('admin routes reject absent and expired sessions before privileged queries', async () => {
  assert.equal((await request('/admin/games')).status, 401);
  assert.equal((await request('/admin/games', 'expired')).status, 401);
  assert.equal(calls.length, 0);
});

test('a valid Google/player session and an unapproved admin cannot manage content', async () => {
  assert.equal((await request('/admin/games', 'player-token')).status, 403);
  assert.equal((await request('/admin/games', 'unapproved-token')).status, 403);
  assert.ok(calls.every(call => call.table === 'admin_users'));
});

test('an approved administrator can view draft content', async () => {
  const response = await request('/admin/games', 'admin-token');
  assert.equal(response.status, 200);
  assert.ok(response.body.some(row => row.id === ids.draftGame));
});

test('admin mutations run guards and DTO validation before writing', async () => {
  assert.equal((await request('/admin/games', undefined, {})).status, 401);
  assert.equal((await request('/admin/games', 'player-token', {})).status, 403);
  assert.equal((await request('/admin/games', 'admin-token', { name: 'bad', difficulty: 99, extra: true })).status, 400);
  assert.ok(calls.every(call => call.table === 'admin_users'));
});

test('progress requires a validated session', async () => {
  assert.equal((await request('/progress')).status, 401);
  assert.equal((await request('/progress', 'expired')).status, 401);
  assert.equal(calls.length, 0);
});

test('progress reads use each player session rather than the service role', async () => {
  assert.equal((await request('/progress', 'player-token')).body[0].score, 2);
  assert.equal((await request('/progress', 'other-token')).body[0].score, 7);
  assert.deepEqual(calls.map(call => call.scope), ['player-token', 'other-token']);
});

const progress = { levelId: ids.level, questionIndex: 1, score: 1, correctCount: 1 };
test('progress writes take ownership from the server-verified identity', async () => {
  const sync = { ...progress, completed: true, contentFingerprint: ids.question, lastPlayedAt: '2026-10-09T12:00:00.000Z' };
  const response = await request('/progress', 'player-token', sync);
  assert.equal(response.status, 201);
  assert.equal(saved.name, 'save_player_progress');
  assert.equal(saved.entry.levelId, ids.level);
  assert.equal(saved.entry.supabase_user_id, undefined);
  assert.equal(saved.scope, 'player-token');
  assert.deepEqual(saved.entry, sync);
});

test('progress rejects forged ownership and invalid counters', async () => {
  assert.equal((await request('/progress', 'player-token', { ...progress, supabase_user_id: 'other' })).status, 400);
  assert.equal((await request('/progress', 'player-token', { ...progress, score: -1 })).status, 400);
  assert.equal((await request('/progress', 'player-token', { ...progress, contentFingerprint: null })).status, 400);
  assert.equal((await request('/progress', 'player-token', { ...progress, lastPlayedAt: 'yesterday' })).status, 400);
  assert.equal(saved, undefined);
});

test('approved question edits reach the atomic RPC and preserve omitted aliases', async () => {
  const changes = { primary_answer: 'bicycle', difficulty: 2 };
  const response = await request(`/admin/questions/${ids.question}`, 'admin-token', changes, 'PATCH');
  assert.equal(response.status, 200);
  assert.deepEqual(questionUpdate, { name: 'update_question_content', args: {
    target_question_id: ids.question, changes,
  } });
});

test('question edits reject unapproved users and invalid DTOs before calling the RPC', async () => {
  assert.equal((await request(`/admin/questions/${ids.question}`, 'player-token', { primary_answer: 'bicycle' }, 'PATCH')).status, 403);
  assert.equal((await request(`/admin/questions/${ids.question}`, 'admin-token', { difficulty: 99 }, 'PATCH')).status, 400);
  assert.equal(questionUpdate, undefined);
});

test('question edits return 404 for a missing question', async () => {
  assert.equal((await request(`/admin/questions/${ids.game}`, 'admin-token', { primary_answer: 'bicycle' }, 'PATCH')).status, 404);
});

async function uploadImage(token, bytes, type = 'image/png') {
  const body = new FormData();
  body.set('file', new Blob([bytes], { type }), 'test.png');
  return fetch(`${base}/api/admin/media`, { method: 'POST', headers: token ? { Authorization: `Bearer ${token}` } : {}, body });
}

test('image uploads require an approved administrator before accessing storage', async () => {
  assert.equal((await uploadImage(undefined, 'not-image')).status, 401);
  assert.equal((await uploadImage('player-token', 'not-image')).status, 403);
  assert.deepEqual(uploadedPaths, []);
});

test('image uploads reject corrupt files and enforce the multipart size limit', async () => {
  assert.equal((await uploadImage('admin-token', 'not-image')).status, 400);
  assert.equal((await uploadImage('admin-token', Buffer.alloc(5 * 1024 * 1024 + 1))).status, 413);
  assert.deepEqual(uploadedPaths, []);
});

test('approved image uploads return a private preview and a generated Storage path', async () => {
  const png = await sharp({ create: { width: 8, height: 8, channels: 3, background: 'red' } }).png().toBuffer();
  const response = await uploadImage('admin-token', png);
  assert.equal(response.status, 201);
  const body = await response.json();
  assert.match(body.storage_path, /^questions\/[0-9a-f-]+\.webp$/);
  assert.match(body.image_url, /token=private/);
  assert.equal(body.mime_type, 'image/webp');
});


test('game deletion requires a server-approved admin before accessing content', async () => {
  assert.equal((await request(`/admin/games/${ids.game}`, undefined, undefined, 'DELETE')).status, 401);
  assert.equal((await request(`/admin/games/${ids.game}`, 'player-token', undefined, 'DELETE')).status, 403);
  assert.equal((await request(`/admin/games/${ids.game}`, 'unapproved-token', undefined, 'DELETE')).status, 403);
  assert.equal(calls.some(call => call.operation === 'delete'), false);
});

test('approved game deletion targets only the requested game and rejects missing IDs', async () => {
  assert.equal((await request('/admin/games/not-a-uuid', 'admin-token', undefined, 'DELETE')).status, 400);
  assert.equal(calls.some(call => call.operation === 'delete'), false);
  const response = await request(`/admin/games/${ids.game}`, 'admin-token', undefined, 'DELETE');
  assert.equal(response.status, 200);
  assert.deepEqual(response.body, { id: ids.game });
  assert.deepEqual(calls.find(call => call.operation === 'delete'), {
    table: 'games', scope: 'service-role', operation: 'delete', filters: [['id', ids.game]],
  });
  assert.equal((await request(`/admin/games/${ids.question}`, 'admin-token', undefined, 'DELETE')).status, 404);
});


test('standalone games are created with their question configuration in one RPC', async () => {
  const content = { name: 'Animals', slug: 'animals', category_id: ids.game, difficulty: 1, question_count: 5, default_time_limit_seconds: 5 };
  const response = await request('/admin/games', 'admin-token', content);
  assert.equal(response.status, 201);
  assert.deepEqual(questionUpdate, { name: 'create_standalone_game', args: { content } });
});

test('game assignment and publication are guarded and validate identifiers', async () => {
  assert.equal((await request(`/admin/games/${ids.game}/questions`, 'player-token', { question_ids: [] }, 'PUT')).status, 403);
  assert.equal((await request(`/admin/games/${ids.game}/questions`, 'admin-token', { question_ids: ['invalid'] }, 'PUT')).status, 400);
  assert.equal(questionUpdate, undefined);
  assert.equal((await request(`/admin/games/${ids.game}/questions`, 'admin-token', { question_ids: [ids.question] }, 'PUT')).status, 200);
  assert.deepEqual(questionUpdate, { name: 'set_game_questions', args: { target_game_id: ids.game, question_ids: [ids.question] } });
  assert.equal((await request(`/admin/games/${ids.game}`, 'admin-token', { is_published: true }, 'PATCH')).status, 200);
  assert.deepEqual(questionUpdate, { name: 'update_standalone_game', args: { target_game_id: ids.game, changes: { is_published: true } } });
});

test('category management requires a server-approved administrator', async () => {
  assert.equal((await request('/admin/categories', undefined)).status, 401);
  assert.equal((await request('/admin/categories', 'player-token')).status, 403);
  assert.equal((await request('/admin/categories', 'admin-token')).status, 200);
  assert.equal((await request('/admin/categories', 'admin-token', { name:'Unsupported', slug:'unsupported', gameplay_kind:'made_up' })).status, 400);
});

test('direct game play rejects drafts and malformed IDs without a level choice', async () => {
  assert.equal((await request(`/games/${ids.draftGame}/play`)).status, 404);
  assert.equal((await request('/games/invalid/play')).status, 400);
});


test('a published game plays directly with ordered questions and the game name', async () => {
  levels[0].level_questions = [{order_index:0,questions:{id:ids.question,image_storage_path:'cat.webp',primary_answer:'cat',difficulty:1,language:'en-US',question_answers:[{answer:'cat'},{answer:'kitty'}]}}];
  try {
    const response = await request(`/games/${ids.game}/play`);
    assert.equal(response.status, 200);
    assert.equal(response.body.name, 'Published game');
    assert.equal(response.body.prompt, 'Guess the animal');
    assert.equal(response.body.questions[0].primary_answer, 'cat');
    assert.deepEqual(response.body.questions[0].accepted_answers, ['cat','kitty']);
  } finally { levels[0].level_questions = []; }
});


test('game prompts are approved-admin only and unknown prompt keys are rejected', async () => {
  assert.equal((await request('/admin/game-prompts')).status, 401);
  const response=await request('/admin/game-prompts','admin-token');
  assert.equal(response.status,200);
  assert.ok(response.body.some(prompt=>prompt.key==='guess_animal'&&prompt.label==='Guess the animal'));
  assert.equal((await request(`/admin/games/${ids.game}`,'admin-token',{prompt_key:'not_supported'},'PATCH')).status,400);
  assert.equal((await request(`/admin/games/${ids.game}`,'admin-token',{prompt_key:'guess_animal'},'PATCH')).status,200);
});


test('catalogue artwork is signed only after publication filtering', async () => {
  games[0].icon_key = 'paw-print';
  games[0].icon_storage_path = 'artwork/published.webp';
  games[0].category_id = 'guess';
  games[0].game_categories = { id: 'guess', name: 'Guess', description: 'Say what you see.', icon_key: 'question', icon_storage_path: 'artwork/guess.webp' };
  games[1].icon_storage_path = 'artwork/draft.webp';
  try {
    const response = await request('/games');
    assert.equal(response.status, 200);
    assert.equal(response.body[0].icon_key, 'paw-print');
    assert.equal(response.body[0].category.icon_key, 'question');
    assert.match(response.body[0].icon_url, /artwork\/published.webp/);
    assert.match(response.body[0].category.icon_url, /artwork\/guess.webp/);
    assert.equal(response.body[0].category.description, 'Say what you see.');
    assert.deepEqual(signedPaths.sort(), ['artwork/guess.webp', 'artwork/published.webp']);
  } finally {
    delete games[0].icon_key; delete games[0].icon_storage_path; delete games[0].category_id; delete games[0].game_categories; delete games[1].icon_storage_path;
  }
});

test('square artwork upload enforces admin approval and returns a 512px square', async () => {
  const send = async token => {
    const body = new FormData();
    body.set('file', new Blob([await sharp({ create: { width: 80, height: 40, channels: 3, background: 'red' } }).png().toBuffer()], { type: 'image/png' }), 'icon.png');
    return fetch(`${base}/api/admin/media/artwork`, { method: 'POST', headers: token ? { Authorization: `Bearer ${token}` } : {}, body });
  };
  assert.equal((await send()).status, 401);
  assert.equal((await send('player-token')).status, 403);
  assert.deepEqual(uploadedPaths, []);
  const response = await send('admin-token');
  assert.equal(response.status, 201);
  const body = await response.json();
  assert.equal(body.width, 512); assert.equal(body.height, 512);
  assert.match(body.storage_path, /^artwork\/[0-9a-f-]+\.webp$/);
});


test('approved admins save library icons without uploading media and unknown keys are rejected', async () => {
  const content = { name: 'Animals', slug: 'animals', difficulty: 1, icon_key: 'paw-print' };
  assert.equal((await request('/admin/games', 'admin-token', content)).status, 201);
  assert.deepEqual(questionUpdate, { name: 'create_standalone_game', args: { content } });
  assert.equal((await request(`/admin/games/${ids.game}`, 'player-token', { icon_key: 'cat' }, 'PATCH')).status, 403);
  assert.equal((await request(`/admin/games/${ids.game}`, 'admin-token', { icon_key: 'not-bundled' }, 'PATCH')).status, 400);
  assert.equal((await request(`/admin/games/${ids.game}`, 'admin-token', { icon_key: 'cat', icon_storage_path: null }, 'PATCH')).status, 200);
  assert.deepEqual(questionUpdate.args.changes, { icon_key: 'cat', icon_storage_path: null });
  assert.deepEqual(uploadedPaths, []);
});


test('guest catalogue remains publication-aware before the icon SQL upgrade', async () => {
  legacyIconSchema = true;
  const response = await request('/games');
  assert.equal(response.status, 200);
  assert.deepEqual(response.body.map(game => game.id), [ids.game]);
  assert.equal(response.body[0].icon_key, 'game-controller');
  assert.ok(calls.every(call => call.scope === 'public'));
  assert.deepEqual(signedPaths, []);
});


test('library icon saves require the SQL upgrade before mutating legacy content', async () => {
  legacyIconSchema = true;
  const response = await request('/admin/games', 'admin-token', { name: 'Animals', slug: 'animals', difficulty: 1, icon_key: 'paw-print' });
  assert.equal(response.status, 503);
  assert.match(response.body.message, /upgrade_icon_library.sql/);
  assert.equal(questionUpdate, undefined);
});
