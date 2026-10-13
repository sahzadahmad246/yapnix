'use client';
import React, { useEffect, useState } from 'react';
import { createClient, type Session } from '@supabase/supabase-js';
import { apiRequest, ApiError } from './api';
import { ContentDialog } from './content-dialog';
import { ContentEditor, type ContentRow as Row } from './content-editor';
import { CreateContent } from './create-content';
import { GameForm, CategoryForm, type GamePromptOption } from './game-form';
import { ImagePicker, type MediaImage } from './image-picker';

const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
const key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
const supabase = url && key ? createClient(url, key) : null;
type Tab = 'overview' | 'categories' | 'games' | 'questions' | 'media';
type Form = {kind: 'game' | 'category' | 'question'; row?: Row};
const names: Record<Tab, string> = {overview: 'Overview', categories: 'Categories', games: 'Games', questions: 'Images & answers', media: 'Media'};
const assignments = (game: Row) => [...((game.game_questions as Row[] | undefined) ?? [])].sort((a,b) => Number(a.order_index)-Number(b.order_index)).map(item => String(item.question_id));

export default function Home() {
  const [session, setSession] = useState<Session | null>(null);
  const [checking, setChecking] = useState(true);
  const [denied, setDenied] = useState('');
  const [tab, setTab] = useState<Tab>('overview');
  const [prompts, setPrompts] = useState<GamePromptOption[]>([]);
  const [categories, setCategories] = useState<Row[]>([]);
  const [games, setGames] = useState<Row[]>([]);
  const [questions, setQuestions] = useState<Row[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState(false);
  const [reload, setReload] = useState(0);
  const [form, setForm] = useState<Form | null>(null);
  const [categoryFilter, setCategoryFilter] = useState('');
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('all');
  const [activeId, setActiveId] = useState('');
  const [selected, setSelected] = useState<string[]>([]);
  const [questionSearch, setQuestionSearch] = useState('');
  const [deleting, setDeleting] = useState<Row | null>(null);
  const [deleteName, setDeleteName] = useState('');
  const [image, setImage] = useState<MediaImage | null>(null);
  const busy = loading || saving || uploading;
  const active = games.find(game => String(game.id) === activeId);
  const dirty = !!active && JSON.stringify(assignments(active)) !== JSON.stringify(selected);

  useEffect(() => {
    if (!supabase) { setChecking(false); return; }
    void supabase.auth.getSession().then(({data, error}) => { setSession(data.session); if (error) setMessage(error.message); setChecking(false); }).catch(() => { setMessage('Could not restore your session. Please sign in again.'); setChecking(false); });
    const {data} = supabase.auth.onAuthStateChange((_event, next) => {
      setSession(next);
      if (!next) { setGames([]); setCategories([]); setQuestions([]); setForm(null); setDeleting(null); setActiveId(''); setDenied(''); }
    });
    return () => data.subscription.unsubscribe();
  }, []);
  useEffect(() => {
    if (!session) return;
    let cancelled = false;
    setLoading(true); setDenied('');
    void Promise.all([apiRequest<Row[]>('/admin/categories', session.access_token), apiRequest<Row[]>('/admin/games', session.access_token), apiRequest<Row[]>('/admin/questions', session.access_token), apiRequest<GamePromptOption[]>('/admin/game-prompts', session.access_token)])
      .then(([categoryRows, gameRows, questionRows, promptRows]) => { if (!cancelled) { setCategories(categoryRows); setGames(gameRows); setQuestions(questionRows); setPrompts(promptRows); } })
      .catch(failure => { if (!cancelled) { setError(true); setMessage(failure instanceof Error ? failure.message : 'Could not load workspace'); if (failure instanceof ApiError && [401,403].includes(failure.status)) setDenied(failure.message); } })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [session, reload]);

  async function mutate(path: string, method: string, value?: Row, done?: (created: Row) => void) {
    if (!session || busy) return;
    setSaving(true); setMessage(''); setError(false);
    try {
      const created = await apiRequest<Row>(path, session.access_token, {method, ...(value ? {body: JSON.stringify(value)} : {})});
      setMessage('Changes saved.'); done?.(created); setReload(current => current + 1);
    } catch (failure) { setError(true); setMessage(failure instanceof Error ? failure.message : 'Could not save changes'); }
    finally { setSaving(false); }
  }
  function openGame(game: Row) { setActiveId(String(game.id)); setSelected(assignments(game)); setQuestionSearch(''); setMessage(''); }
  function leaveGame() { if (dirty && !window.confirm('Leave without saving the selected questions?')) return false; setActiveId(''); return true; }
  function navigate(next: Tab) { if (!leaveGame()) return; setTab(next); setMessage(''); setSearch(''); }
  function publish(kind: 'games' | 'categories', row: Row) { void mutate(`/admin/${kind}/${String(row.id)}`, 'PATCH', {is_published: !row.is_published}); }
  const closeForm = () => { setForm(null); setMessage(''); };
  async function saveForm(value: Row) {
    if (!form) return;
    const kind = form.kind === 'game' ? 'games' : form.kind === 'category' ? 'categories' : 'questions';
    await mutate(`/admin/${kind}${form.row ? `/${String(form.row.id)}` : ''}`, form.row ? 'PATCH' : 'POST', value, created => {
      if (kind === 'games' && !form.row) { setGames(current => [{...created, ...value, game_questions: []}, ...current]); setActiveId(String(created.id)); setSelected([]); setTab('games'); }
      if (kind === 'questions' && !form.row && activeId && created.id) { setSelected(current => [...current, String(created.id)]); setMessage('Image saved. Save game questions to link your selection.'); }
      setForm(null);
    });
  }
  async function signOut() { const result = await supabase?.auth.signOut(); if (result?.error) {setMessage(result.error.message); return;} setSession(null); setGames([]); setCategories([]); setQuestions([]); setActiveId(''); setForm(null); setDeleting(null); }
  if (checking) return <main className="loading"><div className="loading-brand"><span className="spinner" /><strong>Yapnix Studio</strong><span>Opening Yapnix Studio…</span></div></main>;
  if (!session) return <main className="login-shell"><div className="login-mark">Y</div><p className="eyebrow">CONTENT WORKSPACE</p><h1>Yapnix Studio</h1><p className="muted">Create speaking games, organize categories and manage questions.</p>
    <button className="primary-button" onClick={() => { if (!supabase) {setMessage('Configure the Studio Supabase environment first.'); return;} void supabase.auth.signInWithOAuth({provider:'google',options:{redirectTo:window.location.origin}}).then(({error}) => {if(error)setMessage(error.message);}); }}>Continue with Google</button>
    {message && <p role="alert" className="notice">{message}</p>}<p className="small-note">Google sign-in alone does not grant administrator access.</p></main>;
  if (denied) return <main className="login-shell"><h1>Administrator access required</h1><p role="alert" className="notice">{denied}</p><button className="primary-button" onClick={() => void signOut()}>Sign out</button></main>;
  const categoryName = (game: Row) => String(categories.find(category => category.id === game.category_id)?.name ?? 'Category');
  const issues = (game: Row) => (game.publication_issues as string[] | undefined) ?? [];
  const visibleGames = games.filter(game => (!categoryFilter || game.category_id === categoryFilter) && String(game.name).toLowerCase().includes(search.toLowerCase()) && (status === 'all' || Boolean(game.is_published) === (status === 'published')));
  const visibleQuestions = questions.filter(question => `${question.primary_answer} ${((question.question_answers as Row[] | undefined) ?? []).map(answer => answer.answer).join(' ')}`.toLowerCase().includes(questionSearch.toLowerCase()));
  return <main className="studio-shell"><aside className="sidebar"><a href="#overview" className="brand" onClick={event => {event.preventDefault(); if(!busy)navigate('overview');}}><span>Y</span>YAPNIX <small>STUDIO</small></a>
    <nav aria-label="Admin sections">{(Object.keys(names) as Tab[]).map(item => <button key={item} disabled={busy} aria-current={tab === item ? 'page' : undefined} className={tab === item ? 'nav-item active' : 'nav-item'} onClick={() => navigate(item)}><span className="nav-dot" />{names[item]}</button>)}</nav>
    <div className="sidebar-bottom"><span className="signed-in">{session.user.email}</span><button className="quiet-button" disabled={busy} onClick={() => {if(leaveGame())void signOut();}}>Sign out</button></div></aside>
    <section className="workspace" aria-busy={busy}><header className="topbar"><div><p className="eyebrow">YAPNIX / {active ? categoryName(active).toUpperCase() : 'CONTENT'}</p><h1>{active ? String(active.name) : names[tab]}</h1><p className="page-description">{active ? 'Add pictures, set accepted answers and arrange the play order.' : 'Category → Game → Questions. Simple speaking experiences.'}</p></div>
      <div className="form-actions">{!active && ['games','categories','questions'].includes(tab) && <button className="primary-button" disabled={busy || (tab === 'games' && !categories.length)} onClick={() => {setMessage('');setForm({kind:tab === 'games' ? 'game' : tab === 'categories' ? 'category' : 'question'});}}>{tab === 'games' ? 'New game' : tab === 'categories' ? 'New category' : 'Add image & answer'}</button>}<button className="refresh-button" disabled={busy} onClick={() => {setMessage('');setReload(current => current+1);}}>{loading ? 'Refreshing…' : 'Refresh'}</button></div></header>
      {busy && <div className="activity-bar" role="status"><span className="spinner" />{loading ? 'Loading your workspace…' : deleting ? 'Deleting game…' : uploading ? 'Uploading image…' : 'Saving your changes…'}</div>}
      {message && !form && !deleting && <div className="notice feedback" role={error ? 'alert' : 'status'}><span>{message}</span><button className="quiet-button" disabled={busy} onClick={() => {setMessage(''); if(error)setReload(current=>current+1);}}>{error ? 'Try again' : 'Dismiss'}</button></div>}
      {loading && !games.length && <div className="skeleton-grid" aria-hidden="true">{[1,2,3].map(item => <div className="skeleton-card" key={item}><i/><i/><i/></div>)}</div>}
      {form && <ContentDialog title={form.kind === 'game' ? form.row ? 'Game settings' : 'Create game' : form.kind === 'category' ? form.row ? 'Category settings' : 'Create category' : form.row ? 'Edit image & answer' : 'Add image & answer'} busy={busy} onClose={closeForm}>
        {message && <p className="notice" role="alert">{message}</p>}
        {form.kind === 'game' ? <GameForm prompts={prompts} row={form.row} categories={categories} categoryId={categoryFilter || String(categories[0]?.id ?? '')} busy={busy} onSave={saveForm} onCancel={closeForm}/> : form.kind === 'category' ? <CategoryForm row={form.row} busy={busy} onSave={saveForm} onCancel={closeForm}/> : form.row ? <ContentEditor prompt={prompts.find(prompt=>prompt.key===active?.prompt_key)?.label} kind="questions" row={form.row} token={session.access_token} busy={busy} onSave={saveForm} onCancel={closeForm} onBusyChange={setUploading}/> : <CreateContent prompt={prompts.find(prompt=>prompt.key===active?.prompt_key)?.label} kind="questions" games={[]} gameId="" token={session.access_token} busy={saving || loading} onSave={(_path,value)=>saveForm(value)} onCancel={closeForm} onBusyChange={setUploading}/>}
      </ContentDialog>}
      {deleting && <ContentDialog title="Delete game" busy={busy} onClose={() => {setDeleting(null);setMessage('');}}><section className="form-panel delete-panel"><span className="danger-icon">!</span><h3>Delete {String(deleting.name)}?</h3><p>This permanently deletes the game, its question assignments and saved player progress. Reusable questions and uploaded files stay in your library. You cannot undo this action.</p>
        {message && <p className="notice" role="alert">{message}</p>}<form onSubmit={event => {event.preventDefault();if(deleteName === String(deleting.name))void mutate(`/admin/games/${String(deleting.id)}`, 'DELETE', undefined,()=>{setDeleting(null);setActiveId('');setMessage('Game deleted. Reusable images remain in your library.');});}}><label>Type the game name to confirm<input autoFocus disabled={busy} value={deleteName} onChange={event=>setDeleteName(event.target.value)} autoComplete="off"/></label><div className="form-actions"><button type="button" className="quiet-button" disabled={busy} onClick={()=>{setDeleting(null);setMessage('');}}>Cancel</button><button className="danger-button" disabled={busy || deleteName !== String(deleting.name)}>{saving ? 'Deleting…' : 'Delete game permanently'}</button></div></form></section></ContentDialog>}
      {tab === 'overview' && <><div className={loading ? 'metrics is-loading' : 'metrics'}>{[['Categories',categories.length],['Games',games.length],['Image questions',questions.length]].map(([label,value])=><div className="metric" key={label}><span>{label}</span><strong>{value}</strong></div>)}</div><section className="form-panel welcome-panel"><p className="eyebrow">FROM IDEA TO PLAY</p><h2>One category. Endless things to say.</h2><p className="muted">Guess is a category. Animals, Foods and Everyday Objects are separate games inside it. Add questions directly to a game, choose the timer, and publish.</p><button className="primary-button" disabled={busy} onClick={()=>navigate('categories')}>Explore categories</button></section></>}
      {tab === 'categories' && <><p className="muted">Categories group games by the kind of speaking experience. The current player supports image guessing.</p><div className="game-grid">{categories.map(category=><article className="game-card" key={String(category.id)}><div className="section-heading"><span className="eyebrow">CATEGORY · IMAGE GUESSING</span><Status value={Boolean(category.is_published)}/></div><h2>{String(category.name)}</h2><p className="muted">{String(category.description || 'Add speaking games to this category.')}</p><p className="small-note">{games.filter(game=>game.category_id === category.id).length} games</p><div className="game-card-actions"><button className="primary-button" disabled={busy} onClick={()=>{setCategoryFilter(String(category.id));navigate('games');}}>Games</button><button className="text-action" disabled={busy} onClick={()=>{setMessage('');setForm({kind:'category',row:category});}}>Edit</button><button className="text-action" disabled={busy} onClick={()=>publish('categories',category)}>{category.is_published ? 'Unpublish' : 'Publish'}</button></div></article>)}</div>{!categories.length && !loading && <p className="muted">Create your first category to start adding games.</p>}</>}
      {tab === 'games' && !active && <><div className="library-toolbar"><label className="search-field"><span>Search games</span><input type="search" value={search} onChange={event=>setSearch(event.target.value)} placeholder="Animals, Foods…"/></label><label className="search-field"><span>Category</span><select value={categoryFilter} onChange={event=>setCategoryFilter(event.target.value)}><option value="">All categories</option>{categories.map(category=><option key={String(category.id)} value={String(category.id)}>{String(category.name)}</option>)}</select></label><div className="filter-tabs" aria-label="Game status">{['all','draft','published'].map(item=><button key={item} aria-pressed={status===item} className={status===item?'active':''} onClick={()=>setStatus(item)}>{item==='all'?'All games':item==='draft'?'Drafts':'Published'}</button>)}</div></div>
        {!visibleGames.length && !loading && <section className="empty-state"><h2>{games.length ? 'No matching games' : 'Create your first game'}</h2><p className="muted">Choose a category, name the game and add its questions directly.</p>{games.length > 0 && <button className="quiet-button" onClick={()=>{setSearch('');setCategoryFilter('');setStatus('all');}}>Clear filters</button>}</section>}
        <div className="game-grid">{visibleGames.map(game=><article className="game-card" key={String(game.id)}><div className="section-heading"><span className="eyebrow">{categoryName(game)}</span><Status value={Boolean(game.is_published)}/></div><h2>{String(game.name)}</h2><p className="muted">{String(game.description || 'A speaking challenge, one picture at a time.')}</p><p className="small-note">{assignments(game).length} / {String(game.question_count)} questions · {String(game.default_time_limit_seconds)}s each</p><div className="game-card-actions"><button className="primary-button" disabled={busy} onClick={()=>openGame(game)}>Questions</button><button className="text-action" disabled={busy} onClick={()=>{setMessage('');setForm({kind:'game',row:game});}}>Edit</button><button className="text-action" disabled={busy || (!game.is_published && issues(game).length>0)} onClick={()=>publish('games',game)}>{game.is_published?'Unpublish':'Publish'}</button><button className="text-action danger-text" disabled={busy} onClick={()=>{setMessage('');setDeleteName('');setDeleting(game);}}>Delete</button></div>{issues(game).map(issue=><p className="small-note" key={issue}>{issue}</p>)}</article>)}</div></>}
      {active && <><section className="workflow-banner"><button className="quiet-button" disabled={busy} onClick={leaveGame}>← All games</button><div><p className="eyebrow">{categoryName(active)} / {String(active.name)}</p><strong>{selected.length} questions selected</strong><p className="muted">{prompts.find(prompt=>prompt.key===active.prompt_key)?.label ?? 'What is this?'} · {String(active.default_time_limit_seconds)}s per picture</p></div><div className="game-publication"><Status value={Boolean(active.is_published)}/><button className="quiet-button" disabled={busy} onClick={()=>{setMessage('');setForm({kind:'game',row:active});}}>Game settings</button><button className="primary-button" disabled={busy || dirty || (!active.is_published && issues(active).length>0)} onClick={()=>publish('games',active)}>{active.is_published?'Unpublish game':'Publish game'}</button></div></section>
        <section className="form-panel"><div className="section-heading"><div><h2>Questions & answers</h2><p className="small-note">Already have uploaded pictures? Add an image & answer, then choose Browse uploaded images.</p></div><button className="primary-button" disabled={busy || selected.length>=100} onClick={()=>{setMessage('');setForm({kind:'question'});}}>Add new image & answer</button></div>
          {Boolean(active.is_published) && <p className="notice">Saving a different question selection moves this game to draft. Review it and publish again when ready.</p>}{dirty && <p className="notice">You have unsaved question selections. Save game questions before publishing.</p>}{issues(active).map(issue=><p className="small-note" key={issue}>{issue}</p>)}
          <div className="section-heading"><h3>Selected for this game</h3><span className="row-count">{dirty ? "Unsaved changes" : "Saved selection"}</span></div>
          {!selected.length && <p className="muted">No pictures selected. Choose image questions from your library below.</p>}
          <ol className="selected-images" aria-label="Image play order">{selected.map((id,index)=><li key={id}><span className="selected-question-label">{Boolean(questions.find(question=>String(question.id)===id)?.image_url) && <img src={String(questions.find(question=>String(question.id)===id)?.image_url)} alt=""/>}<span>{index+1}. {String(questions.find(question=>String(question.id)===id)?.primary_answer ?? 'Image unavailable')}</span></span><div>{[-1,1].map(offset=><button key={offset} className="quiet-button" aria-label={`Move image ${index+1} ${offset<0?'earlier':'later'}`} disabled={busy || index+offset<0 || index+offset>=selected.length} onClick={()=>setSelected(current=>{const next=[...current];[next[index],next[index+offset]]=[next[index+offset],next[index]];return next;})}>{offset<0?'↑':'↓'}</button>)}<button className="quiet-button" disabled={busy} aria-label={`Remove image ${index+1}`} onClick={()=>setSelected(current=>current.filter(item=>item!==id))}>Remove</button></div></li>)}</ol>
          <h3>Reusable question library</h3><p className="small-note">Removing a picture from the selected list keeps it in this library. Select it again whenever you need it.</p>
          <label>Find an image or answer<input type="search" value={questionSearch} onChange={event=>setQuestionSearch(event.target.value)} placeholder="Search correct and accepted names"/></label>
          {!visibleQuestions.length && <p className="muted">No matching images. Add a new image and answer, or try another search.</p>}
          <div className="level-image-grid">{visibleQuestions.map(question=>{const id=String(question.id);const order=selected.indexOf(id);return <article className={order>=0?'level-image selected':'level-image'} key={id}><label><input type="checkbox" aria-label={`Use ${String(question.primary_answer)}`} checked={order>=0} disabled={busy || (order<0 && (!question.is_active || selected.length>=100))} onChange={event=>{const checked=event.target.checked;setSelected(current=>checked?[...current,id]:current.filter(item=>item!==id));}}/><span>{order>=0?`#${order+1}`:'Select image'}</span>{!!question.image_url && <img src={String(question.image_url)} alt={String(question.primary_answer)}/>}<strong>{String(question.primary_answer)}</strong><small>{((question.question_answers as Row[]|undefined)??[]).map(answer=>String(answer.answer)).join(', ')}</small></label><button className="text-action" disabled={busy} onClick={()=>{setMessage('');setForm({kind:'question',row:question});}}>Edit answer / image</button></article>;})}</div>
          <div className="form-actions"><button className="quiet-button" disabled={busy || !dirty} onClick={()=>setSelected(assignments(active))}>Discard selection changes</button><button className="quiet-button" disabled={busy || !selected.length} onClick={()=>setSelected([])}>Remove all from game</button><button className="primary-button" disabled={busy || !dirty} onClick={()=>void mutate(`/admin/games/${activeId}/questions`,'PUT',{question_ids:selected},()=>setMessage('Questions saved as draft. The question count now matches your selection. Publish when ready.'))}>{saving?'Saving…':'Save game questions'}</button></div>
        </section></>}
      {tab === 'questions' && <div className="game-grid">{questions.map(question=><article className="game-card" key={String(question.id)}>{!!question.image_url && <img className="image-preview" src={String(question.image_url)} alt={String(question.primary_answer)}/>}<h2>{String(question.primary_answer)}</h2><p className="small-note">{question.is_active?'Active':'Inactive'} · {((question.question_answers as Row[]|undefined)??[]).map(answer=>String(answer.answer)).join(', ')}</p><button className="text-action" disabled={busy} onClick={()=>{setMessage('');setForm({kind:'question',row:question});}}>Edit answer / image</button></article>)}</div>}
      {tab === 'media' && <section className="form-panel"><h2>Image library</h2><p className="muted">Upload pictures here or while adding a question to a game.</p><ImagePicker token={session.access_token} value={image?.storage_path??''} imageUrl={image?.image_url} disabled={loading||saving} onChange={setImage} onBusyChange={setUploading}/></section>}
    </section></main>;
}
function Status({value}:{value:boolean}) { return <span className={value?'status published':'status draft'}>{value?'Published':'Draft'}</span>; }
