import { BadRequestException, ServiceUnavailableException } from '@nestjs/common';
import sharp from 'sharp';
import { describe, expect, it, vi } from 'vitest';
import { MediaService, MAX_IMAGE_BYTES } from './media.service.js';
import type { SupabaseService } from '../supabase/supabase.service.js';

function fixture() {
  const object = {
    upload: vi.fn().mockResolvedValue({ error: null }),
    remove: vi.fn().mockResolvedValue({ error: null }),
    createSignedUrl: vi.fn().mockResolvedValue({ data: { signedUrl: 'https://storage.test/signed' }, error: null }),
    createSignedUrls: vi.fn().mockResolvedValue({ data: [{ path: 'image.webp', signedUrl: 'https://storage.test/signed' }], error: null }),
  };
  const getBucket = vi.fn().mockResolvedValue({ data: { public: false }, error: null });
  const single = vi.fn().mockResolvedValue({ data: { id: 'media-id' }, error: null });
  const insert = vi.fn().mockReturnValue({ select: () => ({ single }) });
  const supabase = { adminClient: { storage: { getBucket, from: () => object }, from: () => ({ insert }) } } as unknown as SupabaseService;
  return { service: new MediaService(supabase), object, getBucket, insert, single };
}
const image = () => sharp({ create: { width: 8, height: 8, channels: 3, background: 'red' } }).png().toBuffer();

describe('private image uploads', () => {
  it('decodes and re-encodes images with generated paths and saves a media record', async () => {
    const { service, object, insert } = fixture();
    await service.upload({ buffer: await image(), originalname: '../../picture.png' });
    const [path, encoded, options] = object.upload.mock.calls[0];
    expect(path).toMatch(/^questions\/[0-9a-f-]+\.webp$/);
    expect((await sharp(encoded).metadata()).format).toBe('webp');
    expect(options).toEqual({ contentType: 'image/webp', upsert: false });
    expect(insert).toHaveBeenCalledWith(expect.objectContaining({ mime_type: 'image/webp', width: 8, height: 8 }));
  });
  it('centre-crops non-square artwork to a 512px square', async () => {
    const { service, object, insert } = fixture();
    const buffer = await sharp({ create: { width: 80, height: 40, channels: 3, background: 'red' } }).png().toBuffer();
    await service.upload({ buffer, originalname: 'game.png' }, true);
    const [path, encoded] = object.upload.mock.calls[0];
    expect(path).toMatch(/^artwork\/[0-9a-f-]+\.webp$/);
    expect(await sharp(encoded).metadata()).toMatchObject({ width: 512, height: 512 });
    expect(insert).toHaveBeenCalledWith(expect.objectContaining({ width: 512, height: 512 }));
  });
  it('does not request a signed URL for a missing legacy icon', async () => {
    const { service, object } = fixture();
    expect(await service.withArtworkUrls([{ icon_storage_path: null }])).toEqual([{ icon_storage_path: null, icon_url: null }]);
    expect(object.createSignedUrls).not.toHaveBeenCalled();
  });
  it('rejects non-square or missing artwork from the library', async () => {
    const maybeSingle = vi.fn().mockResolvedValue({ data: { id: 'image', width: 80, height: 40 }, error: null });
    const query = { select: () => query, eq: () => query, maybeSingle };
    const service = new MediaService({ adminClient: { from: () => query } } as unknown as SupabaseService);
    await expect(service.assertReadyImage('wide.webp', true)).rejects.toBeInstanceOf(BadRequestException);
    maybeSingle.mockResolvedValue({ data: null, error: null });
    await expect(service.assertReadyImage('missing.webp', true)).rejects.toBeInstanceOf(BadRequestException);
    maybeSingle.mockResolvedValue({ data: { id: 'image', width: 512, height: 512 }, error: null });
    await expect(service.assertReadyImage('square.webp', true)).resolves.toBeUndefined();
  });
  it('rejects missing, oversized, SVG and corrupt image data before touching storage', async () => {
    const { service, getBucket } = fixture();
    await expect(service.upload()).rejects.toBeInstanceOf(BadRequestException);
    for (const buffer of [Buffer.alloc(MAX_IMAGE_BYTES + 1), Buffer.from('<svg></svg>'), Buffer.from('not an image')]) {
      await expect(service.upload({ buffer, originalname: 'fake.png' })).rejects.toBeInstanceOf(BadRequestException);
    }
    expect(getBucket).not.toHaveBeenCalled();
  });
  it('refuses to upload drafts into a public bucket', async () => {
    const { service, getBucket, object } = fixture();
    getBucket.mockResolvedValue({ data: { public: true }, error: null });
    await expect(service.upload({ buffer: await image(), originalname: 'picture.png' })).rejects.toBeInstanceOf(ServiceUnavailableException);
    expect(object.upload).not.toHaveBeenCalled();
  });
  it('removes the uploaded object when saving metadata fails', async () => {
    const { service, object, single } = fixture();
    single.mockResolvedValue({ data: null, error: new Error('database failed') });
    await expect(service.upload({ buffer: await image(), originalname: 'picture.png' })).rejects.toThrow('Could not save');
    expect(object.remove).toHaveBeenCalledWith([object.upload.mock.calls[0][0]]);
  });
  it('signs only the supplied question paths and reports missing previews', async () => {
    const { service, object } = fixture();
    const result = await service.withImageUrls([{ image_storage_path: 'image.webp' }, { image_storage_path: 'missing.webp' }]);
    expect(object.createSignedUrls).toHaveBeenCalledWith(['image.webp', 'missing.webp'], 3600);
    expect(result[0].image_url).toBe('https://storage.test/signed');
    expect(result[1].image_url).toBeNull();
  });
  it.each([
    { data: { public: true }, error: null },
    { data: null, error: null },
    { data: { public: false }, error: new Error('Storage unavailable') },
  ])('refuses image serving when private bucket configuration cannot be confirmed: %j', async result => {
    const { service, getBucket, object } = fixture();
    getBucket.mockResolvedValue(result);
    await expect(service.withImageUrls([{ image_storage_path: 'draft.webp' }])).rejects.toBeInstanceOf(ServiceUnavailableException);
    expect(object.createSignedUrls).not.toHaveBeenCalled();
  });
});
