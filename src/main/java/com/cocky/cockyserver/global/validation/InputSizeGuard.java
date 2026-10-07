package com.cocky.cockyserver.global.validation;

import com.cocky.cockyserver.global.exception.InputTooLargeException;
import java.nio.charset.StandardCharsets;

/**
 * 코드/stdin 같은 사용자 입력의 크기 제한(UTF-8 바이트 기준 64KB). POST /run과 POST /submissions가
 * 같은 기준을 쓰도록 한 곳에 둔다. 초과하면 {@link InputTooLargeException}(400).
 */
public final class InputSizeGuard {

    public static final int MAX_INPUT_BYTES = 64 * 1024;

    private InputSizeGuard() {
    }

    /** value가 null이면 통과. 문자열 길이가 아니라 UTF-8 바이트 수로 비교한다. */
    public static void requireWithinLimit(String field, String value) {
        if (value != null && value.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
            throw new InputTooLargeException(field + "는 64KB 이하여야 합니다.");
        }
    }
}
