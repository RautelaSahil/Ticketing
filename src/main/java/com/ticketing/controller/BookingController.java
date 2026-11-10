package com.ticketing.controller;

import com.ticketing.dto.BookRoomRequest;
import com.ticketing.dto.ReservationResponse;
import com.ticketing.security.UserPrincipal;
import com.ticketing.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/book")
    public ResponseEntity<ReservationResponse> bookRoom(
            @RequestBody BookRoomRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ReservationResponse response = bookingService.bookRoom(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
