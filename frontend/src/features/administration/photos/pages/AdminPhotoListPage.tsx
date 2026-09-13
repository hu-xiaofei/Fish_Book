import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, Navigate, useSearchParams } from 'react-router-dom';
import { SessionNav } from '../../../auth/components/SessionNav';
import { adminPhotoPageQueryKey, fetchAdminPhotoPage } from '../api/adminPhotoApi';
import { parseAdminPhotoSearchParams, toAdminPhotoSearchParams } from '../model/adminPhotoSearchParams';
import { isAdminPhotoAccessError, useAdminPhotoAccess } from '../model/useAdminPhotoAccess';
import styles from './AdminPhotoPages.module.css';

export function AdminPhotoListPage() {
  const { sessionEnded, forbidden, onAccessError } = useAdminPhotoAccess();
  if (sessionEnded) return <Navigate to="/login" replace />;
  if (forbidden) return <main className={styles.page}><h1>没有管理员权限</h1></main>;
  return <PhotoList onAccessError={onAccessError} />;
}
function PhotoList({ onAccessError }: { onAccessError: (error: unknown) => void }) {
  const [params, setParams] = useSearchParams();
  const filters = parseAdminPhotoSearchParams(params);
  const [filterError, setFilterError] = useState(false);
  const list = useQuery({
    queryKey: filters ? adminPhotoPageQueryKey(filters) : ['admin-photos', 'invalid'],
    queryFn: () => fetchAdminPhotoPage(filters!), enabled: !!filters, retry: false,
  });
  useEffect(() => { onAccessError(list.error); }, [list.error, onAccessError]);
  if (isAdminPhotoAccessError(list.error)) return null;
  return <main className={styles.page}>
    <header className={styles.header}><div><h1>照片管理</h1><p>查看和管理所有用户的私有钓获照片。</p></div><div className={styles.navigation}><Link to="/">返回首页</Link><SessionNav /></div></header>
    <section className={styles.controls} aria-label="照片管理筛选"><form onSubmit={(event) => {
      event.preventDefault();
      const userId = String(new FormData(event.currentTarget).get('userId') ?? '').trim();
      const proposed = toAdminPhotoSearchParams({ userId, page: 0, size: 20 });
      if (!parseAdminPhotoSearchParams(proposed)) { setFilterError(true); return; }
      setFilterError(false); setParams(proposed);
    }}><label htmlFor="admin-photo-user">所属用户 ID</label><input key={params.get('userId') ?? ''} id="admin-photo-user" name="userId" inputMode="numeric" defaultValue={params.get('userId') ?? ''} /><button type="submit">筛选</button></form></section>
    {!filters || filterError ? <p role="status">筛选参数无效，请输入正整数用户 ID 和有效页码</p> : null}
    {filters && list.isPending ? <p role="status">正在加载照片管理列表…</p> : null}
    {list.isError ? <section className={styles.message}><p role="status">加载照片管理列表失败，请稍后重试</p><button onClick={() => { void list.refetch(); }}>重试</button></section> : null}
    {!filterError && !list.isError && list.data?.items.length === 0 ? <p className={styles.message}>暂无照片</p> : null}
    {!filterError && !list.isError && list.data && list.data.items.length > 0 ? <>
      <div className={styles.tableWrapper}>
        <table>
          <caption>照片管理列表</caption>
          <thead><tr><th scope="col">记录 ID</th><th scope="col">用户 ID</th><th scope="col">用户昵称</th><th scope="col">鱼类</th><th scope="col">钓获日期</th><th scope="col">照片状态</th><th scope="col">版本</th><th scope="col">更新时间</th><th scope="col">操作</th></tr></thead>
          <tbody>{list.data.items.map((photo) => (
            <tr key={photo.recordId}>
              <th scope="row">{photo.recordId}</th>
              <td>{photo.ownerUserId}</td><td>{photo.ownerNickname}</td><td>{photo.commonNameZh}</td><td>{photo.caughtOn}</td>
              <td>{photo.hasPhoto ? '有照片' : '暂无照片'}</td><td>{photo.revision}</td><td>{photo.updatedAt}</td>
              <td><Link to={`/admin/photos/${photo.recordId}`}>查看照片 {photo.recordId}</Link></td>
            </tr>
          ))}</tbody>
        </table>
      </div>
      {filters && list.data.totalPages > 1 ? <nav className={styles.navigation} aria-label="照片管理分页"><button disabled={list.data.page <= 0} onClick={() => setParams(toAdminPhotoSearchParams({ ...filters, page: list.data!.page - 1 }))}>上一页</button><span>第 {list.data.page + 1} 页，共 {list.data.totalPages} 页</span><button disabled={list.data.page >= list.data.totalPages - 1} onClick={() => setParams(toAdminPhotoSearchParams({ ...filters, page: list.data!.page + 1 }))}>下一页</button></nav> : null}
    </> : null}
  </main>;
}
