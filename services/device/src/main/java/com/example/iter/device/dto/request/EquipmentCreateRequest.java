package com.example.iter.device.dto.request;

import com.example.iter.common.dto.request.CapturedImageRequest;
import com.example.iter.common.image.CaptureView;
import com.example.iter.device.domain.entity.EquipmentCategory;
import com.example.iter.device.domain.entity.ProductConditionType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record EquipmentCreateRequest(
        @NotNull(message = "장비 카테고리는 필수입니다.")
        EquipmentCategory category,

        @NotBlank(message = "장비명은 필수입니다.")
        @Size(max = 100, message = "장비명은 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "장비 설명은 필수입니다.")
        String description,

        @NotNull(message = "1일 대여 가격은 필수입니다.")
        @Positive(message = "1일 대여 가격은 0보다 커야 합니다.")
        BigDecimal dailyPrice,

        @NotNull(message = "대여 가능 시작일은 필수입니다.")
        @FutureOrPresent(message = "대여 가능 시작일은 현재 날짜 이상이어야 합니다.")
        LocalDate availableFrom,

        @NotNull(message = "대여 가능 종료일은 필수입니다.")
        LocalDate availableTo,

        @NotNull(message = "장비 컨디션은 필수입니다.")
        ProductConditionType productCondition,

        String conditionDetail,

        @NotEmpty(message = "장비 이미지는 필수입니다.")
        @Size(min = 3, max = 3, message = "정면·측면·후면 사진을 각각 한 장씩 등록해주세요.")
        List<@Valid CapturedImageRequest> images
) {

    @AssertTrue(message = "대여 가능 종료일은 시작일보다 빠를 수 없습니다.")
    public boolean isValidAvailablePeriod() {
        return availableFrom == null || availableTo == null || !availableTo.isBefore(availableFrom);
    }

    @AssertTrue(message = "NORMAL이 아닌 장비는 컨디션 상세 설명이 필수입니다.")
    public boolean isValidConditionDetail() {
        return productCondition == null
                || productCondition == ProductConditionType.NORMAL
                || conditionDetail != null && !conditionDetail.isBlank();
    }

    @AssertTrue(message = "정면·측면·후면 사진을 각각 한 장씩 등록해주세요.")
    public boolean hasAllCaptureViews() {
        if (images == null || images.size() != CaptureView.values().length) {
            return false;
        }
        EnumSet<CaptureView> views = EnumSet.noneOf(CaptureView.class);
        images.stream()
                .map(CapturedImageRequest::captureView)
                .filter(Objects::nonNull)
                .forEach(views::add);
        return views.equals(EnumSet.allOf(CaptureView.class));
    }

    @AssertTrue(message = "중복된 이미지 객체 키는 등록할 수 없습니다.")
    public boolean hasNoDuplicateImageKeys() {
        return images == null
                || new HashSet<>(images.stream().map(CapturedImageRequest::objectKey).toList()).size()
                == images.size();
    }

    public List<CapturedImageRequest> orderedImages() {
        if (images == null) {
            return List.of();
        }
        return images.stream()
                .sorted(Comparator.comparing(
                        CapturedImageRequest::captureView,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public List<String> imageKeys() {
        return orderedImages().stream().map(CapturedImageRequest::objectKey).toList();
    }
}
