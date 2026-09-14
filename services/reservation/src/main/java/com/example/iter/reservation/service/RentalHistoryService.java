package com.example.iter.reservation.service;

import com.example.iter.auth.api.UserQueryPort;
import com.example.iter.auth.api.UserSummary;
import com.example.iter.common.dto.request.PagingRequest;
import com.example.iter.common.dto.response.PageResponse;
import com.example.iter.common.exception.CustomException;
import com.example.iter.common.exception.ErrorCode;
import com.example.iter.device.api.EquipmentInfo;
import com.example.iter.device.api.EquipmentQueryPort;
import com.example.iter.device.api.EquipmentThumbnailQueryPort;
import com.example.iter.reservation.domain.entity.Rental;
import com.example.iter.reservation.domain.repository.RentalHistoryRepository;
import com.example.iter.reservation.domain.repository.spec.RentalSpecifications;
import com.example.iter.reservation.dto.request.RentalHistorySearchRequest;
import com.example.iter.reservation.dto.response.RentalHistoryResponse;
import com.example.iter.reservation.util.RentalHistoryMapper;
import com.example.iter.reservation.util.RentalOverduePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RentalHistoryService {

    private final RentalHistoryRepository rentalHistoryRepository;
    private final EquipmentQueryPort equipmentQueryPort;
    private final EquipmentThumbnailQueryPort equipmentThumbnailQueryPort;
    private final UserQueryPort userQueryPort;
    private final RentalHistoryMapper rentalHistoryMapper;

    // 로그인 사용자가 빌린 장비 이력을 조회합니다.
    @Transactional(readOnly = true)
    public PageResponse<RentalHistoryResponse> getBorrowedHistory(Long renterId, RentalHistorySearchRequest request) {
        Page<Rental> rentals = rentalHistoryRepository.findAll(
                RentalSpecifications.borrowedHistory(renterId, request.status(), normalizeKeyword(request.equipmentName())),
                historyPageable(request.page(), request.size())
        );

        return toBorrowedHistoryResponse(rentals, LocalDate.now());
    }

    // 로그인 사용자가 빌려준 장비 이력을 조회합니다.
    @Transactional(readOnly = true)
    public PageResponse<RentalHistoryResponse> getLentHistory(Long ownerId, RentalHistorySearchRequest request) {
        Page<Rental> rentals = rentalHistoryRepository.findLentHistory(
                ownerId,
                request.status(),
                normalizeKeyword(request.equipmentName()),
                historyPageable(request.page(), request.size())
        );

        return toLentHistoryResponse(rentals, LocalDate.now());
    }

    // 로그인 사용자가 빌린 장비 중 현재 연체 중인 거래를 조회합니다.
    @Transactional(readOnly = true)
    public PageResponse<RentalHistoryResponse> getBorrowedOverdueHistory(Long renterId, PagingRequest request) {
        LocalDate today = LocalDate.now();

        Page<Rental> rentals = rentalHistoryRepository.findByRenterIdAndEndDateBeforeAndStatusIn(
                renterId,
                today,
                RentalOverduePolicy.statuses(),
                overduePageable(request.page(), request.size())
        );

        return toBorrowedHistoryResponse(rentals, today);
    }

    // 로그인 사용자가 빌려준 장비 중 현재 연체 중인 거래를 조회합니다.
    @Transactional(readOnly = true)
    public PageResponse<RentalHistoryResponse> getLentOverdueHistory(Long ownerId, PagingRequest request) {
        LocalDate today = LocalDate.now();

        Page<Rental> rentals = rentalHistoryRepository.findByOwnerIdSnapshotAndEndDateBeforeAndStatusIn(
                ownerId,
                today,
                RentalOverduePolicy.statuses(),
                overduePageable(request.page(), request.size())
        );

        return toLentHistoryResponse(rentals, today);
    }

    // ============================================================

    private PageResponse<RentalHistoryResponse> toBorrowedHistoryResponse(Page<Rental> rentals, LocalDate today) {
        if (rentals.isEmpty()) {
            return emptyResponse(rentals);
        }

        Set<Long> equipmentIds = rentals.getContent().stream().map(Rental::getEquipmentId).collect(Collectors.toSet());
        Map<Long, EquipmentInfo> equipmentMap = equipmentQueryPort.findAll(equipmentIds);

        Set<Long> ownerIds = rentals.getContent().stream()
                .map(rental -> getEquipment(equipmentMap, rental.getEquipmentId()).ownerId())
                .collect(Collectors.toSet());

        Map<Long, UserSummary> userMap = userQueryPort.findSummaries(ownerIds);
        Map<Long, String> thumbnailMap = loadThumbnails(equipmentIds);

        List<RentalHistoryResponse> responses = rentals.getContent().stream().map(rental -> {
                EquipmentInfo equipment = getEquipment(equipmentMap, rental.getEquipmentId());
                UserSummary owner = getUser(userMap, equipment.ownerId());

                return rentalHistoryMapper.toResponse(
                        rental,
                        owner,
                        thumbnailMap.get(rental.getEquipmentId()),
                        RentalOverduePolicy.calculateDays(rental, today)
                );
        }).toList();

        return toPageResponse(rentals, responses);
    }

    private PageResponse<RentalHistoryResponse> toLentHistoryResponse(Page<Rental> rentals, LocalDate today) {
        if (rentals.isEmpty()) {
            return emptyResponse(rentals);
        }

        Set<Long> renterIds = rentals.getContent().stream()
                .map(Rental::getRenterId)
                .collect(Collectors.toSet());

        Map<Long, UserSummary> userMap = userQueryPort.findSummaries(renterIds);
        Map<Long, String> thumbnailMap = loadThumbnails(rentals.getContent().stream().map(Rental::getEquipmentId).collect(Collectors.toSet()));

        List<RentalHistoryResponse> responses = rentals.getContent().stream().map(rental -> rentalHistoryMapper.toResponse(
                rental,
                getUser(userMap, rental.getRenterId()),
                thumbnailMap.get(rental.getEquipmentId()),
                RentalOverduePolicy.calculateDays(rental, today)
        )).toList();

        return toPageResponse(rentals, responses);
    }

    private Map<Long, String> loadThumbnails(Collection<Long> equipmentIds) {
        return equipmentThumbnailQueryPort.findThumbnailUrls(equipmentIds);
    }

    private EquipmentInfo getEquipment(Map<Long, EquipmentInfo> equipmentMap, Long equipmentId) {
        EquipmentInfo equipment = equipmentMap.get(equipmentId);

        if (equipment == null) {
            throw new CustomException(ErrorCode.EQUIPMENT_NOT_FOUND);
        }

        return equipment;
    }

    private UserSummary getUser(Map<Long, UserSummary> userMap, Long userId) {
        UserSummary user = userMap.get(userId);

        if (user == null) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        return user;
    }

    private Pageable historyPageable(int page, int size) {
        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
    }

    private Pageable overduePageable(int page, int size) {
        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.ASC, "endDate")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                        .and(Sort.by(Sort.Direction.DESC, "id"))
        );
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }

    private PageResponse<RentalHistoryResponse> emptyResponse(Page<Rental> rentals) {
        return new PageResponse<>(
                List.of(),
                rentals.getNumber(),
                rentals.getSize(),
                rentals.getTotalElements(),
                rentals.getTotalPages()
        );
    }

    private PageResponse<RentalHistoryResponse> toPageResponse(Page<Rental> rentals, List<RentalHistoryResponse> responses) {
        return new PageResponse<>(
                responses,
                rentals.getNumber(),
                rentals.getSize(),
                rentals.getTotalElements(),
                rentals.getTotalPages()
        );
    }
}
