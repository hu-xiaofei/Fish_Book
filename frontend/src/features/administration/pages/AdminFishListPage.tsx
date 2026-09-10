import { useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Link, Navigate, useSearchParams } from 'react-router-dom';
import { isConfirmedUnauthorized } from '../../auth/api/currentUser';
import { SessionNav } from '../../auth/components/SessionNav';
import { useSessionExpiry } from '../../auth/hooks/useExpireSessionOnUnauthorized';
import { adminFishPageQueryKey, fetchAdminFishPage } from '../api/adminFishApi';
import { parseAdminFishSearchParams, toAdminFishSearchParams } from '../model/adminFishSearchParams';
import type { AdminFishFilters, PublicationStatus } from '../model/types';
import styles from './AdminFishPages.module.css';

const statusLabels: Record<PublicationStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  UNPUBLISHED: '已下架',
};

function updateSearchParams(
  setSearchParams: ReturnType<typeof useSearchParams>[1],
  filters: AdminFishFilters,
) {
  setSearchParams(toAdminFishSearchParams(filters));
}

export function AdminFishListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = parseAdminFishSearchParams(searchParams);
  const { sessionExpired, expireIfUnauthorized } = useSessionExpiry();
  const fishQuery = useQuery({
    queryKey: adminFishPageQueryKey(filters),
    queryFn: () => fetchAdminFishPage(filters),
    enabled: !sessionExpired,
    retry: (failureCount, error) => !isConfirmedUnauthorized(error) && failureCount < 2,
  });

  useEffect(() => {
    expireIfUnauthorized(fishQuery.error);
  }, [expireIfUnauthorized, fishQuery.error]);

  if (sessionExpired || isConfirmedUnauthorized(fishQuery.error)) {
    return <Navigate to="/login" replace />;
  }

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1>图鉴管理</h1>
          <p>维护鱼类内容及其发布状态。</p>
        </div>
        <div className={styles.navigation}>
          <Link to="/">返回鱼图鉴</Link>
          <Link to="/admin/fishes/new">新建鱼类</Link>
          <SessionNav />
        </div>
      </header>

      <section className={styles.controls} aria-label="图鉴管理筛选">
        <form
          onSubmit={(event) => {
            event.preventDefault();
            const formData = new FormData(event.currentTarget);
            updateSearchParams(setSearchParams, {
              ...filters,
              q: String(formData.get('q') ?? ''),
              page: 0,
            });
          }}
        >
          <label htmlFor="admin-fish-search">搜索鱼类</label>
          <div>
            <input
              key={filters.q}
              id="admin-fish-search"
              name="q"
              type="search"
              defaultValue={filters.q}
              placeholder="名称或学名"
            />
            <button type="submit">搜索</button>
          </div>
        </form>
        <label htmlFor="admin-fish-status">发布状态</label>
        <select
          id="admin-fish-status"
          value={filters.status}
          onChange={(event) => updateSearchParams(setSearchParams, {
            ...filters,
            status: event.target.value as PublicationStatus | '',
            page: 0,
          })}
        >
          <option value="">全部状态</option>
          <option value="DRAFT">草稿</option>
          <option value="PUBLISHED">已发布</option>
          <option value="UNPUBLISHED">已下架</option>
        </select>
      </section>

      {fishQuery.isPending ? <p role="status">正在加载图鉴管理列表…</p> : null}
      {fishQuery.isError ? (
        <section className={styles.message} aria-label="加载错误">
          <p role="status">加载图鉴管理列表失败，请稍后重试</p>
          <button type="button" onClick={() => { void fishQuery.refetch(); }}>重试</button>
        </section>
      ) : null}
      {fishQuery.data?.totalItems === 0 ? (
        <section className={styles.message}>
          <h2>还没有管理中的鱼类</h2>
          <p>新建一条鱼类内容，开始维护图鉴。</p>
          <Link to="/admin/fishes/new">新建鱼类</Link>
        </section>
      ) : null}
      {fishQuery.data && fishQuery.data.items.length > 0 ? (
        <>
          <div className={styles.tableWrapper}>
            <table>
              <caption>图鉴管理列表</caption>
              <thead>
                <tr>
                  <th scope="col">中文名</th>
                  <th scope="col">学名</th>
                  <th scope="col">状态</th>
                  <th scope="col">更新时间</th>
                  <th scope="col">操作</th>
                </tr>
              </thead>
              <tbody>
                {fishQuery.data.items.map((fish) => (
                  <tr key={fish.id}>
                    <th scope="row">{fish.commonNameZh}</th>
                    <td><i>{fish.scientificName}</i></td>
                    <td>{statusLabels[fish.status]}</td>
                    <td>{fish.updatedAt}</td>
                    <td><Link to={`/admin/fishes/${fish.id}/edit`}>编辑{fish.commonNameZh}</Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {fishQuery.data.totalPages > 1 ? (
            <div className={styles.pagination}>
              <nav aria-label="图鉴管理分页">
                <button
                  type="button"
                  disabled={fishQuery.data.page <= 0}
                  onClick={() => updateSearchParams(setSearchParams, {
                    ...filters,
                    page: Math.max(fishQuery.data.page - 1, 0),
                  })}
                >
                  上一页
                </button>
                <span aria-live="polite">第 {fishQuery.data.page + 1} 页，共 {fishQuery.data.totalPages} 页</span>
                <button
                  type="button"
                  disabled={fishQuery.data.page >= fishQuery.data.totalPages - 1}
                  onClick={() => updateSearchParams(setSearchParams, {
                    ...filters,
                    page: fishQuery.data.page + 1,
                  })}
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
