import type { HabitatCode } from '../../catalog/model/types';

export type PublicationStatus = 'DRAFT' | 'PUBLISHED' | 'UNPUBLISHED';

export type AdminFishFilters = {
  q: string;
  status: PublicationStatus | '';
  page: number;
};

export type AdminFishSummary = {
  id: number;
  slug: string;
  commonNameZh: string;
  scientificName: string;
  status: PublicationStatus;
  updatedAt: string;
};

export type AdminFishPage = {
  items: AdminFishSummary[];
  page: number;
  size: 20;
  totalItems: number;
  totalPages: number;
};

export type AdminFishContentInput = {
  commonNameZh: string;
  scientificName: string;
  familyNameZh: string;
  familyScientificName: string;
  genusNameZh: string;
  genusScientificName: string;
  aliases: string[];
  habitats: HabitatCode[];
  appearance: string;
  sizeDescription: string;
  habitatDescription: string;
  distribution: string;
  description: string;
  imagePath: string;
  imageAltText: string;
  imageSourceUrl: string;
  imageAuthor: string;
  imageLicenseName: string;
  imageLicenseUrl: string;
  displayOrder: number;
};

export type AdminFishCreateInput = AdminFishContentInput & { slug: string };
export type AdminFishUpdateInput = AdminFishContentInput;

export type AdminFishDetail = AdminFishCreateInput & {
  id: number;
  status: PublicationStatus;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
};
