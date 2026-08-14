import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router-dom';
import {
  currentUserQueryConfig,
  fetchCurrentUser,
} from '../../auth/api/currentUser';
import {
  favoriteStatusQueryKey,
  fetchFavoriteStatuses,
} from '../../favorites/api/favoritesApi';
import {
  fetchFishFilterOptions,
  fetchFishPage,
  fishFilterOptionsQueryKey,
  fishListQueryKey,
} from '../api/catalogApi';
import { CatalogFilters } from '../components/CatalogFilters';
import { CatalogPagination } from '../components/CatalogPagination';
import { CatalogSearchForm } from '../components/CatalogSearchForm';
import { FishCard } from '../components/FishCard';
import { parseCatalogSearchParams, toCatalogSearchParams } from '../model/catalogSearchParams';
import type { CatalogFilters as CatalogFiltersValue, HabitatCode } from '../model/types';
import styles from './FishCatalogPage.module.css';

const emptyFilters: CatalogFiltersValue = { q: '', family: '', habitat: '', page: 0 };

export function FishCatalogPage() {
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = parseCatalogSearchParams(searchParams);
  const latestFilters = useRef(filters);
  useEffect(() => {
    latestFilters.current = filters;
  }, [filters]);
  const fishQuery = useQuery({
    queryKey: fishListQueryKey(filters),
    queryFn: () => fetchFishPage(filters),
  });
  const currentUser = useQuery({
    ...currentUserQueryConfig,
    queryFn: fetchCurrentUser,
  });
  const visibleFishSlugs = fishQuery.data?.items.map((fish) => fish.slug) ?? [];
  const favoriteStatuses = useQuery({
    queryKey: favoriteStatusQueryKey(visibleFishSlugs),
    queryFn: () => fetchFavoriteStatuses(visibleFishSlugs),
    enabled: Boolean(currentUser.data && fishQuery.data && visibleFishSlugs.length > 0),
  });
  const filterQuery = useQuery({
    queryKey: fishFilterOptionsQueryKey,
    queryFn: fetchFishFilterOptions,
  });
  const options = filterQuery.data ?? { families: [], habitats: [] };
  const from = `${location.pathname}${location.search}`;
  const favoriteBySlug = new Map(
    favoriteStatuses.data?.items.map((status) => [status.fishSlug, status.favorited]),
  );

  const updateFilters = (update: (current: CatalogFiltersValue) => CatalogFiltersValue) => {
    const next = update(latestFilters.current);
    latestFilters.current = next;
    setSearchParams(toCatalogSearchParams(next));
  };

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1>FishBook</h1>
          <p>记录渔获，认识鱼类。</p>
        </div>
        <nav aria-label="主要导航" className={styles.nav}>
          <Link to="/">首页</Link>
          <Link to="/login">登录</Link>
          <Link to="/register">注册</Link>
          <Link to="/profile">个人资料</Link>
        </nav>
      </header>

      <section className={styles.controls} aria-label="鱼类检索">
        <CatalogSearchForm
          key={filters.q}
          submittedQuery={filters.q}
          onSubmit={(q) => updateFilters((current) => ({ ...current, q, page: 0 }))}
        />
        <div className={styles.filterGrid}>
          <CatalogFilters
            families={options.families}
            habitats={options.habitats}
            family={filters.family}
            habitat={filters.habitat}
            onFamilyChange={(family) => updateFilters((current) => ({ ...current, family, page: 0 }))}
            onHabitatChange={(habitat: HabitatCode | '') => updateFilters((current) => ({ ...current, habitat, page: 0 }))}
            onClear={() => updateFilters(() => emptyFilters)}
          />
        </div>
      </section>

      {fishQuery.isPending ? <p role="status">正在加载鱼类…</p> : null}
      {fishQuery.isError ? (
        <section className={styles.message} aria-label="加载错误">
          <p role="status">加载鱼类失败，请稍后重试</p>
          <button type="button" onClick={() => { void fishQuery.refetch(); }}>重试</button>
        </section>
      ) : null}
      {fishQuery.data && fishQuery.data.items.length === 0 ? (
        <section className={styles.message}>
          <h2>没有找到匹配的鱼类</h2>
        </section>
      ) : null}
      {fishQuery.data && fishQuery.data.items.length > 0 ? (
        <>
          <section className={styles.cardGrid} aria-label="鱼类图鉴">
            {fishQuery.data.items.map((fish) => (
              <FishCard
                key={fish.slug}
                fish={fish}
                from={from}
                isFavorited={favoriteBySlug.get(fish.slug) ?? false}
              />
            ))}
          </section>
          <div className={styles.pagination}>
            <CatalogPagination
              page={fishQuery.data.page}
              totalPages={fishQuery.data.totalPages}
              onPageChange={(page) => updateFilters((current) => ({ ...current, page }))}
            />
          </div>
        </>
      ) : null}
    </main>
  );
}
