package com.ticketing.controller;

import com.ticketing.dto.LeaveResponse;
import com.ticketing.security.UserPrincipal;
import com.ticketing.service.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    @PostMapping("/leave/{reservationId}")
    public ResponseEntity<LeaveResponse> leaveRoom(
            @PathVariable String reservationId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        LeaveResponse response = leaveService.leaveRoom(reservationId, currentUser);
        return ResponseEntity.ok(response);
    }
}
