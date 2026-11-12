package com.ticketing.controller;

import com.ticketing.dto.BatchBookingResponse;
import com.ticketing.dto.BookBatchRequest;
import com.ticketing.dto.BookRoomRequest;
import com.ticketing.dto.FutureBookRequest;
import com.ticketing.dto.FutureBookingResponse;
import com.ticketing.dto.ReservationResponse;
import com.ticketing.security.UserPrincipal;
import com.ticketing.service.BatchBookingService;
import com.ticketing.service.BookingService;
import com.ticketing.service.FutureBookingService;
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
    private final BatchBookingService batchBookingService;
    private final FutureBookingService futureBookingService;

    @PostMapping("/book")
    public ResponseEntity<ReservationResponse> bookRoom(
            @RequestBody BookRoomRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ReservationResponse response = bookingService.bookRoom(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/book-batch")
    public ResponseEntity<BatchBookingResponse> bookBatch(
            @RequestBody BookBatchRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        BatchBookingResponse response = batchBookingService.bookBatch(currentUser, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/future-book")
    public ResponseEntity<FutureBookingResponse> futureBook(
            @RequestBody FutureBookRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        FutureBookingResponse response = futureBookingService.reserveFutureSlot(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
