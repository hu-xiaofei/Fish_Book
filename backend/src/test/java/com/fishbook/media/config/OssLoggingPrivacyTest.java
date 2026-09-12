package com.fishbook.media.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.common.utils.LogUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;

@ExtendWith(OutputCaptureExtension.class)
class OssLoggingPrivacyTest {
    @Test
    void defaultLoggingSuppressesRawSdkCredentialsAndHttpDetailsButKeepsApplicationLogs(CapturedOutput output) {
        var application = new SpringApplication(MinimalApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (var context = application.run()) {
            String details = "FAKE-token FAKE-secret https://FAKE-endpoint.invalid FAKE-object-key "
                    + "Authorization: FAKE-auth x-oss-security-token: FAKE-header";
            LogUtils.logException("synthetic SDK failure: ", new ClientException(details));
            for (String category : new String[]{"com.aliyun.credentials", "org.apache.http",
                    "org.apache.http.headers", "org.apache.http.wire", "okhttp3",
                    "okhttp3.logging.HttpLoggingInterceptor", "jdk.httpclient.HttpClient"}) {
                var logger = LoggerFactory.getLogger(category);
                logger.warn(details);
                logger.error(details);
            }
            LoggerFactory.getLogger("com.fishbook.media.application").warn("storage failed: ClientException");
        }
        assertThat(output.getAll()).contains("storage failed: ClientException")
                .doesNotContain("FAKE-token", "FAKE-secret", "FAKE-endpoint", "FAKE-object-key",
                        "FAKE-auth", "FAKE-header");
    }

    @Configuration(proxyBeanMethods = false)
    static class MinimalApplication {}
}
