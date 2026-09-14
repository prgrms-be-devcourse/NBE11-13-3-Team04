package com.example.iter.reservation.support;

import com.example.iter.reservation.api.RentalReviewInfo;
import com.example.iter.reservation.api.RentalReviewQueryPort;
import com.example.iter.reservation.domain.entity.RentalReview;
import com.example.iter.reservation.domain.repository.RentalReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// reservation/api/RentalReviewQueryPort 의 모놀리스 구현.
// 규약은 auth/support/JpaUserQueryAdapter 의 주석을 따른다.
@Component
@RequiredArgsConstructor
public class JpaRentalReviewQueryAdapter implements RentalReviewQueryPort {

    private final RentalReviewRepository rentalReviewRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<RentalReviewInfo> find(Long reviewId) {
        return rentalReviewRepository.findById(reviewId).map(JpaRentalReviewQueryAdapter::toInfo);
    }

    private static RentalReviewInfo toInfo(RentalReview review) {
        return new RentalReviewInfo(
                review.getId(),
                review.getRentalId(),
                review.getReviewerId(),
                review.getRevieweeId(),
                review.getRating()
        );
    }
}
