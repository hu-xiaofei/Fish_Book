package com.fishbook.administration.photos.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fishbook.catchlog.application.*;
import com.fishbook.catchlog.domain.CatchRecordRepository;
import com.fishbook.media.domain.*;
import com.fishbook.support.MySqlTestConfiguration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties="fishbook.media.cleanup-delay=PT24H")
@Import(MySqlTestConfiguration.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class AdminPhotoConcurrencyTest {
    static final String ADMIN="race-admin@example.com", OWNER="race-owner@example.com";
    static final byte[] JPEG={(byte)255,(byte)216,(byte)255,1};
    @Autowired CatchPhotoApplicationService photos;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean CatchRecordRepository records;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MediaStore store;
    long id;
    @BeforeEach void setup() {
        dropCheck("admin_photo_operations", "fail_admin_audit");
        dropCheck("media_cleanup_jobs", "fail_photo_cleanup");
        jdbc.update("DELETE FROM admin_photo_operations"); jdbc.update("DELETE FROM media_cleanup_jobs"); jdbc.update("DELETE FROM catch_records");
        jdbc.update("DELETE FROM users WHERE id IN (9801,9802)");
        jdbc.update("INSERT INTO users(id,email,password_hash,nickname,role,status,created_at,updated_at) VALUES (9801,?,'hash','admin','ADMIN','ACTIVE',NOW(),NOW()),(9802,?,'hash','owner','USER','ACTIVE',NOW(),NOW())",ADMIN,OWNER);
        jdbc.update("INSERT INTO catch_records(user_id,fish_species_id,caught_on,location,notes,photo_object_key,created_at,updated_at) VALUES(9802,7,'2026-08-20','lake','private notes','catches/9802/old',NOW(),NOW())");
        id=jdbc.queryForObject("SELECT MAX(id) FROM catch_records",Long.class);
    }
    @AfterEach void cleanupTriggers() { dropCheck("admin_photo_operations", "fail_admin_audit"); dropCheck("media_cleanup_jobs", "fail_photo_cleanup"); }
    @Test void ownerReplacementWinsAgainstPausedAdminUploadAndOnlyLoserIsCompensated() throws Exception { race("replace",false); }
    @Test void ownerRemovalWinsAgainstPausedAdminUpload() throws Exception { race("remove",false); }
    @Test void deletedRecordIsAConflictAfterUploadAndLoserEnqueuesInFreshTransaction() throws Exception { race("record-delete",true); }
    private void race(String winner,boolean failCompensation) throws Exception {
        var uploaded=new CountDownLatch(1); var resume=new CountDownLatch(1); var loser=new AtomicReference<String>();
        doAnswer(call->{
            if (Thread.currentThread().getName().equals("paused-admin")) {
                loser.set(call.getArgument(0)); uploaded.countDown();
                if(!resume.await(15,TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            }
            return null;
        }).when(store).put(anyString(),any(byte[].class),anyString());
        if(failCompensation) doThrow(new MediaStorageUnavailableException()).when(store).delete(anyString());
        try(var executor=Executors.newSingleThreadExecutor(r->new Thread(r,"paused-admin"))) {
            Future<?> pending=executor.submit(()->photos.putForAdmin(ADMIN,id,JPEG,"image/jpeg",0));
            try {
                assertThat(uploaded.await(15,TimeUnit.SECONDS)).isTrue();
                if(winner.equals("replace")) photos.put(OWNER,id,JPEG,"image/jpeg",0);
                else if(winner.equals("remove")) photos.remove(OWNER,id,0);
                else tx.executeWithoutResult(s->{var current=records.findByIdAndUserId(id,9802).orElseThrow(); assertThat(records.deleteByIdAndUserId(id,9802,current.version())).isTrue();});
            } finally { resume.countDown(); }
            assertThatThrownBy(()->pending.get(15,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(CatchPhotoConflictException.class);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_photo_operations",Integer.class)).isZero();
        verify(store).delete(loser.get());
        if(winner.equals("replace")) {
            String winningKey=jdbc.queryForObject("SELECT photo_object_key FROM catch_records WHERE id=?",String.class,id);
            assertThat(winningKey).isNotEqualTo(loser.get()).isNotEqualTo("catches/9802/old");
            verify(store,never()).delete(winningKey);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_cleanup_jobs WHERE object_key=?",Integer.class,winningKey)).isZero();
        }
        if(failCompensation) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_cleanup_jobs WHERE object_key=? AND reason='UPLOAD_ROLLBACK'",Integer.class,loser.get())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_cleanup_jobs WHERE object_key='catches/9802/old'",Integer.class)).isZero();
        }
    }
    @Test void revokedAdminAfterUploadCannotCommitAndNewObjectIsCompensated() {
        doAnswer(call->{jdbc.update("UPDATE users SET role='USER' WHERE id=9801");return null;}).when(store).put(anyString(),any(byte[].class),anyString());
        assertThatThrownBy(()->photos.putForAdmin(ADMIN,id,JPEG,"image/jpeg",0)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertOriginalAndNoEvidence();
        verify(store).delete(argThat(key->!key.equals("catches/9802/old")));
    }
    @Test void disabledAdminAfterUploadCannotCommit() {
        doAnswer(call->{jdbc.update("UPDATE users SET status='DISABLED' WHERE id=9801");return null;}).when(store).put(anyString(),any(byte[].class),anyString());
        assertThatThrownBy(()->photos.putForAdmin(ADMIN,id,JPEG,"image/jpeg",0)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertOriginalAndNoEvidence();
        verify(store).delete(argThat(key->!key.equals("catches/9802/old")));
    }
    @Test void bothCompensationFailuresRetainOriginalAccessFailureAndDoNotTouchOldObject() {
        jdbc.execute("ALTER TABLE media_cleanup_jobs ADD CONSTRAINT fail_photo_cleanup CHECK(reason = 'REPLACED')");
        doAnswer(call->{jdbc.update("UPDATE users SET role='USER' WHERE id=9801");return null;}).when(store).put(anyString(),any(byte[].class),anyString());
        doThrow(new MediaStorageUnavailableException()).when(store).delete(anyString());
        assertThatThrownBy(()->photos.putForAdmin(ADMIN,id,JPEG,"image/jpeg",0)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertOriginalAndNoEvidence();
        verify(store,never()).delete("catches/9802/old");
    }
    @Test void actualAuditInsertFailureRollsBackPhotoAndLeavesNoSuccessEvidence() {
        jdbc.execute("ALTER TABLE admin_photo_operations ADD CONSTRAINT fail_admin_audit CHECK(actor_user_id < 0)");
        assertThatThrownBy(()->photos.putForAdmin(ADMIN,id,JPEG,"image/jpeg",0)).isInstanceOf(RuntimeException.class);
        assertOriginalAndNoEvidence();
        verify(store).delete(argThat(key->!key.equals("catches/9802/old")));
    }
    @Test void actualCleanupEnqueueFailureRollsBackPhotoAndAlreadyInsertedAudit() {
        jdbc.execute("ALTER TABLE media_cleanup_jobs ADD CONSTRAINT fail_photo_cleanup CHECK(reason = 'UPLOAD_ROLLBACK')");
        assertThatThrownBy(()->photos.putForAdmin(ADMIN,id,JPEG,"image/jpeg",0)).isInstanceOf(RuntimeException.class);
        assertOriginalAndNoEvidence();
        verify(store).delete(argThat(key->!key.equals("catches/9802/old")));
    }
    @Test void removeCleanupFailureRollsBackReferenceAndAudit() {
        jdbc.execute("ALTER TABLE media_cleanup_jobs ADD CONSTRAINT fail_photo_cleanup CHECK(reason = 'UPLOAD_ROLLBACK')");
        assertThatThrownBy(()->photos.removeForAdmin(ADMIN,id,0)).isInstanceOf(RuntimeException.class);
        assertOriginalAndNoEvidence(); verifyNoInteractions(store);
    }
    @Test void failedDatabaseInsertStillAllowsRollbackCleanupInFreshTransaction() {
        jdbc.execute("ALTER TABLE media_cleanup_jobs ADD CONSTRAINT fail_photo_cleanup CHECK(reason = 'UPLOAD_ROLLBACK')");
        doThrow(new MediaStorageUnavailableException()).when(store).delete(anyString());
        assertThatThrownBy(()->photos.putForAdmin(ADMIN,id,JPEG,"image/jpeg",0))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT photo_object_key FROM catch_records WHERE id=?",String.class,id)).isEqualTo("catches/9802/old");
        assertThat(jdbc.queryForObject("SELECT version FROM catch_records WHERE id=?",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_photo_operations",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_cleanup_jobs WHERE reason='UPLOAD_ROLLBACK' AND object_key <> 'catches/9802/old'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_cleanup_jobs",Integer.class)).isEqualTo(1);
    }
    @Test void optimisticFlushRaceAfterFinalComparisonRejectsAdminAndKeepsOwnerWinner() throws Exception {
        var beforeFlush=new CountDownLatch(1); var resume=new CountDownLatch(1);
        doAnswer(call->{
            if(Thread.currentThread().getName().equals("paused-flush")) {
                beforeFlush.countDown();
                if(!resume.await(15,TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            }
            return call.callRealMethod();
        }).when(records).save(any());
        try(var executor=Executors.newSingleThreadExecutor(r->new Thread(r,"paused-flush"))) {
            var pending=executor.submit(()->photos.putForAdmin(ADMIN,id,JPEG,"image/jpeg",0));
            try {
                assertThat(beforeFlush.await(15,TimeUnit.SECONDS)).isTrue();
                photos.put(OWNER,id,JPEG,"image/jpeg",0);
            } finally { resume.countDown(); }
            assertThatThrownBy(()->pending.get(15,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(CatchPhotoConflictException.class);
        }
        String winningKey=jdbc.queryForObject("SELECT photo_object_key FROM catch_records WHERE id=?",String.class,id);
        assertThat(winningKey).isNotEqualTo("catches/9802/old");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_photo_operations",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_cleanup_jobs",Integer.class)).isEqualTo(1);
        verify(store,never()).delete(winningKey);
        verify(store).delete(argThat(key->!key.equals(winningKey)&&!key.equals("catches/9802/old")));
    }
    @Test void adminReplacementWinsAgainstPausedOwnerUpload() throws Exception {
        var uploaded=new CountDownLatch(1); var resume=new CountDownLatch(1); var loser=new AtomicReference<String>();
        doAnswer(call->{
            if(Thread.currentThread().getName().equals("paused-owner")) {
                loser.set(call.getArgument(0)); uploaded.countDown();
                if(!resume.await(15,TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            }
            return null;
        }).when(store).put(anyString(),any(byte[].class),anyString());
        try(var executor=Executors.newSingleThreadExecutor(r->new Thread(r,"paused-owner"))) {
            var pending=executor.submit(()->photos.put(OWNER,id,JPEG,"image/jpeg",0));
            try { assertThat(uploaded.await(15,TimeUnit.SECONDS)).isTrue(); photos.putForAdmin(ADMIN,id,JPEG,"image/jpeg",0); }
            finally { resume.countDown(); }
            assertThatThrownBy(()->pending.get(15,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(CatchPhotoConflictException.class);
        }
        String winner=jdbc.queryForObject("SELECT photo_object_key FROM catch_records WHERE id=?",String.class,id);
        assertThat(winner).isNotEqualTo(loser.get()).isNotEqualTo("catches/9802/old");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_photo_operations WHERE operation='REPLACED'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_cleanup_jobs WHERE object_key='catches/9802/old'",Integer.class)).isEqualTo(1);
        verify(store).delete(loser.get()); verify(store,never()).delete(winner);
    }
    private void dropCheck(String table, String constraint) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_NAME=?", Integer.class, table, constraint) > 0)
            jdbc.execute("ALTER TABLE " + table + " DROP CHECK " + constraint);
    }
    private void assertOriginalAndNoEvidence() {
        assertThat(jdbc.queryForObject("SELECT photo_object_key FROM catch_records WHERE id=?",String.class,id)).isEqualTo("catches/9802/old");
        assertThat(jdbc.queryForObject("SELECT version FROM catch_records WHERE id=?",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_photo_operations",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_cleanup_jobs",Integer.class)).isZero();
    }
}
