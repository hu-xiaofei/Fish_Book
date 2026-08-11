import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/ApiError';
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

  return (
    <main className={styles.page}>
      <nav className={styles.navigation} aria-label="鱼类详情导航">
        <Link to={returnPath}>返回图鉴</Link>
      </nav>
      <article className={styles.detail}>
        <header className={styles.header}>
          <h1>{fish.commonNameZh}</h1>
          <p><i>{fish.scientificName}</i></p>
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
