import { BadRequestException, Injectable, InternalServerErrorException, ServiceUnavailableException } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import sharp from 'sharp';
import { SupabaseService } from '../supabase/supabase.service.js';

export const IMAGE_BUCKET = 'content-images';
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
export interface ImageUpload { buffer: Buffer; originalname: string }

@Injectable()
export class MediaService {
  constructor(private readonly supabase: SupabaseService) {}

  private async requirePrivateBucket() {
    const { data: bucket, error } = await this.supabase.adminClient.storage.getBucket(IMAGE_BUCKET);
    if (error || !bucket || bucket.public) {
      throw new ServiceUnavailableException('Run Supabase migration 006 to set up a private content-images bucket');
    }
  }

  async upload(file?: ImageUpload, square = false) {
    if (!file?.buffer.length) throw new BadRequestException('Choose a JPEG, PNG or WebP image');
    if (file.buffer.length > MAX_IMAGE_BYTES) throw new BadRequestException('Images must be 5 MB or smaller');
    let encoded: { data: Buffer; info: { width: number; height: number } };
    try {
      const image = sharp(file.buffer, { limitInputPixels: 25_000_000, failOn: 'warning' });
      const metadata = await image.metadata();
      if (!['jpeg', 'png', 'webp'].includes(metadata.format ?? '') || (metadata.pages ?? 1) > 1) {
        throw new Error('Unsupported image');
      }
      encoded = await image.rotate().resize(square
        ? { width: 512, height: 512, fit: 'cover', position: 'centre' }
        : { width: 1920, height: 1920, fit: 'inside', withoutEnlargement: true })
        .webp({ quality: 85 }).toBuffer({ resolveWithObject: true });
    } catch {
      throw new BadRequestException('Use a valid, non-animated JPEG, PNG or WebP image (up to 25 megapixels)');
    }
    if (encoded.data.length > MAX_IMAGE_BYTES) throw new BadRequestException('The processed image is too large');
    const storage = this.supabase.adminClient.storage;
    await this.requirePrivateBucket();
    const path = `${square ? 'artwork' : 'questions'}/${randomUUID()}.webp`;
    const object = storage.from(IMAGE_BUCKET);
    const { error } = await object.upload(path, encoded.data, { contentType: 'image/webp', upsert: false });
    if (error) throw new ServiceUnavailableException('Image upload failed; check the Storage bucket configuration');
    try {
      const { data: signed, error: signedError } = await object.createSignedUrl(path, 3600);
      if (signedError || !signed?.signedUrl) throw new Error('Cannot preview uploaded image');
      const { data, error: recordError } = await this.supabase.adminClient.from('media_records').insert({
        storage_path: path, original_filename: file.originalname.slice(0, 255), mime_type: 'image/webp',
        size_bytes: encoded.data.length, width: encoded.info.width, height: encoded.info.height, status: 'ready',
      }).select().single();
      if (recordError || !data) throw new Error('Cannot save media record');
      return { ...data, image_url: signed.signedUrl };
    } catch {
      await object.remove([path]);
      throw new InternalServerErrorException('Could not save the uploaded image; try again');
    }
  }

  async assertReadyImage(path: string, square = false) {
    const { data, error } = await this.supabase.adminClient.from('media_records')
      .select('id,width,height').eq('storage_path', path).eq('status', 'ready').maybeSingle();
    if (error) throw new InternalServerErrorException('Unable to check image');
    if (!data) throw new BadRequestException('Upload or select an image from the Studio media library first');
    if (square && (!data.width || data.width !== data.height)) throw new BadRequestException('Choose square artwork (4:4). Upload here to crop it automatically.');
  }

  async withImageUrls<T extends { image_storage_path: string }>(rows: T[]) {
    if (!rows.length) return [];
    await this.requirePrivateBucket();
    const paths = [...new Set(rows.map(row => row.image_storage_path))];
    const { data, error } = await this.supabase.adminClient.storage.from(IMAGE_BUCKET).createSignedUrls(paths, 3600);
    if (error) throw new ServiceUnavailableException('Image previews are unavailable; check Supabase Storage');
    const urls = new Map((data ?? []).map(item => [item.path, item.signedUrl || null]));
    return rows.map(row => ({ ...row, image_url: urls.get(row.image_storage_path) ?? null }));
  }

  async withArtworkUrls<T extends { icon_storage_path?: string | null }>(rows: T[]) {
    const supplied = rows.filter(row => row.icon_storage_path);
    const signed = await this.withImageUrls(supplied.map(row => ({ image_storage_path: row.icon_storage_path! })));
    const urls = new Map(signed.map(row => [row.image_storage_path, row.image_url]));
    return rows.map(row => ({ ...row, icon_url: row.icon_storage_path ? urls.get(row.icon_storage_path) ?? null : null }));
  }

  async list() {
    const { data, error } = await this.supabase.adminClient.from('media_records').select('*')
      .eq('status', 'ready').order('created_at', { ascending: false }).limit(200);
    if (error) throw new InternalServerErrorException('Unable to load images');
    const rows = (data ?? []) as Array<Record<string, unknown> & { storage_path: string }>;
    return this.withImageUrls(rows.map(row => ({ ...row, image_storage_path: row.storage_path })));
  }
}
