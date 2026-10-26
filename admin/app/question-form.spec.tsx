// @vitest-environment jsdom
import React from 'react';
import {cleanup,render,screen,waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {afterEach,describe,it,expect,vi} from 'vitest';
import {QuestionForm} from './question-form';
import {apiRequest} from './api';
vi.mock('./api',()=>({apiRequest:vi.fn()}));
afterEach(()=>{cleanup();vi.resetAllMocks();});
const picture={storage_path:'cat.webp',image_url:'https://storage.test/cat',original_filename:'cat.jpg'};
function mount(){const onSave=vi.fn().mockResolvedValue(undefined);const onCancel=vi.fn();render(<QuestionForm token="admin" busy={false} prompt="Guess the animal" onSave={onSave} onCancel={onCancel} onBusyChange={()=>{}}/>);return {user:userEvent.setup(),onSave,onCancel};}
describe('picture and answer composer',()=>{
  it('reuses an uploaded picture and saves answers without uploading it again',async()=>{
    vi.mocked(apiRequest).mockResolvedValue([picture]);const {user,onSave}=mount();
    expect(screen.getByText('Guess the animal')).toBeTruthy();await user.click(screen.getByRole('button',{name:'Browse uploaded images'}));await user.click(await screen.findByRole('button',{name:/cat.jpg/}));await user.type(screen.getByLabelText('Correct name / answer'),'cat');expect(screen.queryByLabelText(/Other accepted names/)).toBeNull();await user.click(screen.getByRole('button',{name:'Save image & answer'}));
    expect(onSave).toHaveBeenCalledWith({image_storage_path:'cat.webp',primary_answer:'cat',difficulty:1,language:'en-US',is_active:true});expect(vi.mocked(apiRequest).mock.calls.every(call=>!call[2])).toBe(true);
  });
  it('clears an unsaved image, supports replacing it, and resets all draft answers',async()=>{
    vi.mocked(apiRequest).mockResolvedValue([picture]);const {user,onSave}=mount();await user.click(screen.getByRole('button',{name:'Browse uploaded images'}));await user.click(await screen.findByRole('button',{name:/cat.jpg/}));await user.type(screen.getByLabelText('Correct name / answer'),'cat');await user.click(screen.getByRole('button',{name:'Remove selected image'}));expect((screen.getByRole('button',{name:'Save image & answer'}) as HTMLButtonElement).disabled).toBe(true);
    await user.click(screen.getByRole('button',{name:'Browse uploaded images'}));await user.click(await screen.findByRole('button',{name:/cat.jpg/}));await user.click(screen.getByRole('button',{name:'Reset draft'}));expect((screen.getByLabelText('Correct name / answer') as HTMLInputElement).value).toBe('');expect(screen.queryByRole('img',{name:'Selected question'})).toBeNull();expect(onSave).not.toHaveBeenCalled();
  });
  it('discards an unsaved form without creating a question',async()=>{const {user,onSave,onCancel}=mount();await user.type(screen.getByLabelText('Correct name / answer'),'cat');await user.click(screen.getByRole('button',{name:'Discard draft'}));await waitFor(()=>expect(onCancel).toHaveBeenCalled());expect(onSave).not.toHaveBeenCalled();});
});
