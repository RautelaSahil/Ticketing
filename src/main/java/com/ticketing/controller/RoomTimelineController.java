package com.ticketing.controller;

import com.ticketing.dto.SlotTimelineResponse;
import com.ticketing.security.UserPrincipal;
import com.ticketing.service.SlotTimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomTimelineController {

    private final SlotTimelineService slotTimelineService;

    @GetMapping("/{roomId}/timeline")
    public ResponseEntity<SlotTimelineResponse> getTimeline(
            @PathVariable String roomId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        SlotTimelineResponse response = slotTimelineService.getTimelineForRoom(roomId, currentUser);
        return ResponseEntity.ok(response);
    }
}
