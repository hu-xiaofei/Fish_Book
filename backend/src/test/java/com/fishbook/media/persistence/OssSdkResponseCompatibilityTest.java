package com.fishbook.media.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.aliyun.oss.common.comm.ResponseMessage;
import com.aliyun.oss.internal.ResponseParsers;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OssSdkResponseCompatibilityTest {
    @Test
    void actualSdkParsesFabricatedOssErrorXmlOnEffectiveJava21Dependencies() throws Exception {
        var xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Error><Code>AccessDenied</Code><Message>offline test denied</Message>
                <RequestId>fake-request-id</RequestId><HostId>fake-host</HostId></Error>
                """;
        var response = new ResponseMessage(null);
        response.setStatusCode(403);
        response.setContent(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        try {
            var error = new ResponseParsers.ErrorResponseParser().parse(response);
            assertThat(error.Code).isEqualTo("AccessDenied");
            assertThat(error.Message).isEqualTo("offline test denied");
            assertThat(error.RequestId).isEqualTo("fake-request-id");
            assertThat(error.HostId).isEqualTo("fake-host");
        } finally {
            response.close();
        }
    }
}
