package iter.notification.controller.api

import iter.common.dto.response.CursorPageResponse
import iter.common.security.CustomUserDetails
import iter.notification.controller.api.spec.NotificationApiSpec
import iter.notification.dto.response.MarkAllReadResponse
import iter.notification.dto.response.NotificationResponse
import iter.notification.dto.response.SseTicketResponse
import iter.notification.dto.response.UnreadCountResponse
import iter.notification.service.NotificationService
import iter.notification.sse.NotificationSseService
import iter.notification.sse.SseTicketService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationApiController(
    private val notificationService: NotificationService,
    private val notificationSseService: NotificationSseService,
    private val sseTicketService: SseTicketService,
) : NotificationApiSpec {

    @PostMapping("/sse-ticket")
    override fun issueSseTicket(@AuthenticationPrincipal principal: CustomUserDetails): ResponseEntity<SseTicketResponse> {
        val ticket = sseTicketService.issue(principal.user.id)
        return ResponseEntity.ok(SseTicketResponse(ticket))
    }

    @GetMapping(value = ["/subscribe"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    override fun subscribe(@AuthenticationPrincipal principal: CustomUserDetails): SseEmitter =
        notificationSseService.subscribe(principal.user.id)

    @GetMapping
    override fun getNotifications(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<CursorPageResponse<NotificationResponse>> {
        val response = notificationService.getNotifications(principal.user.id, unreadOnly, cursor, size)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/unread-count")
    override fun getUnreadCount(@AuthenticationPrincipal principal: CustomUserDetails): ResponseEntity<UnreadCountResponse> {
        val count = notificationService.getUnreadCount(principal.user.id)
        return ResponseEntity.ok(UnreadCountResponse(count))
    }

    @PatchMapping("/{notificationId}/read")
    override fun markRead(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable notificationId: Long,
    ): ResponseEntity<NotificationResponse> {
        val response = notificationService.markRead(principal.user.id, notificationId)
        return ResponseEntity.ok(response)
    }

    @PatchMapping("/read-all")
    override fun markAllRead(@AuthenticationPrincipal principal: CustomUserDetails): ResponseEntity<MarkAllReadResponse> {
        val updated = notificationService.markAllRead(principal.user.id)
        return ResponseEntity.ok(MarkAllReadResponse(updated))
    }
}
