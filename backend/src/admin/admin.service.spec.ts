import { InternalServerErrorException, NotFoundException } from '@nestjs/common';
import { describe, expect, it, vi } from 'vitest';
import type { MediaService } from '../media/media.service.js';
import { AdminService } from './admin.service.js';
import type { SupabaseService } from '../supabase/supabase.service.js';

describe('atomic question updates', () => {
  function setup(result: { data: unknown; error: unknown }) {
    const rpc = vi.fn().mockResolvedValue(result);
    const from = vi.fn();
    const service = new AdminService({ adminClient: { rpc, from } } as unknown as SupabaseService, { assertReadyImage: vi.fn() } as unknown as MediaService);
    return { service, rpc, from };
  }

  it('sends metadata and answers through one transaction without dropping omitted aliases', async () => {
    const data = { primary_answer: 'bicycle', accepted_answers: ['bicycle', 'road bike'] };
    const { service, rpc, from } = setup({ data, error: null });
    await expect(service.updateQuestion('question', { primary_answer: 'bicycle', difficulty: 2 })).resolves.toEqual(data);
    expect(rpc).toHaveBeenCalledTimes(1);
    expect(rpc).toHaveBeenCalledWith('update_question_content', {
      target_question_id: 'question', changes: { primary_answer: 'bicycle', difficulty: 2 },
    });
    expect(from).not.toHaveBeenCalled();
  });

  it('preserves an explicit request to remove aliases', async () => {
    const { service, rpc } = setup({ data: {}, error: null });
    await service.updateQuestion('question', { accepted_answers: [] });
    expect(rpc).toHaveBeenCalledWith('update_question_content', {
      target_question_id: 'question', changes: { accepted_answers: [] },
    });
  });

  it('maps a missing question to 404', async () => {
    const { service } = setup({ data: null, error: { code: 'P0002' } });
    await expect(service.updateQuestion('missing', {})).rejects.toBeInstanceOf(NotFoundException);
  });

  it('does not report success when the transaction fails', async () => {
    const { service } = setup({ data: null, error: { code: '23514' } });
    await expect(service.updateQuestion('question', { difficulty: 2 })).rejects.toBeInstanceOf(InternalServerErrorException);
  });
});


describe('game deletion', () => {
  function setup(data: unknown, error: unknown = null) {
    const query = { delete: vi.fn(), eq: vi.fn(), select: vi.fn(), maybeSingle: vi.fn().mockResolvedValue({ data, error }) };
    query.delete.mockReturnValue(query); query.eq.mockReturnValue(query); query.select.mockReturnValue(query);
    const from = vi.fn().mockReturnValue(query);
    return { service: new AdminService({ adminClient: { from } } as unknown as SupabaseService, {} as MediaService), query, from };
  }
  it('deletes only the specified game and returns its identifier', async () => {
    const { service, query, from } = setup({ id: 'game' });
    await expect(service.deleteGame('game')).resolves.toEqual({ id: 'game' });
    expect(from).toHaveBeenCalledWith('games');
    expect(query.eq).toHaveBeenCalledWith('id', 'game');
  });
  it('returns 404 when the game no longer exists', async () => {
    await expect(setup(null).service.deleteGame('missing')).rejects.toBeInstanceOf(NotFoundException);
  });
  it('keeps database failures from being reported as successful deletion', async () => {
    await expect(setup(null, { code: 'XX000' }).service.deleteGame('game')).rejects.toBeInstanceOf(InternalServerErrorException);
  });
});


describe('standalone game transactions', () => {
  it('sends game metadata and its question settings through one atomic write', async () => {
    const rpc=vi.fn().mockResolvedValue({data:{id:'game'},error:null});
    const service=new AdminService({adminClient:{rpc}} as unknown as SupabaseService, {} as MediaService);
    await service.createGame({name:'Animals',slug:'animals',difficulty:1,question_count:5});
    expect(rpc).toHaveBeenCalledWith('create_standalone_game',{content:{name:'Animals',slug:'animals',difficulty:1,question_count:5}});
    await service.updateGame('game',{is_published:true});
    expect(rpc).toHaveBeenCalledWith('update_standalone_game',{target_game_id:'game',changes:{is_published:true}});
    await service.setGameQuestions('game',{question_ids:['cat']});
    expect(rpc).toHaveBeenCalledWith('set_game_questions',{target_game_id:'game',question_ids:['cat']});
  });
});
