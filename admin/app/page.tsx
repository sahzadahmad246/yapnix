'use client';

import React, { useEffect, useState, type ReactNode } from 'react';
import { createClient, type Session } from '@supabase/supabase-js';
import { ContentEditor, type ContentKind } from './content-editor';
import { apiRequest, ApiError } from './api';
import { ImagePicker, type MediaImage } from './image-picker';
import { ContentDialog } from './content-dialog';
import { CreateContent } from './create-content';

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
  const [creating, setCreating] = useState<ContentKind | null>(null);
  const [gameFilter, setGameFilter] = useState('');
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
        setEditing(null); setSelectedLevel(null); setImage(null); setCreating(null); setUploading(false); setGameFilter('');
      }
    });
    return () => data.subscription.unsubscribe();
  }, []);

  useEffect(() => {
    if (!session) return;
    let cancelled = false;
    setBusy(true);
    setAccessDenied('');
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
    setCreating(null);
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

  async function createContent(path: string, value: Row) {
    if (!session || busy) return;
    setBusy(true); setMessage('');
    try {
      const created = await apiRequest<Row>(path, session.access_token, { method: 'POST', body: JSON.stringify(value) });
      if (path === '/admin/questions' && selectedLevel) {
        if (created.id) setSelectedQuestionIds(current => [...current, String(created.id)]);
        setMessage('Image and answer saved. Click Save level images to link your selection to this level.');
      } else setMessage('Saved.');
      setCreating(null);
      setReloadKey(key => key + 1);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Could not save content');
    } finally { setBusy(false); }
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
      setSelectedLevel(null);
      setMessage('Level images and answer order saved.');
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
            <button key={item} disabled={uploading || busy} className={tab === item ? 'nav-item active' : 'nav-item'} onClick={() => {
              setTab(item); setRows([]); setEditing(null); setSelectedLevel(null); setCreating(null); setMessage(''); setGameFilter('');
            }}>
              <span className="nav-dot" />{item === 'questions' ? 'Images & answers' : item}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <span className="signed-in">{session.user.email}</span>
          <button className="quiet-button" disabled={busy || uploading} onClick={signOut}>Sign out</button>
        </div>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div><p className="eyebrow">YAPNIX / CONTENT</p><h1>{tab === 'questions' ? 'Images & answers' : tab[0].toUpperCase() + tab.slice(1)}</h1></div>
          <div className="form-actions">
            {(['games', 'levels', 'questions'] as Tab[]).includes(tab) && <button className="primary-button" disabled={busy || uploading}
              onClick={() => { setMessage(''); setEditing(null); setCreating(tab as ContentKind); }}>
              {tab === 'games' ? 'New game' : tab === 'levels' ? 'New level' : 'Add image & answer'}</button>}
            <button className="refresh-button" disabled={busy || uploading} onClick={() => setReloadKey(key => key + 1)}>Refresh</button>
          </div>
        </header>
        {message && !creating && !editing && !selectedLevel && <p className="notice" role="status">{message}</p>}
        {busy && <div className="progress-line" />}
        {creating && <ContentDialog title={creating === 'questions' ? 'Add image & answer' : creating === 'games' ? 'Create a game' : 'Create a level'}
          busy={busy || uploading} onClose={() => { setCreating(null); setMessage(''); }}>
          {message && <p className="notice" role="alert">{message}</p>}
          <CreateContent kind={creating} token={session.access_token} busy={busy} games={games} gameId={gameFilter}
            levelName={creating === 'questions' && selectedLevel ? String(selectedLevel.name) : undefined}
            onBusyChange={setUploading} onSave={createContent} onCancel={() => { setCreating(null); setMessage(''); }} />
        </ContentDialog>}
        {editing && <ContentDialog title={editing.kind === 'questions' ? 'Edit image & answer' : 'Edit content'}
          busy={busy || uploading} onClose={() => { setEditing(null); setMessage(''); }}>
          {message && <p className="notice" role="alert">{message}</p>}
          <ContentEditor key={`${editing.kind}/${String(editing.row.id)}`} kind={editing.kind} row={editing.row}
            busy={busy} token={session.access_token} onBusyChange={setUploading} onSave={saveContentChanges} onCancel={() => { setEditing(null); setMessage(''); }} />
        </ContentDialog>}
        {tab === 'media' && <section className="form-panel"><h2>Uploaded files</h2>
          <p className="muted">This library stores image files. Set the correct answer in Images & answers, then choose the image for a game's level.</p>
          <ImagePicker token={session.access_token} value={image?.storage_path ?? ''} imageUrl={image?.image_url}
            disabled={busy} onChange={setImage} onBusyChange={setUploading} />
        </section>}
        {tab === 'overview' && <>
          <div className="metrics">{[['Games', overview?.games], ['Levels', overview?.levels], ['Image questions', overview?.questions]].map(([label, value]) =>
            <div className="metric" key={String(label)}><span>{String(label)}</span><strong>{String(value ?? '—')}</strong></div>)}</div>
          <section className="form-panel"><h2>Build a game</h2><p className="muted">Game → Level → Images & answers → Publish</p>
            <p>Create a game, open its levels, then add pictures with their correct names and accepted alternatives. Forms open only when you need them.</p>
            <button className="primary-button" disabled={busy} onClick={() => { setTab('games'); setGameFilter(''); }}>Open games</button></section>
          <DataTable title="Recently edited levels" headers={['Level', 'Status', 'Updated']}
            rows={(overview?.recentlyEditedLevels as Row[] | undefined) ?? []} render={row => <>
              <td>{String(row.name)}</td><td><Status value={Boolean(row.is_published)} /></td><td>{new Date(String(row.updated_at)).toLocaleDateString()}</td>
            </>} />
        </>}
        {tab === 'games' && <DataTable title="Games" headers={['Name', 'Slug', 'Status', 'Actions']} rows={rows} render={row => <>
          <td>{String(row.name)}</td><td>{String(row.slug)}</td><td><Status value={Boolean(row.is_published)} /></td><td>
            <button className="text-action" disabled={busy} onClick={() => { setGameFilter(String(row.id)); setTab('levels'); setRows([]); setMessage(''); }}>Levels</button>
            <button className="text-action" disabled={busy} onClick={() => { setMessage(''); setEditing({ kind: 'games', row }); }}>Edit</button>
            <button className="text-action" disabled={busy} onClick={() => void togglePublished('games', row)}>{row.is_published ? 'Unpublish' : 'Publish'}</button>
          </td></>} />}
        {tab === 'levels' && <>
          <div className="content-context"><label>Game<select aria-label="Filter levels by game" value={gameFilter} disabled={busy}
            onChange={event => setGameFilter(event.target.value)}><option value="">All games</option>
            {games.map(game => <option key={String(game.id)} value={String(game.id)}>{String(game.name)}</option>)}</select></label>
            <p className="muted">Open Images & answers on a level to add pictures, name them and choose their order.</p></div>
          <DataTable title="Levels" headers={['Name', 'Game', 'Images', 'Status', 'Actions']}
            rows={rows.filter(row => !gameFilter || row.game_id === gameFilter)} render={row => <>
              <td>{String(row.name)}</td><td>{String((row.games as Row | null)?.name ?? '—')}</td>
              <td>{((row.level_questions as Row[] | undefined) ?? []).length} / {String(row.question_count)}
                {((row.publication_issues as string[] | undefined) ?? []).map(issue => <p className="small-note" key={issue}>{issue}</p>)}</td>
              <td><Status value={Boolean(row.is_published)} /></td><td>
                <button className="text-action" disabled={busy} onClick={() => {
                  setSelectedLevel(row); setMessage('');
                  setSelectedQuestionIds([...((row.level_questions as Row[] | undefined) ?? [])].sort((a,b) => Number(a.order_index)-Number(b.order_index)).map(item => String(item.question_id)));
                }}>Images & answers</button>
                <button className="text-action" disabled={busy} onClick={() => { setMessage(''); setEditing({ kind: 'levels', row }); }}>Edit</button>
                <button className="text-action" disabled={busy || (!row.is_published && ((row.publication_issues as string[] | undefined) ?? []).length > 0)}
                  onClick={() => void togglePublished('levels', row)}>{row.is_published ? 'Unpublish' : 'Publish'}</button>
              </td></>} />
        </>}
        {selectedLevel && !creating && !editing && <ContentDialog title={`Images & answers · ${String(selectedLevel.name)}`} busy={busy || uploading}
          onClose={() => { setSelectedLevel(null); setMessage(''); }}>
          <section className="form-panel">
            <p className="muted">Game: {String((selectedLevel.games as Row | null)?.name ?? games.find(game => game.id === selectedLevel.game_id)?.name ?? 'Selected game')} → {String(selectedLevel.name)}</p>
            <p>This level needs {String(selectedLevel.question_count)} images before publishing. You can save a draft with fewer images. Each picture shows its correct answer; selection numbers are the play order.</p>
            {Boolean(selectedLevel.is_published) && <p className="notice">To add more images, unpublish this level first. Unpublish its game first if this is its last published level.</p>}
            {message && <p className="notice" role="status">{message}</p>}
            <div className="section-heading"><strong>{selectedQuestionIds.length} / {String(selectedLevel.question_count)} selected</strong>
              <button className="primary-button" disabled={busy || Boolean(selectedLevel.is_published)} onClick={() => { setMessage(''); setCreating('questions'); }}>Add new image & answer</button></div>
            {!questionRows.length && <p className="muted">No image questions yet. Add your first picture and its correct name above.</p>}
            <div className="level-image-grid">{questionRows.map(question => {
              const id = String(question.id); const order = selectedQuestionIds.indexOf(id);
              return <article className={order >= 0 ? 'level-image selected' : 'level-image'} key={id}>
                <label><input type="checkbox" aria-label={`Use ${String(question.primary_answer)}`} checked={order >= 0}
                  disabled={busy || (!question.is_active && order < 0)} onChange={event => setSelectedQuestionIds(current => event.target.checked ? [...current, id] : current.filter(item => item !== id))} />
                  <span>{order >= 0 ? `#${order + 1}` : 'Select image'}</span>
                  {Boolean(question.image_url) && <img src={String(question.image_url)} alt={String(question.primary_answer)} />}
                  <strong>{String(question.primary_answer)}</strong>
                  <small>Accepted: {((question.question_answers as Row[] | undefined) ?? []).map(answer => String(answer.answer)).join(', ') || String(question.primary_answer)}</small>
                  {!question.is_active && <small>Inactive — edit to activate</small>}
                </label><button className="text-action" disabled={busy} onClick={() => { setMessage(''); setEditing({ kind: 'questions', row: question }); }}>Edit answer / image</button>
              </article>;
            })}</div>
            <div className="form-actions"><button className="quiet-button" disabled={busy} onClick={() => { setSelectedLevel(null); setMessage(''); }}>Cancel</button>
              <button className="primary-button" disabled={busy || (Boolean(selectedLevel.is_published) && selectedQuestionIds.length !== Number(selectedLevel.question_count))} onClick={() => void saveLevelQuestions()}>Save level images</button></div>
          </section>
        </ContentDialog>}
        {tab === 'questions' && <>
          <p className="muted">Image questions are reusable. To link them to a game, open Games → Levels → Images & answers and save the level's selection.</p>
          <DataTable title="Images & answers" headers={['Correct answer', 'Image', 'Accepted names', 'Actions']} rows={rows} render={row => <>
            <td>{String(row.primary_answer)}<small>{row.is_active ? ' · Active' : ' · Inactive'}</small></td>
            <td>{row.image_url ? <img className="question-thumbnail" src={String(row.image_url)} alt={String(row.primary_answer)} /> : 'Image unavailable'}</td>
            <td>{((row.question_answers as Row[] | undefined) ?? []).map(answer => String(answer.answer)).join(', ')}</td>
            <td><button className="text-action" disabled={busy} onClick={() => { setMessage(''); setEditing({ kind: 'questions', row }); }}>Edit answer / image</button></td>
          </>} />
        </>}
      </section>
    </main>
  );
}

function Status({ value }: { value: boolean }) {
  return <span className={value ? 'status published' : 'status draft'}>{value ? 'Published' : 'Draft'}</span>;
}

function DataTable({ title, headers, rows, render }: { title: string; headers: string[]; rows: Row[]; render: (row: Row) => ReactNode }) {
  return <section className="section-block table-section"><div className="section-heading"><div><p className="eyebrow">LIBRARY</p><h2>{title}</h2></div><span className="row-count">{rows.length} items</span></div>
    <div className="table-wrap"><table><thead><tr>{headers.map((header) => <th key={header}>{header}</th>)}</tr></thead><tbody>
      {rows.map((row) => <tr key={String(row.id)}>{render(row)}</tr>)}
      {rows.length === 0 && <tr><td colSpan={headers.length}>No items yet.</td></tr>}
    </tbody></table></div>
  </section>;
}
