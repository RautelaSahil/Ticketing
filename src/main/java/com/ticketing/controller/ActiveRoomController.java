package com.ticketing.controller;

import com.ticketing.dto.ActiveRoomsResponse;
import com.ticketing.security.UserPrincipal;
import com.ticketing.service.ActiveRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ActiveRoomController {

    private final ActiveRoomService activeRoomService;

    @GetMapping("/active")
    public ResponseEntity<ActiveRoomsResponse> getMyActiveRooms(@AuthenticationPrincipal UserPrincipal currentUser) {
        ActiveRoomsResponse response = activeRoomService.getMyActiveRooms(currentUser);
        return ResponseEntity.ok(response);
    }
}
