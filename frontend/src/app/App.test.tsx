import { render, screen } from '@testing-library/react';
import { App } from './App';

test('renders the FishBook product identity', () => {
  render(<App />);
  expect(screen.getByRole('heading', { name: 'FishBook' })).toBeInTheDocument();
});
