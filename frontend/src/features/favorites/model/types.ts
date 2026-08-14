import type { FishSummary } from '../../catalog/model/types';

export type FavoriteStatus = {
  fishSlug: string;
  favorited: boolean;
};

export type FavoriteStatusResponse = {
  items: FavoriteStatus[];
};

export type FavoriteSummary = FishSummary & {
  favoritedAt: string;
};

export type FavoritePage = {
  items: FavoriteSummary[];
  page: number;
  size: 12;
  totalItems: number;
  totalPages: number;
};
