package com.ticketing.controller;

import com.ticketing.dto.MyFutureReservationsResponse;
import com.ticketing.security.UserPrincipal;
import com.ticketing.service.MyReservationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class MyReservationsController {

    private final MyReservationsService myReservationsService;

    @GetMapping("/my-future")
    public ResponseEntity<MyFutureReservationsResponse> getMyFutureReservations(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        MyFutureReservationsResponse response = myReservationsService.getMyFutureReservations(currentUser);
        return ResponseEntity.ok(response);
    }
}
