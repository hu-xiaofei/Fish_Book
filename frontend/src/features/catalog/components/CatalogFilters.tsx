import type { HabitatCode, HabitatOption } from '../model/types';

type CatalogFiltersProps = {
  families: string[];
  habitats: HabitatOption[];
  family: string;
  habitat: HabitatCode | '';
  onFamilyChange: (family: string) => void;
  onHabitatChange: (habitat: HabitatCode | '') => void;
  onClear: () => void;
};

export function CatalogFilters({
  families,
  habitats,
  family,
  habitat,
  onFamilyChange,
  onHabitatChange,
  onClear,
}: CatalogFiltersProps) {
  return (
    <section aria-label="筛选鱼类">
      <div>
        <label htmlFor="catalog-family">科属</label>
        <select
          id="catalog-family"
          value={family}
          onChange={(event) => onFamilyChange(event.target.value)}
        >
          <option value="">全部科属</option>
          {families.map((item) => <option key={item} value={item}>{item}</option>)}
        </select>
      </div>

      <div>
        <label htmlFor="catalog-habitat">栖息环境</label>
        <select
          id="catalog-habitat"
          value={habitat}
          onChange={(event) => onHabitatChange(event.target.value as HabitatCode | '')}
        >
          <option value="">全部栖息环境</option>
          {habitats.map((item) => <option key={item.code} value={item.code}>{item.labelZh}</option>)}
        </select>
      </div>

      <button type="button" onClick={onClear}>清除筛选</button>
    </section>
  );
}
