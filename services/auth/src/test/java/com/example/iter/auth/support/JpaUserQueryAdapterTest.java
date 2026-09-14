package com.example.iter.auth.support;

import com.example.iter.auth.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaUserQueryAdapterTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JpaUserQueryAdapter adapter;

    // 포트 주석의 "탈퇴 회원을 제외하지 않는다"를 지키는 테스트.
    // 어댑터가 조용히 조건을 추가하면 대시보드 숫자가 달라지는데 호출부는 알 방법이 없다.
    @Test
    void 전체_회원_수를_거르지_않고_그대로_돌려준다() {
        when(userRepository.count()).thenReturn(120L);

        assertThat(adapter.count()).isEqualTo(120L);

        verify(userRepository).count();
        verifyNoMoreInteractions(userRepository);
    }
}
