package iter.auth.service;

import iter.common.security.Role;
import iter.auth.domain.entity.User;
import iter.common.security.UserStatus;
import iter.auth.domain.repository.UserRepository;
import iter.auth.dto.request.AdminUserSearchRequest;
import iter.auth.dto.request.AdminUserStatusRequest;
import iter.auth.util.AdminUserMapper;
import iter.common.audit.domain.entity.AdminActionTargetType;
import iter.common.audit.domain.entity.AdminActionType;
import iter.common.audit.service.AdminActionService;
import iter.common.exception.CustomException;
import iter.common.exception.ErrorCode;
import iter.common.pagination.CursorCodec;
import iter.common.pagination.CursorKey;
import iter.dispute.api.ReportQueryPort;
import iter.reservation.api.RentalStatus;
import iter.reservation.api.RentalQueryPort;
import iter.reservation.api.UserRentalStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long USER_ID = 2L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RentalQueryPort rentalQueryPort;

    @Mock
    private ReportQueryPort reportQueryPort;

    @Mock
    private AdminActionService adminActionService;

    @Spy
    private AdminUserMapper adminUserMapper = new AdminUserMapper();

    @Mock
    private Clock clock;

    @InjectMocks
    private AdminUserService adminUserService;

    @BeforeEach
    void setUpClock() {
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-08-20T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
    }

    @Test
    void 관리자_회원_목록은_검색어와_커서를_정규화해_조회한다() {
        User user = user(USER_ID, Role.USER, UserStatus.ACTIVE);
        CursorKey cursorKey = new CursorKey(LocalDateTime.of(2026, 8, 1, 10, 0), 100L);
        AdminUserSearchRequest request = new AdminUserSearchRequest(
                "  iter  ",
                UserStatus.ACTIVE,
                CursorCodec.encode(cursorKey),
                10
        );
        when(userRepository.searchForAdminByCursor(
                eq("iter"),
                eq(UserStatus.ACTIVE),
                eq(cursorKey.createdAt()),
                eq(cursorKey.id()),
                any(Pageable.class)
        )).thenReturn(List.of(user));

        var response = adminUserService.getUsers(request);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().userId()).isEqualTo(USER_ID);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchForAdminByCursor(
                eq("iter"),
                eq(UserStatus.ACTIVE),
                eq(cursorKey.createdAt()),
                eq(cursorKey.id()),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(11);
    }

    @Test
    void 빈_검색어는_null로_변환해_전체_회원을_조회한다() {
        when(userRepository.searchForAdminByCursor(
                isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(List.of());

        var response = adminUserService.getUsers(
                new AdminUserSearchRequest("   ", null, null, null)
        );

        assertThat(response.content()).isEmpty();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void 회원_상세에_성립_거래_연체_피신고_건수를_함께_반환한다() {
        User user = user(USER_ID, Role.USER, UserStatus.ACTIVE);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(rentalQueryPort.countUserRentalStats(eq(USER_ID), any(LocalDate.class)))
                .thenReturn(new UserRentalStats(3L, 4L, 2L));
        when(reportQueryPort.countAgainstUser(USER_ID))
                .thenReturn(5L);

        var response = adminUserService.getUser(USER_ID);

        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.rentedCount()).isEqualTo(3);
        assertThat(response.lentCount()).isEqualTo(4);
        assertThat(response.overdueCount()).isEqualTo(2);
        assertThat(response.reportCount()).isEqualTo(5);

        // 어떤 상태가 "성사"·"연체"인지는 reservation 의 정책이다.
        // 그 검증은 JpaRentalQueryAdapterTest 로 옮겼다.
        verify(rentalQueryPort).countUserRentalStats(eq(USER_ID), any(LocalDate.class));
    }

    @Test
    void 존재하지_않는_회원_상세는_조회할_수_없다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.getUser(USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verifyNoInteractions(rentalQueryPort, reportQueryPort, adminUserMapper);
    }

    @Test
    void ACTIVE_일반_회원을_정지하고_관리자_조치_이력을_기록한다() {
        User user = user(USER_ID, Role.USER, UserStatus.ACTIVE);
        when(userRepository.findWithLockById(USER_ID)).thenReturn(Optional.of(user));

        var response = adminUserService.updateStatus(
                ADMIN_ID,
                USER_ID,
                new AdminUserStatusRequest(UserStatus.SUSPENDED, "  신고 누적  ")
        );

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(response.status()).isEqualTo(UserStatus.SUSPENDED);
        verify(adminActionService).record(
                ADMIN_ID,
                AdminActionTargetType.USER,
                USER_ID,
                AdminActionType.SUSPEND_USER,
                "신고 누적"
        );
        verify(userRepository).flush();
    }

    @Test
    void SUSPENDED_회원의_정지를_해제하고_관리자_조치_이력을_기록한다() {
        User user = user(USER_ID, Role.USER, UserStatus.SUSPENDED);
        when(userRepository.findWithLockById(USER_ID)).thenReturn(Optional.of(user));

        var response = adminUserService.updateStatus(
                ADMIN_ID,
                USER_ID,
                new AdminUserStatusRequest(UserStatus.ACTIVE, "재검토 완료")
        );

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        verify(adminActionService).record(
                ADMIN_ID,
                AdminActionTargetType.USER,
                USER_ID,
                AdminActionType.RESTORE_USER,
                "재검토 완료"
        );
    }

    @Test
    void 관리자_계정은_정지할_수_없다() {
        User admin = user(USER_ID, Role.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findWithLockById(USER_ID)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> adminUserService.updateStatus(
                ADMIN_ID,
                USER_ID,
                new AdminUserStatusRequest(UserStatus.SUSPENDED, "정지 시도")
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ADMIN_SUSPENSION_NOT_ALLOWED);

        verifyNoInteractions(adminActionService);
        verify(userRepository, never()).flush();
    }

    @Test
    void 탈퇴한_회원은_정지하거나_복구할_수_없다() {
        User deletedUser = user(USER_ID, Role.USER, UserStatus.DELETED);
        when(userRepository.findWithLockById(USER_ID)).thenReturn(Optional.of(deletedUser));

        assertThatThrownBy(() -> adminUserService.updateStatus(
                ADMIN_ID,
                USER_ID,
                new AdminUserStatusRequest(UserStatus.ACTIVE, "복구 시도")
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_USER_STATUS_TRANSITION);

        verifyNoInteractions(adminActionService);
    }

    @Test
    void 현재와_같은_회원_상태로는_변경할_수_없다() {
        User user = user(USER_ID, Role.USER, UserStatus.ACTIVE);
        when(userRepository.findWithLockById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> adminUserService.updateStatus(
                ADMIN_ID,
                USER_ID,
                new AdminUserStatusRequest(UserStatus.ACTIVE, "중복 변경")
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_USER_STATUS_TRANSITION);

        verifyNoInteractions(adminActionService);
    }

    @Test
    void 존재하지_않는_회원의_상태는_변경할_수_없다() {
        when(userRepository.findWithLockById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.updateStatus(
                ADMIN_ID,
                USER_ID,
                new AdminUserStatusRequest(UserStatus.SUSPENDED, "정지 시도")
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verifyNoInteractions(adminActionService);
    }

    private User user(Long id, Role role, UserStatus status) {
        return User.builder()
                .id(id)
                .email("user" + id + "@iter.test")
                .password("encoded-password")
                .name("회원" + id)
                .nickname("닉네임" + id)
                .phone("010-0000-000" + id)
                .role(role)
                .status(status)
                .build();
    }
}
