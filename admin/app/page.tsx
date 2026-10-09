'use client';

import React, { useEffect, useState, type FormEvent } from 'react';
import { createClient, type Session } from '@supabase/supabase-js';
import { ContentEditor, type ContentKind } from './content-editor';
import { apiRequest, ApiError } from './api';
import { ImagePicker, type MediaImage } from './image-picker';

type Tab = 'overview' | 'games' | 'levels' | 'questions' | 'media';
type Row = Record<string, unknown>;

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
const supabaseKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
const supabase = supabaseUrl && supabaseKey ? createClient(supabaseUrl, supabaseKey) : null;

export default function Home() {
  const [session, setSession] = useState<Session | null>(null);
  const [checkingSession, setCheckingSession] = useState(true);
  const [tab, setTab] = useState<Tab>('overview');
  const [overview, setOverview] = useState<Row | null>(null);
  const [rows, setRows] = useState<Row[]>([]);
  const [games, setGames] = useState<Row[]>([]);
  const [questionRows, setQuestionRows] = useState<Row[]>([]);
  const [selectedLevel, setSelectedLevel] = useState<Row | null>(null);
  const [selectedQuestionIds, setSelectedQuestionIds] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [editing, setEditing] = useState<{ kind: ContentKind; row: Row } | null>(null);
  const [image, setImage] = useState<MediaImage | null>(null);
  const [uploading, setUploading] = useState(false);
  const [sessionError, setSessionError] = useState('');
  const [accessDenied, setAccessDenied] = useState('');

  useEffect(() => {
    if (!supabase) {
      setCheckingSession(false);
      return;
    }
    void supabase.auth.getSession().then(({ data, error }) => {
      setSession(data.session);
      if (error) setSessionError(error.message);
      setCheckingSession(false);
    }).catch(() => { setSessionError('Could not restore your session. Please sign in again.'); setCheckingSession(false); });
    const { data } = supabase.auth.onAuthStateChange((_event, nextSession) => {
      setSession(nextSession);
      if (!nextSession) {
        setOverview(null); setRows([]); setGames([]); setQuestionRows([]);
        setEditing(null); setSelectedLevel(null); setImage(null);
      }
    });
    return () => data.subscription.unsubscribe();
  }, []);

  useEffect(() => {
    if (!session) return;
    let cancelled = false;
    setBusy(true);
    setAccessDenied('');
    setMessage('');
    setRows([]);
    const load = async () => {
      try {
        const result = await apiRequest<Row>('/admin/overview', session.access_token);
        const gameRows = await apiRequest<Row[]>('/admin/games', session.access_token);
        if (cancelled) return;
        setOverview(result);
        setGames(gameRows);
        if (tab === 'games') setRows(gameRows);
        if (tab === 'levels') {
          const [levelRows, availableQuestions] = await Promise.all([
            apiRequest<Row[]>('/admin/levels', session.access_token),
            apiRequest<Row[]>('/admin/questions', session.access_token),
          ]);
          if (cancelled) return;
          setRows(levelRows);
          setQuestionRows(availableQuestions);
        }
        if (tab === 'questions') {
          const questions = await apiRequest<Row[]>('/admin/questions', session.access_token);
          if (!cancelled) setRows(questions);
        }
      } catch (error) {
        if (!cancelled && error instanceof ApiError && [401, 403].includes(error.status)) setAccessDenied(error.message);
        if (!cancelled) setMessage(error instanceof Error ? error.message : 'Unable to load dashboard');
      } finally {
        if (!cancelled) setBusy(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [session, tab, reloadKey]);

  async function signIn() {
    if (!supabase) {
      setMessage('Set NEXT_PUBLIC_SUPABASE_URL and NEXT_PUBLIC_SUPABASE_ANON_KEY in admin/.env.');
      return;
    }
    const { error } = await supabase.auth.signInWithOAuth({
      provider: 'google',
      options: { redirectTo: window.location.origin },
    });
    if (error) setMessage(error.message);
  }

  async function signOut() {
    const result = await supabase?.auth.signOut();
    if (result?.error) { setMessage(result.error.message); return; }
    setOverview(null);
    setRows([]);
    setEditing(null);
    setSelectedLevel(null);
    setGames([]);
    setQuestionRows([]);
  }

  async function saveContentChanges(changes: Row) {
    if (!session || !editing || busy) return;
    setBusy(true);
    setMessage('');
    try {
      await apiRequest(`/admin/${editing.kind}/${String(editing.row.id)}`, session.access_token, {
        method: 'PATCH', body: JSON.stringify(changes),
      });
      setEditing(null);
      setMessage('Changes saved.');
      setReloadKey(key => key + 1);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Could not save changes');
    } finally {
      setBusy(false);
    }
  }

  async function submitForm(event: FormEvent<HTMLFormElement>, path: string, value: Row) {
    event.preventDefault();
    if (!session) return;
    const form = event.currentTarget;
    setBusy(true);
    setMessage('');
    try {
      await apiRequest(path, session.access_token, { method: 'POST', body: JSON.stringify(value) });
      form.reset();
      if (path === '/admin/questions') setImage(null);
      setMessage('Saved.');
      setReloadKey((key) => key + 1);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Could not save content');
    } finally {
      setBusy(false);
    }
  }

  async function togglePublished(kind: 'games' | 'levels', row: Row) {
    if (!session) return;
    setBusy(true);
    try {
      await apiRequest(`/admin/${kind}/${String(row.id)}`, session.access_token, {
        method: 'PATCH',
        body: JSON.stringify({ is_published: !row.is_published }),
      });
      setReloadKey((key) => key + 1);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Could not update publication');
    } finally {
      setBusy(false);
    }
  }

  async function saveLevelQuestions() {
    if (!session || !selectedLevel) return;
    setBusy(true);
    setMessage('');
    try {
      await apiRequest(`/admin/levels/${String(selectedLevel.id)}/questions`, session.access_token, {
        method: 'PUT',
        body: JSON.stringify({ question_ids: selectedQuestionIds }),
      });
      setMessage('Question order saved.');
      setReloadKey((key) => key + 1);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Could not assign questions');
    } finally {
      setBusy(false);
    }
  }

  if (checkingSession) return <main className="loading">Opening Yapnix Studio…</main>;

  if (!session) {
    return (
      <main className="login-shell">
        <div className="login-mark">Y</div>
        <p className="eyebrow">CONTENT WORKSPACE</p>
        <h1>Yapnix Studio</h1>
        <p className="muted">Sign in with an approved administrator account to manage games, levels, and questions.</p>
        <button className="primary-button" onClick={signIn}>Continue with Google</button>
        {message && <p className="notice">{message}</p>}
        {sessionError && <p className="notice" role="alert">{sessionError}</p>}
        <p className="small-note">Google sign-in alone does not grant administrator access.</p>
      </main>
    );
  }

  const tabs: Tab[] = ['overview', 'games', 'levels', 'questions', 'media'];
  if (accessDenied) return <main className="login-shell">
    <h1>Administrator access required</h1><p className="notice" role="alert">{accessDenied}</p>
    <p className="muted">{session.user.email} must be approved as an administrator before using Studio.</p>
    <button className="primary-button" onClick={() => void signOut()}>Sign out</button>
  </main>;

  return (
    <main className="studio-shell">
      <aside className="sidebar">
        <a className="brand" href="#overview"><span>Y</span> YAPNIX <small>STUDIO</small></a>
        <nav aria-label="Admin sections">
          {tabs.map((item) => (
            <button key={item} disabled={uploading} className={tab === item ? 'nav-item active' : 'nav-item'} onClick={() => {
              setTab(item); setRows([]); setEditing(null); setSelectedLevel(null);
            }}>
              <span className="nav-dot" />{item}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <span className="signed-in">{session.user.email}</span>
          <button className="quiet-button" onClick={signOut}>Sign out</button>
        </div>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div><p className="eyebrow">YAPNIX / CONTENT</p><h1>{tab === 'overview' ? 'Overview' : tab[0].toUpperCase() + tab.slice(1)}</h1></div>
          <button className="refresh-button" onClick={() => setReloadKey((key) => key + 1)}>Refresh</button>
        </header>

        {message && <p className="notice">{message}</p>}
        {busy && <div className="progress-line" />}
        {editing && <ContentEditor key={`${editing.kind}/${String(editing.row.id)}`} kind={editing.kind} row={editing.row}
          busy={busy} token={session.access_token} onSave={saveContentChanges} onCancel={() => setEditing(null)} />}
        {tab === 'media' && <section className="form-panel"><h2>Image library</h2>
          <ImagePicker token={session.access_token} value={image?.storage_path ?? ''} imageUrl={image?.image_url}
            disabled={busy} onChange={setImage} onBusyChange={setUploading} />
          <p className="small-note">Choose these images when creating or editing questions.</p>
        </section>}

        {tab === 'overview' && (
          <>
            <div className="metrics">
              {[
                ['Games', overview?.games],
                ['Levels', overview?.levels],
                ['Questions', overview?.questions],
              ].map(([label, value]) => <div className="metric" key={String(label)}><span>{String(label)}</span><strong>{String(value ?? '—')}</strong></div>)}
            </div>
            <section className="section-block">
              <div className="section-heading"><div><p className="eyebrow">CONTENT ACTIVITY</p><h2>Recently edited levels</h2></div><button className="text-action" onClick={() => setTab('levels')}>View levels</button></div>
              <div className="table-wrap"><table><thead><tr><th>Level</th><th>Status</th><th>Updated</th></tr></thead><tbody>
                {(overview?.recentlyEditedLevels as Row[] | undefined)?.map((row) => <tr key={String(row.id)}><td>{String(row.name)}</td><td><Status value={Boolean(row.is_published)} /></td><td>{new Date(String(row.updated_at)).toLocaleDateString()}</td></tr>)}
                {!overview && <tr><td colSpan={3}>Loading overview…</td></tr>}
              </tbody></table></div>
            </section>
          </>
        )}

        {tab === 'games' && (
          <div className="management-grid">
            <section className="form-panel"><p className="eyebrow">NEW CONTENT</p><h2>Create a game</h2>
              <form onSubmit={(event) => {
                const data = new FormData(event.currentTarget);
                void submitForm(event, '/admin/games', { name: data.get('name'), slug: data.get('slug'), description: data.get('description'), difficulty: Number(data.get('difficulty')), is_published: false });
              }}>
                <Field label="Name" name="name" required /><Field label="Slug" name="slug" required /><Field label="Description" name="description" />
                <label>Difficulty<select name="difficulty"><option value="1">1 · Easy</option><option value="2">2</option><option value="3">3 · Medium</option><option value="4">4</option><option value="5">5 · Hard</option></select></label>
                <button className="primary-button" disabled={busy}>Create game</button>
              </form>
            </section>
            <DataTable title="Games" headers={['Name', 'Slug', 'Status', '']} rows={rows} render={(row) => <><td>{String(row.name)}</td><td>{String(row.slug)}</td><td><Status value={Boolean(row.is_published)} /></td><td><button className="text-action" disabled={busy} onClick={() => setEditing({ kind: 'games', row })}>Edit</button><button className="text-action" disabled={busy} onClick={() => void togglePublished('games', row)}>{row.is_published ? 'Unpublish' : 'Publish'}</button></td></>} />
          </div>
        )}

        {tab === 'levels' && (
          <div className="management-grid">
            <section className="form-panel"><p className="eyebrow">NEW CONTENT</p><h2>Create a level</h2>
              <form onSubmit={(event) => {
                const data = new FormData(event.currentTarget);
                void submitForm(event, '/admin/levels', { game_id: data.get('game_id'), name: data.get('name'), description: data.get('description'), question_count: Number(data.get('question_count')), default_time_limit_seconds: Number(data.get('time_limit')), is_published: false });
              }}>
                <label>Game<select name="game_id" required defaultValue=""><option value="" disabled>Select a game</option>{games.map((game) => <option key={String(game.id)} value={String(game.id)}>{String(game.name)}</option>)}</select></label>
                <Field label="Name" name="name" required /><Field label="Description" name="description" />
                <div className="field-pair"><Field label="Questions" name="question_count" type="number" value="5" required /><Field label="Seconds / question" name="time_limit" type="number" value="5" required /></div>
                <button className="primary-button" disabled={busy}>Create level</button>
              </form>
            </section>
            <DataTable title="Levels" headers={['Name', 'Game', 'Questions', 'Status', '']} rows={rows} render={(row) => <><td>{String(row.name)}</td><td>{String((row.games as Row | null)?.name ?? '—')}</td><td>{String(row.question_count)}{((row.publication_issues as string[] | undefined) ?? []).map(issue => <p className="small-note" key={issue}>{issue}</p>)}</td><td><Status value={Boolean(row.is_published)} /></td><td><button className="text-action" disabled={busy} onClick={() => setEditing({ kind: 'levels', row })}>Edit</button><button className="text-action" disabled={busy} onClick={() => {
              setSelectedLevel(row);
              const assigned = [...((row.level_questions as Row[] | undefined) ?? [])].sort((a, b) => Number(a.order_index) - Number(b.order_index));
              setSelectedQuestionIds(assigned.map((item) => String(item.question_id)));
            }}>Configure</button><button className="text-action" disabled={busy || (!row.is_published && ((row.publication_issues as string[] | undefined) ?? []).length > 0)} onClick={() => void togglePublished('levels', row)}>{row.is_published ? 'Unpublish' : 'Publish'}</button></td></>} />
            {selectedLevel && <section className="form-panel question-order-panel">
              <div className="section-heading"><div><p className="eyebrow">LEVEL CONTENT</p><h2>{String(selectedLevel.name)}</h2></div><button className="quiet-button" onClick={() => setSelectedLevel(null)}>Close</button></div>
              <p className="muted">Select questions in the order they should appear. Re-selecting after removal places a question last.</p>
              <div className="question-check-list">{questionRows.map((question) => {
                const id = String(question.id);
                const order = selectedQuestionIds.indexOf(id);
                return <label className="question-check" key={id}><input type="checkbox" checked={order >= 0} onChange={(event) => setSelectedQuestionIds((current) => event.target.checked ? [...current, id] : current.filter((item) => item !== id))} /><span className="order-number">{order >= 0 ? String(order + 1).padStart(2, '0') : '—'}</span><span><strong>{String(question.primary_answer)}</strong><small>{String(question.image_storage_path)}</small></span></label>;
              })}</div>
              <button className="primary-button" disabled={busy} onClick={() => void saveLevelQuestions()}>Save question order</button>
            </section>}
          </div>
        )}

        {tab === 'questions' && (
          <div className="management-grid">
            <section className="form-panel"><p className="eyebrow">NEW CONTENT</p><h2>Create a question</h2>
              <form onSubmit={(event) => {
                const data = new FormData(event.currentTarget);
                const aliases = String(data.get('aliases') ?? '').split(',').map((answer) => answer.trim()).filter(Boolean);
                void submitForm(event, '/admin/questions', { image_storage_path: data.get('image_storage_path'), primary_answer: data.get('primary_answer'), accepted_answers: aliases, difficulty: Number(data.get('difficulty')), language: 'en', is_active: true });
              }}>
                <ImagePicker token={session.access_token} value={image?.storage_path ?? ''} imageUrl={image?.image_url}
                  disabled={busy} onChange={setImage} onBusyChange={setUploading} />
                <Field label="Primary answer" name="primary_answer" required />
                <Field label="Accepted aliases, comma-separated" name="aliases" placeholder="fan, electric fan" />
                <label>Difficulty<select name="difficulty"><option value="1">1 · Easy</option><option value="2">2</option><option value="3">3 · Medium</option><option value="4">4</option><option value="5">5 · Hard</option></select></label>
                <button className="primary-button" disabled={busy || uploading || !image}>Create question</button>
              </form>
              <p className="small-note">Images can be reused across questions. Draft images stay private.</p>
            </section>
            <DataTable title="Questions" headers={['Answer', 'Image', 'Aliases', '']} rows={rows} render={(row) => <><td>{String(row.primary_answer)}<small>{row.is_active ? ' · Active' : ' · Inactive'}</small></td><td>{row.image_url ? <img className="question-thumbnail" src={String(row.image_url)} alt={String(row.primary_answer)} /> : 'Image unavailable'}</td><td>{((row.question_answers as Row[] | undefined) ?? []).map((answer) => String(answer.answer)).join(', ')}</td><td><button className="text-action" disabled={busy} onClick={() => setEditing({ kind: 'questions', row })}>Edit</button></td></>} />
          </div>
        )}
      </section>
    </main>
  );
}

function Field({ label, name, required, placeholder, type = 'text', value }: { label: string; name: string; required?: boolean; placeholder?: string; type?: string; value?: string }) {
  return <label>{label}<input name={name} type={type} required={required} placeholder={placeholder} defaultValue={value} /></label>;
}

function Status({ value }: { value: boolean }) {
  return <span className={value ? 'status published' : 'status draft'}>{value ? 'Published' : 'Draft'}</span>;
}

function DataTable({ title, headers, rows, render }: { title: string; headers: string[]; rows: Row[]; render: (row: Row) => React.ReactNode }) {
  return <section className="section-block table-section"><div className="section-heading"><div><p className="eyebrow">LIBRARY</p><h2>{title}</h2></div><span className="row-count">{rows.length} items</span></div>
    <div className="table-wrap"><table><thead><tr>{headers.map((header) => <th key={header}>{header}</th>)}</tr></thead><tbody>
      {rows.map((row) => <tr key={String(row.id)}>{render(row)}</tr>)}
      {rows.length === 0 && <tr><td colSpan={headers.length}>No items yet.</td></tr>}
    </tbody></table></div>
  </section>;
}
