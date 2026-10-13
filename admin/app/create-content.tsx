'use client';
import React from 'react';
import { QuestionForm } from './question-form';
import type { ContentKind, ContentRow } from './content-editor';
export function CreateContent({token,busy,onBusyChange,onSave,onCancel,prompt}: {
  kind:ContentKind;token:string;busy:boolean;games:ContentRow[];gameId:string;prompt?:string;
  onBusyChange:(busy:boolean)=>void;onSave:(path:string,value:ContentRow)=>Promise<void>;onCancel:()=>void;
}) {
  return <QuestionForm token={token} busy={busy} prompt={prompt} onBusyChange={onBusyChange} onSave={value=>onSave('/admin/questions',value)} onCancel={onCancel}/>;
}
