import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/ApiError';
import {
  currentUserQueryConfig,
  fetchCurrentUser,
  hasUsableCurrentUser,
  isConfirmedUnauthorized,
} from '../../auth/api/currentUser';
import { useConfirmedUnauthorizedSession } from '../../auth/hooks/useConfirmedUnauthorizedSession';
import { useFavoriteSessionExpiry } from '../../auth/hooks/useExpireSessionOnUnauthorized';
import {
  favoriteStatusQueryKey,
  favoriteStatusQueryRetry,
  fetchFavoriteStatuses,
} from '../../favorites/api/favoritesApi';
import { FavoriteButton } from '../../favorites/components/FavoriteButton';
import { fetchFishDetail, fishDetailQueryKey } from '../api/catalogApi';
import styles from './FishDetailPage.module.css';

function catalogReturnPath(state: unknown): string {
  if (typeof state !== 'object' || state === null || !('from' in state)) return '/';

  const from = state.from;
  return typeof from === 'string' && (from === '/' || from.startsWith('/?')) ? from : '/';
}

function safeExternalUrl(value: string): string | undefined {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' || url.protocol === 'http:' ? value : undefined;
  } catch {
    return undefined;
  }
}

export function FishDetailPage() {
  const { slug } = useParams();
  const location = useLocation();
  const [imageFailed, setImageFailed] = useState(false);
  const detail = useQuery({
    queryKey: fishDetailQueryKey(slug ?? ''),
    queryFn: () => fetchFishDetail(slug ?? ''),
    enabled: Boolean(slug),
  });
  const currentUser = useQuery({
    ...currentUserQueryConfig,
    queryFn: fetchCurrentUser,
  });
  const currentUserUnauthorized = useConfirmedUnauthorizedSession(currentUser.error);
  const { sessionExpired, expireIfUnauthorized } = useFavoriteSessionExpiry();
  const hasAuthenticatedSession = !sessionExpired && hasUsableCurrentUser(
    currentUser.data,
    currentUser.error,
  );
  const favoriteStatuses = useQuery({
    queryKey: favoriteStatusQueryKey(slug ? [slug] : []),
    queryFn: () => fetchFavoriteStatuses(slug ? [slug] : []),
    enabled: Boolean(hasAuthenticatedSession && detail.data && slug),
    retry: favoriteStatusQueryRetry,
  });
  useEffect(() => {
    expireIfUnauthorized(favoriteStatuses.error);
  }, [expireIfUnauthorized, favoriteStatuses.error]);
  const returnPath = catalogReturnPath(location.state);
  const isMissingFish = detail.error instanceof ApiError
    && detail.error.status === 404
    && detail.error.body.code === 'FISH_NOT_FOUND';

  if (!slug || isMissingFish) {
    return (
      <main className={styles.page}>
        <section className={styles.message} aria-live="polite">
          <h1>没有找到这种鱼</h1>
          <Link to={returnPath}>返回图鉴</Link>
        </section>
      </main>
    );
  }

  if (detail.isPending) {
    return (
      <main className={styles.page}>
        <p role="status">正在加载鱼类资料…</p>
      </main>
    );
  }

  if (detail.isError || !detail.data) {
    return (
      <main className={styles.page}>
        <section className={styles.message} aria-label="加载错误">
          <p role="status">加载鱼类资料失败，请稍后重试</p>
          <button type="button" onClick={() => { void detail.refetch(); }}>重试</button>
          <Link to={returnPath}>返回图鉴</Link>
        </section>
      </main>
    );
  }

  const fish = detail.data;
  const sourceUrl = safeExternalUrl(fish.image.sourceUrl);
  const licenseUrl = safeExternalUrl(fish.image.licenseUrl);
  const isFavorited = hasAuthenticatedSession && favoriteStatuses.isSuccess
    ? favoriteStatuses.data?.items.find(
      (status) => status.fishSlug === fish.slug,
    )?.favorited
    : sessionExpired || currentUserUnauthorized ? false : undefined;
  const favoriteStatusError = hasAuthenticatedSession
    && favoriteStatuses.isError
    && !isConfirmedUnauthorized(favoriteStatuses.error);
  const currentPath = `${location.pathname}${location.search}`;

  return (
    <main className={styles.page}>
      <nav className={styles.navigation} aria-label="鱼类详情导航">
        <Link to={returnPath}>返回图鉴</Link>
      </nav>
      <article className={styles.detail}>
        <header className={styles.header}>
          <h1>{fish.commonNameZh}</h1>
          <p><i>{fish.scientificName}</i></p>
          {hasAuthenticatedSession && favoriteStatuses.isPending ? (
            <p role="status" aria-label="正在加载收藏状态">正在加载收藏状态…</p>
          ) : null}
          {favoriteStatusError ? (
            <section className={styles.message} aria-label="收藏状态错误">
              <p role="status" aria-label="收藏状态加载失败">
                加载收藏状态失败，请稍后重试
              </p>
              <button
                type="button"
                onClick={() => { void favoriteStatuses.refetch(); }}
              >
                重试收藏状态
              </button>
            </section>
          ) : null}
          {isFavorited === undefined ? null : (
            <FavoriteButton
              fishSlug={fish.slug}
              isFavorited={isFavorited}
              returnTo={currentPath}
            />
          )}
        </header>

        <div className={styles.layout}>
          <figure className={styles.figure}>
            {imageFailed ? (
              <div className={styles.imageFallback} role="img" aria-label={fish.image.altText}>
                暂无鱼类图片
              </div>
            ) : (
              <img
                src={fish.image.path}
                alt={fish.image.altText}
                onError={() => setImageFailed(true)}
              />
            )}
            <figcaption className={styles.attribution}>
              {sourceUrl ? (
                <a href={sourceUrl} target="_blank" rel="noopener noreferrer">图片来源</a>
              ) : <span>图片来源</span>}
              <span> · 作者：{fish.image.author} · </span>
              {licenseUrl ? (
                <a href={licenseUrl} target="_blank" rel="noopener noreferrer">许可证：{fish.image.licenseName}</a>
              ) : <span>许可证：{fish.image.licenseName}</span>}
            </figcaption>
          </figure>

          <div className={styles.content}>
            <section aria-labelledby="classification-heading">
              <h2 id="classification-heading">分类信息</h2>
              <dl className={styles.taxonomy}>
                <div>
                  <dt>科</dt>
                  <dd>{fish.familyNameZh}（<i>{fish.familyScientificName}</i>）</dd>
                </div>
                <div>
                  <dt>属</dt>
                  <dd>{fish.genusNameZh}（<i>{fish.genusScientificName}</i>）</dd>
                </div>
                <div>
                  <dt>别名</dt>
                  <dd>{fish.aliases.length > 0 ? fish.aliases.join('、') : '暂无'}</dd>
                </div>
                <div>
                  <dt>栖息环境</dt>
                  <dd>{fish.habitats.length > 0 ? fish.habitats.map((item) => item.labelZh).join('、') : '暂无'}</dd>
                </div>
              </dl>
            </section>

            <section aria-labelledby="description-heading">
              <h2 id="description-heading">鱼类资料</h2>
              <dl className={styles.facts}>
                <div>
                  <dt>外观</dt>
                  <dd>{fish.appearance}</dd>
                </div>
                <div>
                  <dt>体型</dt>
                  <dd>{fish.sizeDescription}</dd>
                </div>
                <div>
                  <dt>栖息地</dt>
                  <dd>{fish.habitatDescription}</dd>
                </div>
                <div>
                  <dt>分布</dt>
                  <dd>{fish.distribution}</dd>
                </div>
                <div>
                  <dt>简介</dt>
                  <dd>{fish.description}</dd>
                </div>
              </dl>
            </section>
          </div>
        </div>
      </article>
    </main>
  );
}
