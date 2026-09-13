import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { adminPhotoOperationsQueryKey, fetchAdminPhotoOperations } from '../api/adminPhotoApi';
import { isAdminPhotoAccessError, useAdminPhotoSession } from '../model/useAdminPhotoAccess';
import styles from '../pages/AdminPhotoPages.module.css';
type Props = { recordId: number; onAccessError: (error: unknown) => void };
export function AdminPhotoOperations(props: Props) { return <Operations key={props.recordId} {...props} />; }
function Operations({ recordId, onAccessError }: Props) {
  const [page, setPage] = useState(0);
  const { sessionChanged } = useAdminPhotoSession();
  const operations = useQuery({ queryKey: adminPhotoOperationsQueryKey(recordId, page), queryFn: () => fetchAdminPhotoOperations(recordId, page), enabled: !sessionChanged, retry: false });
  useEffect(() => { if (isAdminPhotoAccessError(operations.error)) onAccessError(operations.error); }, [operations.error, onAccessError]);
  if (sessionChanged || isAdminPhotoAccessError(operations.error)) return null;
  return <section className={styles.message} aria-labelledby="photo-operations-title"><h2 id="photo-operations-title">近期管理员操作</h2>
    <p>成功操作的只读记录，不提供旧照片恢复。</p>
    {operations.isPending ? <p role="status">正在加载操作记录…</p> : null}
    {operations.isError ? <><p role="status">加载操作记录失败，请稍后重试</p><button onClick={() => { void operations.refetch(); }}>重试操作记录</button></> : null}
    {!operations.isError && operations.data?.items.length === 0 ? <p>暂无管理员操作记录</p> : null}
    {!operations.isError && operations.data && operations.data.items.length > 0 ? <>
      <div className={styles.tableWrapper}><table><caption>管理员照片操作记录</caption><thead><tr><th>操作者 ID</th><th>所属用户 ID</th><th>记录 ID</th><th>操作</th><th>修改前版本</th><th>时间</th></tr></thead><tbody>{operations.data.items.map((entry) => <tr key={entry.id}><td>{entry.actorUserId}</td><td>{entry.ownerUserId}</td><td>{entry.recordId}</td><td>{entry.operation === 'REPLACED' ? '替换照片' : '删除照片'}</td><td>{entry.previousRevision}</td><td>{entry.occurredAt}</td></tr>)}</tbody></table></div>
      {operations.data.totalPages > 1 ? <nav className={styles.navigation} aria-label="操作记录分页"><button disabled={page <= 0} onClick={() => setPage(page - 1)}>上一页操作</button><span>第 {page + 1} 页，共 {operations.data.totalPages} 页</span><button disabled={page >= operations.data.totalPages - 1} onClick={() => setPage(page + 1)}>下一页操作</button></nav> : null}
    </> : null}
  </section>;
}
