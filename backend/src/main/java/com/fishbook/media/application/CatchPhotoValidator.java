package com.fishbook.media.application;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class CatchPhotoValidator {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    public CatchPhotoType validate(byte[] content, String declaredContentType) {
        if (content == null || content.length == 0 || content.length > MAX_BYTES) {
            throw new InvalidCatchPhotoException();
        }

        var detectedType = detect(content);
        var normalizedDeclaration = declaredContentType == null
                ? ""
                : declaredContentType.trim().toLowerCase(Locale.ROOT);
        if (!detectedType.contentType().equals(normalizedDeclaration)) {
            throw new InvalidCatchPhotoException();
        }
        return detectedType;
    }

    private static CatchPhotoType detect(byte[] content) {
        if (startsWith(content, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
            return CatchPhotoType.JPEG;
        }
        if (startsWith(content, PNG_SIGNATURE)) {
            return CatchPhotoType.PNG;
        }
        if (isWebp(content)) {
            return CatchPhotoType.WEBP;
        }
        throw new InvalidCatchPhotoException();
    }

    private static boolean isWebp(byte[] content) {
        return content.length >= 12
                && content[0] == 0x52
                && content[1] == 0x49
                && content[2] == 0x46
                && content[3] == 0x46
                && content[8] == 0x57
                && content[9] == 0x45
                && content[10] == 0x42
                && content[11] == 0x50;
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (var index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }
}
