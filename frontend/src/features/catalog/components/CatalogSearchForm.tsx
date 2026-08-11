import { useState } from 'react';

type CatalogSearchFormProps = {
  submittedQuery: string;
  onSubmit: (query: string) => void;
};

export function CatalogSearchForm({ submittedQuery, onSubmit }: CatalogSearchFormProps) {
  const [draft, setDraft] = useState(submittedQuery);

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit(draft.trim());
      }}
    >
      <label htmlFor="catalog-search">搜索鱼类</label>
      <div>
        <input
          id="catalog-search"
          type="search"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="名称、学名或别名"
        />
        <button type="submit">搜索</button>
      </div>
    </form>
  );
}
