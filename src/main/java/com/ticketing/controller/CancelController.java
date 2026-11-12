package com.ticketing.controller;

import com.ticketing.dto.CancelReservationResponse;
import com.ticketing.security.UserPrincipal;
import com.ticketing.service.CancelService;
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
public class CancelController {

    private final CancelService cancelService;

    @PostMapping("/cancel/{reservationId}")
    public ResponseEntity<CancelReservationResponse> cancelReservation(
            @PathVariable String reservationId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        CancelReservationResponse response = cancelService.cancelReservation(reservationId, currentUser);
        return ResponseEntity.ok(response);
    }
}
