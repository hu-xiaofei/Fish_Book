type CatalogPaginationProps = {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
};

export function CatalogPagination({ page, totalPages, onPageChange }: CatalogPaginationProps) {
  if (totalPages <= 1) return null;

  return (
    <nav aria-label="图鉴分页">
      <button type="button" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>上一页</button>
      <span aria-live="polite">第 {page + 1} 页，共 {totalPages} 页</span>
      <button
        type="button"
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
      >
        下一页
      </button>
    </nav>
  );
}
