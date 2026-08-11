/// <reference types="node" />

import { existsSync, statSync } from 'node:fs';
import { resolve } from 'node:path';
import { expect, test } from 'vitest';

const slugs = [
  'carassius-auratus', 'cyprinus-carpio', 'ctenopharyngodon-idella',
  'mylopharyngodon-piceus', 'hypophthalmichthys-molitrix',
  'hypophthalmichthys-nobilis', 'channa-argus', 'siniperca-chuatsi',
  'tachysurus-fulvidraco', 'megalobrama-amblycephala',
  'culter-alburnus', 'misgurnus-anguillicaudatus',
] as const;

test('ships one non-empty local image for each seeded fish', () => {
  for (const slug of slugs) {
    const path = resolve(process.cwd(), 'public/images/fish', `${slug}.jpg`);
    expect(existsSync(path), path).toBe(true);
    expect(statSync(path).size, path).toBeGreaterThan(10_000);
  }
});
