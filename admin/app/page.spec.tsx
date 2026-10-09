import React from 'react';
import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import Home from './page';

describe('Yapnix Studio dashboard', () => {
  it('renders the initial management shell', () => {
    const html = renderToStaticMarkup(<Home />);

    expect(html).toContain('Yapnix Studio');
    expect(html).toContain('Opening Yapnix Studio');
  });
});
