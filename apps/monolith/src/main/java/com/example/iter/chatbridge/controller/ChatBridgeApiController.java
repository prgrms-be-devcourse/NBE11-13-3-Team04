package com.example.iter.chatbridge.controller;

import com.example.iter.chatbridge.controller.spec.ChatBridgeApiSpec;
import com.example.iter.chatbridge.dto.request.ChatInquiryGrantRequest;
import com.example.iter.chatbridge.dto.response.ChatInquiryGrantResponse;
import com.example.iter.chatbridge.dto.response.ChatTicketResponse;
import com.example.iter.chatbridge.service.ChatInquiryGrantService;
import com.example.iter.chatbridge.service.ChatTicketService;
import com.example.iter.common.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/chat")
@PreAuthorize("hasRole('USER')")
@RequiredArgsConstructor
public class ChatBridgeApiController implements ChatBridgeApiSpec {

    private final ChatTicketService chatTicketService;
    private final ChatInquiryGrantService chatInquiryGrantService;

    @PostMapping("/tickets")
    @Override
    public ResponseEntity<ChatTicketResponse> issueTicket(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        String ticket = chatTicketService.issue(principal.getUser().getId());
        return ResponseEntity.ok(new ChatTicketResponse(ticket));
    }

    @PostMapping("/inquiry-grants")
    @Override
    public ResponseEntity<ChatInquiryGrantResponse> issueInquiryGrant(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody ChatInquiryGrantRequest request
    ) {
        String grantToken = chatInquiryGrantService.issue(principal.getUser().getId(), request.equipmentId());
        return ResponseEntity.ok(new ChatInquiryGrantResponse(grantToken));
    }
}
