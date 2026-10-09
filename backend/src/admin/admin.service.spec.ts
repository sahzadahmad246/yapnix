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
