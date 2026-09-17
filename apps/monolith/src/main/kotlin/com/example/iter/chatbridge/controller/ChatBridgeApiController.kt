package com.example.iter.chatbridge.controller

import com.example.iter.chatbridge.controller.spec.ChatBridgeApiSpec
import com.example.iter.chatbridge.dto.request.ChatInquiryGrantRequest
import com.example.iter.chatbridge.dto.response.ChatInquiryGrantResponse
import com.example.iter.chatbridge.dto.response.ChatTicketResponse
import com.example.iter.chatbridge.service.ChatInquiryGrantService
import com.example.iter.chatbridge.service.ChatTicketService
import com.example.iter.common.security.CustomUserDetails
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/chat")
@PreAuthorize("hasRole('USER')")
class ChatBridgeApiController(
    private val chatTicketService: ChatTicketService,
    private val chatInquiryGrantService: ChatInquiryGrantService,
) : ChatBridgeApiSpec {

    @PostMapping("/tickets")
    override fun issueTicket(
        @AuthenticationPrincipal principal: CustomUserDetails,
    ): ResponseEntity<ChatTicketResponse> {
        val ticket = chatTicketService.issue(principal.user.id)
        return ResponseEntity.ok(ChatTicketResponse(ticket))
    }

    @PostMapping("/inquiry-grants")
    override fun issueInquiryGrant(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody request: ChatInquiryGrantRequest,
    ): ResponseEntity<ChatInquiryGrantResponse> {
        val grantToken = chatInquiryGrantService.issue(principal.user.id, request.equipmentId!!)
        return ResponseEntity.ok(ChatInquiryGrantResponse(grantToken))
    }
}
