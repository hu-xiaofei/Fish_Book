export type FavoriteStatus = {
  fishSlug: string;
  favorited: boolean;
};

export type FavoriteStatusResponse = {
  items: FavoriteStatus[];
};
