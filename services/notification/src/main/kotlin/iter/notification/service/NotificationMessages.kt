package iter.notification.service

import iter.auth.api.PreferredLanguage

// NotificationEventListener가 만드는 알림의 제목/본문/보간 파라미터를 한곳에 모아둔다.
// 한국어 문구/보간 동작은 이전(각 이벤트 핸들러에 흩어져 있던 리터럴)과 완전히 동일하게 유지했다 — 순수 이동.
//
// title/message는 이메일 발송에만 쓰인다 (그 자리에서 조립해서 바로 보내고 저장하지 않음).
// 이메일은 발사 후 소멸(fire-and-forget)이라 보내는 시점에 언어가 확정돼야 하므로, 수신자의
// User.preferredLanguage를 보고 한국어/영어 중 하나를 그 자리에서 고른다.
//
// params는 Notification 엔티티에 저장돼서 API로 그대로 나가고, 프론트가 type + params로
// 자체 i18n 사전을 통해 화면 표시 문구(제목/본문)를 조립한다 — 이메일과 무관한 별도 경로다.
// (자세한 내용은 docs/i18n-frontend-handoff.md 참고)
internal object NotificationMessages {

    private const val PAYMENT_COMPLETED_OWNER_TITLE_KO = "결제가 완료되었습니다"
    private const val PAYMENT_COMPLETED_OWNER_TEMPLATE_KO = "%s님이 [%s] 대여 건의 결제를 완료했습니다."
    private const val PAYMENT_COMPLETED_OWNER_TITLE_EN = "Payment completed"
    private const val PAYMENT_COMPLETED_OWNER_TEMPLATE_EN = "%s completed payment for your [%s] rental."

    private const val RENTAL_REQUESTED_TITLE_KO = "새로운 대여 신청이 도착했습니다"
    private const val RENTAL_REQUESTED_TEMPLATE_KO =
        "[%s] 대여 신청이 도착했습니다. 승인 대기 목록을 확인해주세요."
    private const val RENTAL_REQUESTED_TITLE_EN = "New rental request received"
    private const val RENTAL_REQUESTED_TEMPLATE_EN =
        "A rental request for [%s] has arrived. Please check your pending approvals."

    private const val PAYMENT_COMPLETED_RENTER_TITLE_KO = "결제가 완료되었습니다"
    private const val PAYMENT_COMPLETED_RENTER_TEMPLATE_KO =
        "[%s] 대여 결제가 완료되었습니다. 등록자의 승인을 기다려주세요."
    private const val PAYMENT_COMPLETED_RENTER_TITLE_EN = "Payment completed"
    private const val PAYMENT_COMPLETED_RENTER_TEMPLATE_EN =
        "Your payment for [%s] is complete. Please wait for the owner's approval."

    private const val RENTAL_APPROVED_TITLE_KO = "대여 요청이 승인되었습니다"
    private const val RENTAL_APPROVED_TEMPLATE_KO = "[%s] 대여 요청이 승인되었습니다."
    private const val RENTAL_APPROVED_TITLE_EN = "Rental request approved"
    private const val RENTAL_APPROVED_TEMPLATE_EN = "Your rental request for [%s] has been approved."

    private const val RENTAL_REJECTED_TITLE_KO = "대여 요청이 거절되었습니다"
    private const val RENTAL_REJECTED_TEMPLATE_KO = "[%s] 대여 요청이 거절되었습니다. 사유: %s.%s"
    private const val RENTAL_REJECTED_REFUND_NOTICE_KO = " 결제 금액은 환불 처리되었습니다."
    private const val RENTAL_REJECTED_TITLE_EN = "Rental request rejected"
    private const val RENTAL_REJECTED_TEMPLATE_EN = "Your rental request for [%s] was rejected. Reason: %s.%s"
    private const val RENTAL_REJECTED_REFUND_NOTICE_EN = " Your payment has been refunded."

    private const val RENTAL_CANCELED_TITLE_KO = "대여 요청이 취소되었습니다"
    private const val RENTAL_CANCELED_TEMPLATE_KO = "%s님이 [%s] 대여 요청을 취소했습니다."
    private const val RENTAL_CANCELED_TITLE_EN = "Rental request canceled"
    private const val RENTAL_CANCELED_TEMPLATE_EN = "%s canceled the rental request for [%s]."

    private const val RENTAL_RECEIVED_TITLE_KO = "대여자가 수령을 확인했습니다"
    private const val RENTAL_RECEIVED_TEMPLATE_KO =
        "%s님이 [%s] 물품 수령을 확인했습니다. 대여가 시작됩니다."
    private const val RENTAL_RECEIVED_TITLE_EN = "Renter confirmed receipt"
    private const val RENTAL_RECEIVED_TEMPLATE_EN =
        "%s confirmed receipt of [%s]. The rental period has started."

    // 인자 순서(reviewerName, productName, rating)는 KO/EN 공통이지만 문장 내 등장 순서가 달라
    // 위치 지정자(%1$s 등)로 순서를 맞춘다.
    private const val REVIEW_RECEIVED_TITLE_KO = "새 리뷰가 도착했습니다"
    private const val REVIEW_RECEIVED_TEMPLATE_KO = "%1\$s님이 [%2\$s] 거래에 별점 %3\$d점 리뷰를 남겼습니다."
    private const val REVIEW_RECEIVED_TITLE_EN = "You received a new review"
    private const val REVIEW_RECEIVED_TEMPLATE_EN = "%1\$s left a %3\$d-star review for your [%2\$s] rental."

    data class Content(val title: String, val message: String, val params: Map<String, Any>)

    private fun isEnglish(language: PreferredLanguage): Boolean = language == PreferredLanguage.EN

    fun paymentCompletedOwner(renterName: String, productName: String, language: PreferredLanguage): Content {
        val en = isEnglish(language)
        return Content(
            if (en) PAYMENT_COMPLETED_OWNER_TITLE_EN else PAYMENT_COMPLETED_OWNER_TITLE_KO,
            (if (en) PAYMENT_COMPLETED_OWNER_TEMPLATE_EN else PAYMENT_COMPLETED_OWNER_TEMPLATE_KO)
                .format(renterName, productName),
            mapOf("renterName" to renterName, "productName" to productName),
        )
    }

    fun rentalRequested(productName: String, language: PreferredLanguage): Content {
        val en = isEnglish(language)
        return Content(
            if (en) RENTAL_REQUESTED_TITLE_EN else RENTAL_REQUESTED_TITLE_KO,
            (if (en) RENTAL_REQUESTED_TEMPLATE_EN else RENTAL_REQUESTED_TEMPLATE_KO).format(productName),
            mapOf("productName" to productName),
        )
    }

    fun paymentCompletedRenter(productName: String, language: PreferredLanguage): Content {
        val en = isEnglish(language)
        return Content(
            if (en) PAYMENT_COMPLETED_RENTER_TITLE_EN else PAYMENT_COMPLETED_RENTER_TITLE_KO,
            (if (en) PAYMENT_COMPLETED_RENTER_TEMPLATE_EN else PAYMENT_COMPLETED_RENTER_TEMPLATE_KO).format(productName),
            mapOf("productName" to productName),
        )
    }

    fun rentalApproved(productName: String, language: PreferredLanguage): Content {
        val en = isEnglish(language)
        return Content(
            if (en) RENTAL_APPROVED_TITLE_EN else RENTAL_APPROVED_TITLE_KO,
            (if (en) RENTAL_APPROVED_TEMPLATE_EN else RENTAL_APPROVED_TEMPLATE_KO).format(productName),
            mapOf("productName" to productName),
        )
    }

    fun rentalRejected(productName: String, rejectReason: String, refunded: Boolean, language: PreferredLanguage): Content {
        val en = isEnglish(language)
        val refundNotice = if (refunded) {
            if (en) RENTAL_REJECTED_REFUND_NOTICE_EN else RENTAL_REJECTED_REFUND_NOTICE_KO
        } else {
            ""
        }
        return Content(
            if (en) RENTAL_REJECTED_TITLE_EN else RENTAL_REJECTED_TITLE_KO,
            (if (en) RENTAL_REJECTED_TEMPLATE_EN else RENTAL_REJECTED_TEMPLATE_KO)
                .format(productName, rejectReason, refundNotice),
            mapOf("productName" to productName, "rejectReason" to rejectReason, "refunded" to refunded),
        )
    }

    fun rentalCanceled(renterName: String, productName: String, language: PreferredLanguage): Content {
        val en = isEnglish(language)
        return Content(
            if (en) RENTAL_CANCELED_TITLE_EN else RENTAL_CANCELED_TITLE_KO,
            (if (en) RENTAL_CANCELED_TEMPLATE_EN else RENTAL_CANCELED_TEMPLATE_KO).format(renterName, productName),
            mapOf("renterName" to renterName, "productName" to productName),
        )
    }

    fun rentalReceived(renterName: String, productName: String, language: PreferredLanguage): Content {
        val en = isEnglish(language)
        return Content(
            if (en) RENTAL_RECEIVED_TITLE_EN else RENTAL_RECEIVED_TITLE_KO,
            (if (en) RENTAL_RECEIVED_TEMPLATE_EN else RENTAL_RECEIVED_TEMPLATE_KO).format(renterName, productName),
            mapOf("renterName" to renterName, "productName" to productName),
        )
    }

    fun reviewReceived(reviewerName: String, productName: String, rating: Int, language: PreferredLanguage): Content {
        val en = isEnglish(language)
        return Content(
            if (en) REVIEW_RECEIVED_TITLE_EN else REVIEW_RECEIVED_TITLE_KO,
            (if (en) REVIEW_RECEIVED_TEMPLATE_EN else REVIEW_RECEIVED_TEMPLATE_KO)
                .format(reviewerName, productName, rating),
            mapOf("reviewerName" to reviewerName, "productName" to productName, "rating" to rating),
        )
    }
}
