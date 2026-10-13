// @vitest-environment jsdom
import React from 'react';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Home from './page';
import { apiRequest, ApiError } from './api';
const auth = vi.hoisted(() => {
  process.env.NEXT_PUBLIC_SUPABASE_URL='https://test.supabase.co'; process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY='test-anon';
  return {getSession:vi.fn(),onAuthStateChange:vi.fn(),signOut:vi.fn(),signInWithOAuth:vi.fn()};
});
vi.mock('@supabase/supabase-js',()=>({createClient:()=>({auth})}));
vi.mock('./api',async original=>({...await original<typeof import('./api')>(),apiRequest:vi.fn()}));
let games: Record<string,unknown>[];
let questions: Record<string,unknown>[];
beforeEach(()=>{
  games=[{id:'game',name:'Animals',slug:'animals',category_id:'guess',question_count:5,default_time_limit_seconds:5,difficulty:1,is_published:false,game_questions:[],publication_issues:['Assign exactly 5 questions (0 selected).']}]; questions=[];
  auth.getSession.mockResolvedValue({data:{session:{access_token:'approved-token',user:{id:'admin',email:'admin@test.local'}}}});
  auth.onAuthStateChange.mockReturnValue({data:{subscription:{unsubscribe:vi.fn()}}});auth.signOut.mockResolvedValue({error:null});
  vi.mocked(apiRequest).mockImplementation(async(path,_token,options)=>{
    if(!options){if(path==='/admin/game-prompts')return [{key:'what_is_this',label:'What is this?'},{key:'guess_animal',label:'Guess the animal'}] as never;if(path==='/admin/categories')return [{id:'guess',name:'Guess',slug:'guess',is_published:true}] as never;if(path==='/admin/games')return games as never;if(path==='/admin/questions')return questions as never;}
    if(path==='/admin/games' && options?.method==='POST'){const created={id:'new-game',...JSON.parse(String(options.body)),game_questions:[],publication_issues:['Add questions']};games.push(created);return created as never;}
    if(options?.method==='DELETE'){games=games.filter(game=>path!==`/admin/games/${game.id}`);return {} as never;}
    if(path==='/admin/media')return {storage_path:'questions/cat.webp',image_url:'https://storage.test/cat',original_filename:'cat.png'} as never;
    if(path==='/admin/questions' && options?.method==='POST'){const question={id:'cat',...JSON.parse(String(options.body)),image_url:'https://storage.test/cat'};questions.push(question);return question as never;}
    if(path.endsWith('/questions') && options?.method==='PUT'){const ids=JSON.parse(String(options.body)).question_ids as string[];games[0]={...games[0],game_questions:ids.map((id,index)=>({question_id:id,order_index:index})),publication_issues:ids.length?[]:['Add at least one question.'],is_published:false,question_count:Math.max(1,ids.length)};return {} as never;}
    return {} as never;
  });
});
afterEach(()=>{cleanup();vi.resetAllMocks();});
async function openGames(){const user=userEvent.setup();render(<Home/>);await user.click(await screen.findByRole('button',{name:'Games'}));await screen.findByRole('heading',{name:'Animals'});return user;}

describe('category and standalone game workflows',()=>{
  it('blocks signed-in users who have not been approved',async()=>{vi.mocked(apiRequest).mockRejectedValue(new ApiError('Not approved',403));render(<Home/>);expect(await screen.findByRole('heading',{name:'Administrator access required'})).toBeTruthy();expect(screen.queryByRole('navigation')).toBeNull();});
  it('shows loading feedback while the workspace is fetched',async()=>{let resolve!: (value:never)=>void;const original=vi.mocked(apiRequest).getMockImplementation()!;vi.mocked(apiRequest).mockImplementation(async(path,...args)=>path==='/admin/categories'?new Promise(done=>{resolve=done;}):original(path,...args));render(<Home/>);expect(await screen.findByText('Loading your workspace…')).toBeTruthy();resolve([{id:'guess',name:'Guess'}] as never);await waitFor(()=>expect(screen.queryByText('Loading your workspace…')).toBeNull());});
  it('shows Guess as a category and creates a game with questions directly, without a level step',async()=>{
    const user=await openGames();expect(screen.queryByRole('button',{name:/levels/i})).toBeNull();await user.click(screen.getByRole('button',{name:'New game'}));
    await user.type(screen.getByLabelText('Game name'),'Foods');await user.selectOptions(screen.getByLabelText('Question shown to players'),'guess_animal');expect((screen.getByLabelText('Slug') as HTMLInputElement).value).toBe('foods');await user.click(within(screen.getByRole('dialog')).getByRole('button',{name:'Create game'}));
    expect(await screen.findByRole('heading',{name:'Foods'})).toBeTruthy();expect(await screen.findByRole('button',{name:'Add new image & answer'})).toBeTruthy();expect(screen.queryByRole('dialog')).toBeNull();
    const create=vi.mocked(apiRequest).mock.calls.find(call=>call[0]==='/admin/games'&&call[2]?.method==='POST');expect(JSON.parse(String(create?.[2]?.body))).toMatchObject({name:'Foods',prompt_key:'guess_animal',category_id:'guess',question_count:5,default_time_limit_seconds:5});expect(vi.mocked(apiRequest).mock.calls.some(call=>call[0].includes('/levels'))).toBe(false);
  });
  it('adds an image directly to a game and saves its assignment',async()=>{
    const user=await openGames();await user.click(screen.getByRole('button',{name:'Questions'}));await user.click(screen.getByRole('button',{name:'Add new image & answer'}));await user.upload(screen.getByLabelText('Upload question image'),new File(['png'],'cat.png',{type:'image/png'}));
    await waitFor(()=>expect((screen.getByLabelText('Correct name / answer') as HTMLInputElement).disabled).toBe(false));await user.type(screen.getByLabelText('Correct name / answer'),'cat');await user.click(screen.getByRole('button',{name:'Save image & answer'}));
    expect(await screen.findByRole('checkbox',{name:'Use cat'})).toBeTruthy();await waitFor(()=>expect((screen.getByRole('button',{name:'Save game questions'}) as HTMLButtonElement).disabled).toBe(false));await user.click(screen.getByRole('button',{name:'Save game questions'}));await waitFor(()=>expect(apiRequest).toHaveBeenCalledWith('/admin/games/game/questions','approved-token',{method:'PUT',body:JSON.stringify({question_ids:['cat']})}));
  });
  it('removes a published game image, can discard the change, and saves removal as a draft',async()=>{
    games[0]={...games[0],is_published:true,question_count:1,game_questions:[{question_id:'cat',order_index:0}],publication_issues:[]};
    questions=[{id:'cat',primary_answer:'cat',is_active:true}];
    const user=await openGames();await user.click(screen.getByRole('button',{name:'Questions'}));
    await user.click(screen.getByRole('button',{name:'Remove image 1'}));
    expect((screen.getByRole('checkbox',{name:'Use cat'}) as HTMLInputElement).checked).toBe(false);
    await user.click(screen.getByRole('button',{name:'Discard selection changes'}));
    expect((screen.getByRole('checkbox',{name:'Use cat'}) as HTMLInputElement).checked).toBe(true);
    await user.click(screen.getByRole('button',{name:'Remove image 1'}));await user.click(screen.getByRole('button',{name:'Save game questions'}));
    await waitFor(()=>expect(apiRequest).toHaveBeenCalledWith('/admin/games/game/questions','approved-token',{method:'PUT',body:JSON.stringify({question_ids:[]})}));
    expect(await screen.findByText(/Questions saved as draft/)).toBeTruthy();
    await waitFor(()=>expect(screen.getByText('Draft')).toBeTruthy());
  });

  it('keeps incomplete games unpublished and requires name confirmation before deleting',async()=>{
    const user=await openGames();expect((screen.getByRole('button',{name:'Publish'}) as HTMLButtonElement).disabled).toBe(true);await user.click(screen.getByRole('button',{name:'Delete'}));const dialog=screen.getByRole('dialog');const confirm=within(dialog).getByRole('button',{name:'Delete game permanently'});expect((confirm as HTMLButtonElement).disabled).toBe(true);await user.type(screen.getByLabelText('Type the game name to confirm'),'Animals');await user.click(confirm);await waitFor(()=>expect(screen.queryByRole('dialog')).toBeNull());expect(apiRequest).toHaveBeenCalledWith('/admin/games/game','approved-token',{method:'DELETE'});
  });
  it('keeps failed edits open with the entered values',async()=>{
    const user=await openGames();await user.click(screen.getByRole('button',{name:'Edit'}));await user.clear(screen.getByLabelText('Game name'));await user.type(screen.getByLabelText('Game name'),'Wild Animals');vi.mocked(apiRequest).mockRejectedValueOnce(new Error('Could not save game'));await user.click(screen.getByRole('button',{name:'Save game'}));expect((await screen.findByRole('alert')).textContent).toContain('Could not save game');expect((screen.getByLabelText('Game name') as HTMLInputElement).value).toBe('Wild Animals');
  });
  it('filters games by search and clears the empty state',async()=>{const user=await openGames();await user.type(screen.getByLabelText('Search games'),'unknown');expect(screen.getByRole('heading',{name:'No matching games'})).toBeTruthy();await user.click(screen.getByRole('button',{name:'Clear filters'}));expect(screen.getByRole('heading',{name:'Animals'})).toBeTruthy();});
});
