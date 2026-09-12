package com.fishbook.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fishbook.catchlog.application.CatchPhotoConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class GlobalExceptionHandlerPhotoConflictTest {
    @Test
    void hidesPersistenceDetailsBehindTheSameConflictResponseAsExplicitConflicts() {
        // Bug caught: a late database conflict could become a 500 or expose sensitive persistence details.
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("PUT", "/api/v1/catches/51/photo");
        for (RuntimeException failure : new RuntimeException[] {
                new CatchPhotoConflictException(),
                new ObjectOptimisticLockingFailureException("private-object-key", 51L)}) {
            var response = handler.handleCatchPhotoConflict(failure, request);
            assertThat(response.getStatusCode().value()).isEqualTo(409);
            assertThat(response.getBody().code()).isEqualTo("CATCH_PHOTO_CONFLICT");
            assertThat(response.getBody().message()).isEqualTo("照片或记录已被修改，请刷新后重新确认操作");
            assertThat(response.getBody().fieldErrors()).isEmpty();
        }
    }
}
