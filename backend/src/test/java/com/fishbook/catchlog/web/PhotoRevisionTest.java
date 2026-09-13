package com.fishbook.catchlog.web;

import static org.assertj.core.api.Assertions.*;
import com.fishbook.catchlog.application.*;
import org.junit.jupiter.api.Test;

class PhotoRevisionTest {
    @Test void acceptsOnlySingleStrongNonnegativeDecimalWithinLongRange() {
        assertThat(PhotoRevision.parse("\"0\"")).isZero();
        assertThat(PhotoRevision.parse("\"9223372036854775807\"")).isEqualTo(Long.MAX_VALUE);
        for(String input:new String[]{"", "0", "*", "W/\"1\"", "\"-1\"", "\"+1\"", "\"1\",\"2\"", " \"1\"", "\"1.0\"", "\"9223372036854775808\""})
            assertThatThrownBy(()->PhotoRevision.parse(input)).isInstanceOf(InvalidPhotoVersionException.class);
        assertThatThrownBy(()->PhotoRevision.parse(null)).isInstanceOf(PhotoVersionRequiredException.class);
    }
}
