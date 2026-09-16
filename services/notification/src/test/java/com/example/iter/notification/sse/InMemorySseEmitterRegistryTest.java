package com.example.iter.notification.sse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySseEmitterRegistryTest {

    private final InMemorySseEmitterRegistry registry = new InMemorySseEmitterRegistry();

    @Test
    void 등록한_emitter를_조회할_수_있다() {
        SseEmitter emitter = registry.register(1L);

        assertThat(registry.get(1L)).containsExactly(emitter);
    }

    @Test
    void 같은_사용자가_여러_emitter를_등록하면_전부_조회된다() {
        SseEmitter first = registry.register(1L);
        SseEmitter second = registry.register(1L);

        assertThat(registry.get(1L)).containsExactly(first, second);
    }

    @Test
    void 등록되지_않은_사용자는_빈_리스트를_반환한다() {
        assertThat(registry.get(99L)).isEmpty();
    }

    @Test
    void emitter를_제거하면_더_이상_조회되지_않는다() {
        SseEmitter emitter = registry.register(1L);

        registry.remove(1L, emitter);

        assertThat(registry.get(1L)).isEmpty();
    }

    @Test
    void 같은_사용자의_다른_emitter는_제거되지_않는다() {
        SseEmitter first = registry.register(1L);
        SseEmitter second = registry.register(1L);

        registry.remove(1L, first);

        assertThat(registry.get(1L)).containsExactly(second);
    }
}
