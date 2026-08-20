export type CatchRecordInput = {
  fishSlug: string;
  caughtOn: string;
  location: string;
  lengthCm: number | null;
  weightG: number | null;
  method: string | null;
  notes: string | null;
};

export type CatchRecordSummary = Omit<CatchRecordInput, 'notes'> & {
  id: number;
  commonNameZh: string;
  hasPhoto: boolean;
  createdAt: string;
  updatedAt: string;
};

export type CatchRecordDetail = CatchRecordSummary & {
  notes: string | null;
};

export type CatchRecordPage = {
  items: CatchRecordSummary[];
  page: number;
  size: 20;
  totalItems: number;
  totalPages: number;
};

export type CatchRecordFormValues = {
  fishSlug: string;
  caughtOn: string;
  location: string;
  lengthCm: string | number | null | undefined;
  weightG: string | number | null | undefined;
  method: string;
  notes: string;
};
