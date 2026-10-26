'use client';
import React, { useRef, useState } from 'react';
import { apiRequest } from './api';
export interface MediaImage { storage_path: string; image_url: string | null; original_filename: string; width?: number; height?: number }
export function ImagePicker({token,value,imageUrl,filename,disabled,onChange,onClear,onBusyChange,square = false}: {
  square?:boolean;token:string;value:string;imageUrl?:string|null;filename?:string;disabled:boolean;
  onChange:(image:MediaImage)=>void;onClear?:()=>void;onBusyChange:(busy:boolean)=>void;
}) {
  const [images,setImages]=useState<MediaImage[]>([]);
  const [showLibrary,setShowLibrary]=useState(false);
  const [search,setSearch]=useState('');
  const [error,setError]=useState('');
  const [working,setWorking]=useState(false);
  const input=useRef<HTMLInputElement>(null);
  async function upload(file?:File) {
    if(!file || disabled || working)return;
    setError('');
    if(!['image/jpeg','image/png','image/webp'].includes(file.type)||file.size>5*1024*1024){setError('Choose a JPEG, PNG or WebP image, 5 MB or smaller.');return;}
    setWorking(true);onBusyChange(true);
    try {const body=new FormData();body.set('file',file);const next=await apiRequest<MediaImage>(square ? '/admin/media/artwork' : '/admin/media',token,{method:'POST',body});onChange(next);setImages(current=>[next,...current.filter(item=>item.storage_path!==next.storage_path)]);setShowLibrary(false);}
    catch(failure){setError(failure instanceof Error?failure.message:'Upload failed');}
    finally{setWorking(false);onBusyChange(false);}
  }
  async function browse(){setWorking(true);setError('');onBusyChange(true);try{setImages(await apiRequest<MediaImage[]>('/admin/media',token));setShowLibrary(true);}catch(failure){setError(failure instanceof Error?failure.message:'Could not load images');}finally{setWorking(false);onBusyChange(false);}}
  const filtered=images.filter(image=>(!square || (image.width != null && image.width === image.height)) && image.original_filename.toLowerCase().includes(search.toLowerCase()));
  return <div className={square ? "image-picker square-artwork" : "image-picker"}>
    {square && <p className="small-note">4:4 square icon · uploads are centre-cropped to 512 × 512. The library shows square images.</p>}
    <input ref={input} className="visually-hidden" aria-label={square ? "Upload square artwork" : "Upload question image"} type="file" accept="image/jpeg,image/png,image/webp" disabled={disabled||working} onChange={event=>{const file=event.currentTarget.files?.[0];event.currentTarget.value='';void upload(file);}}/>
    {value ? <div className="picked-image">{imageUrl ? <img className="image-preview" src={imageUrl} alt={square ? "Selected artwork" : "Selected question"}/> : <p className="muted">Preview unavailable</p>}<div><strong>{filename||'Selected image'}</strong>{onClear && <button type="button" className="text-action danger-text" disabled={disabled||working} onClick={()=>{onClear();setError('');}}>Remove selected image</button>}</div><p className="small-note">Removing this selection keeps the uploaded file in your library.</p></div>
      : <button type="button" className="upload-dropzone" disabled={disabled||working} onClick={()=>input.current?.click()} onDragOver={event=>event.preventDefault()} onDrop={event=>{event.preventDefault();void upload(event.dataTransfer.files[0]);}}><span className="upload-symbol">＋</span><strong>Upload a picture</strong><span>Choose a file or drop it here</span><small>JPG, PNG or WebP · up to 5 MB</small></button>}
    <div className="image-source-actions"><button type="button" className="quiet-button" disabled={disabled||working} onClick={()=>void browse()}>Browse uploaded images</button>{value && <button type="button" className="quiet-button" disabled={disabled||working} onClick={()=>input.current?.click()}>Replace image</button>}</div>
    {working && <p className="inline-loading" role="status"><span className="spinner"/>Loading image…</p>}
    {error && <p className="notice" role="alert">{error}</p>}
    <input name="image_storage_path" type="hidden" value={value}/>
    {showLibrary && <section className="media-selector" aria-label="Uploaded images"><div className="section-heading"><strong>Your uploaded pictures</strong><button type="button" className="quiet-button" onClick={()=>setShowLibrary(false)}>Close library</button></div><label>Find uploaded image<input type="search" value={search} onChange={event=>setSearch(event.target.value)} placeholder="Search by filename"/></label>
      <div className="image-library">{!filtered.length && <p className="muted">{images.length?'No matching files.':'No images yet. Upload your first picture.'}</p>}{filtered.map(image=><button type="button" key={image.storage_path} disabled={disabled||working||!image.image_url} className={value===image.storage_path?'image-card selected':'image-card'} onClick={()=>{onChange(image);setShowLibrary(false);}}>{image.image_url?<img src={image.image_url} alt={image.original_filename}/>:<span>Preview unavailable</span>}<span>{image.original_filename}</span></button>)}</div>
    </section>}
  </div>;
}
