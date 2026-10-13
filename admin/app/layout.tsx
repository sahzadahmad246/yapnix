import React from 'react';
import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Yapnix Studio',
  description: 'Yapnix content administration dashboard',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      {/* Browser extensions may add attributes directly to body before React hydrates. */}
      <body suppressHydrationWarning>{children}</body>
    </html>
  );
}
