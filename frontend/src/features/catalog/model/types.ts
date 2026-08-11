export type HabitatCode = 'RIVER' | 'LAKE' | 'RESERVOIR' | 'POND' | 'STREAM';

export type HabitatOption = { code: HabitatCode; labelZh: string };

export type FishImageAttribution = {
  path: string;
  altText: string;
  sourceUrl: string;
  author: string;
  licenseName: string;
  licenseUrl: string;
};

export type FishSummary = {
  slug: string;
  commonNameZh: string;
  scientificName: string;
  familyNameZh: string;
  aliases: string[];
  habitats: HabitatOption[];
  imagePath: string;
  imageAltText: string;
};

export type FishPage = {
  items: FishSummary[];
  page: number;
  size: 12;
  totalItems: number;
  totalPages: number;
};

export type FishDetail = {
  slug: string;
  commonNameZh: string;
  scientificName: string;
  familyNameZh: string;
  familyScientificName: string;
  genusNameZh: string;
  genusScientificName: string;
  aliases: string[];
  habitats: HabitatOption[];
  appearance: string;
  sizeDescription: string;
  habitatDescription: string;
  distribution: string;
  description: string;
  image: FishImageAttribution;
};

export type FishFilterOptions = {
  families: string[];
  habitats: HabitatOption[];
};

export type CatalogFilters = {
  q: string;
  family: string;
  habitat: HabitatCode | '';
  page: number;
};
