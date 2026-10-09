'use client';

import React, { useEffect, useId, useRef, type ReactNode } from 'react';

export function ContentDialog({ title, busy, onClose, children }: {
  title: string; busy: boolean; onClose: () => void; children: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (typeof dialog.showModal === 'function') dialog.showModal();
    else dialog.setAttribute('open', '');
    return () => { if (typeof dialog.close === 'function') dialog.close(); };
  }, []);
  return <dialog ref={ref} className="content-dialog" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}
    onKeyDown={event => { if (event.key === 'Escape') { event.preventDefault(); if (!busy) onClose(); } }}>
    <header className="dialog-heading"><h2 id={titleId}>{title}</h2>
      <button type="button" className="quiet-button" disabled={busy} onClick={onClose} aria-label="Close form">✕</button></header>
    {children}
  </dialog>;
}
