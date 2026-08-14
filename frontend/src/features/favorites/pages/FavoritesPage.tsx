import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, Navigate, useLocation, useSearchParams } from 'react-router-dom';
import { SessionNav } from '../../auth/components/SessionNav';
import { isConfirmedUnauthorized } from '../../auth/api/currentUser';
import { useFavoriteSessionExpiry } from '../../auth/hooks/useExpireSessionOnUnauthorized';
import { FavoriteButton } from '../components/FavoriteButton';
import {
  favoritePageQueryKey,
  favoriteStatusQueryRetry,
  fetchFavoritePage,
} from '../api/favoritesApi';
import type { FavoriteSummary } from '../model/types';
import styles from './FavoritesPage.module.css';

const favoriteDateFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: 'numeric',
  day: 'numeric',
  timeZone: 'Asia/Shanghai',
});

function parsePage(value: string | null) {
  if (value === null || !/^\d+$/.test(value)) return 0;
  const page = Number(value);
  return Number.isSafeInteger(page) ? page : 0;
}

function formatFavoriteDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;

  try {
    return favoriteDateFormatter.format(date);
  } catch {
    return null;
  }
}

function FavoriteCard({ fish, returnTo }: { fish: FavoriteSummary; returnTo: string }) {
  const [imageFailed, setImageFailed] = useState(false);
  const favoriteDate = formatFavoriteDate(fish.favoritedAt);

  return (
    <article>
      {imageFailed ? (
        <div role="img" aria-label={fish.imageAltText}>暂无鱼类图片</div>
      ) : (
        <img
          src={fish.imagePath}
          alt={fish.imageAltText}
          onError={() => setImageFailed(true)}
        />
      )}
      <div>
        <h2>{fish.commonNameZh}</h2>
        <p><i>{fish.scientificName}</i></p>
        <p>{fish.familyNameZh}</p>
        {fish.aliases.length > 0 ? <p>别名：{fish.aliases.join('、')}</p> : null}
        {fish.habitats.length > 0 ? (
          <p>栖息环境：{fish.habitats.map((item) => item.labelZh).join('、')}</p>
        ) : null}
        <p>{favoriteDate ? `收藏于 ${favoriteDate}` : '收藏时间未知'}</p>
        <FavoriteButton
          fishSlug={fish.slug}
          isFavorited
          returnTo={returnTo}
        />
        <Link to={`/fish/${encodeURIComponent(fish.slug)}`} state={{ from: returnTo }}>
          查看{fish.commonNameZh}详情
        </Link>
      </div>
    </article>
  );
}

export function FavoritesPage() {
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePage(searchParams.get('page'));
  const { sessionExpired, expireIfUnauthorized } = useFavoriteSessionExpiry();
  const favoritesQuery = useQuery({
    queryKey: favoritePageQueryKey(page),
    queryFn: () => fetchFavoritePage(page),
    enabled: !sessionExpired,
    retry: favoriteStatusQueryRetry,
  });
  useEffect(() => {
    expireIfUnauthorized(favoritesQuery.error);
  }, [expireIfUnauthorized, favoritesQuery.error]);
  const returnTo = `${location.pathname}${location.search}`;

  useEffect(() => {
    const result = favoritesQuery.data;
    if (
      !result
      || result.items.length > 0
      || result.totalItems === 0
      || page < result.totalPages
    ) {
      return;
    }

    const lastValidPage = Math.max(result.totalPages - 1, 0);
    setSearchParams(
      lastValidPage === 0 ? {} : { page: String(lastValidPage) },
      { replace: true },
    );
  }, [favoritesQuery.data, page, setSearchParams]);

  const changePage = (nextPage: number) => {
    if (nextPage <= 0) {
      setSearchParams({});
      return;
    }
    setSearchParams({ page: String(nextPage) });
  };

  if (sessionExpired || isConfirmedUnauthorized(favoritesQuery.error)) {
    return <Navigate to="/login" replace />;
  }

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1>我的收藏</h1>
          <p>把想认识、想遇见的鱼留在这里。</p>
        </div>
        <div className={styles.navigation}>
          <Link to="/">返回鱼图鉴</Link>
          <SessionNav />
        </div>
      </header>

      {favoritesQuery.isPending ? <p role="status">正在加载收藏…</p> : null}
      {favoritesQuery.isError ? (
        <section className={styles.message} aria-label="加载错误">
          <p role="status">加载收藏失败，请稍后重试</p>
          <button type="button" onClick={() => { void favoritesQuery.refetch(); }}>重试</button>
        </section>
      ) : null}
      {favoritesQuery.data && favoritesQuery.data.totalItems === 0 ? (
        <section className={styles.message}>
          <h2>还没有收藏鱼类</h2>
          <p>浏览鱼图鉴，收藏你感兴趣的鱼。</p>
          <Link to="/">去鱼图鉴看看</Link>
        </section>
      ) : null}
      {favoritesQuery.data && favoritesQuery.data.items.length > 0 ? (
        <>
          <section className={styles.cardGrid} aria-label="收藏的鱼类">
            {favoritesQuery.data.items.map((fish) => (
              <FavoriteCard key={fish.slug} fish={fish} returnTo={returnTo} />
            ))}
          </section>
          {favoritesQuery.data.totalPages > 1 ? (
            <div className={styles.pagination}>
              <nav aria-label="收藏分页">
                <button
                  type="button"
                  disabled={favoritesQuery.data.page <= 0}
                  onClick={() => changePage(favoritesQuery.data.page - 1)}
                >
                  上一页
                </button>
                <span aria-live="polite">
                  第 {favoritesQuery.data.page + 1} 页，共 {favoritesQuery.data.totalPages} 页
                </span>
                <button
                  type="button"
                  disabled={favoritesQuery.data.page >= favoritesQuery.data.totalPages - 1}
                  onClick={() => changePage(favoritesQuery.data.page + 1)}
                >
                  下一页
                </button>
              </nav>
            </div>
          ) : null}
        </>
      ) : null}
    </main>
  );
}
