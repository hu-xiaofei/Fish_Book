import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { AppProviders } from './app/providers';

const rootElement = document.getElementById('root');

if (!rootElement) {
  throw new Error('FishBook root element is missing');
}

createRoot(rootElement).render(
  <StrictMode>
    <AppProviders />
  </StrictMode>,
);
