package com.ticketing.controller;

import com.ticketing.dto.GridResponse;
import com.ticketing.security.UserPrincipal;
import com.ticketing.service.GridService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class GridController {

    private final GridService gridService;

    @GetMapping("/grid")
    public ResponseEntity<GridResponse> getGrid(@AuthenticationPrincipal UserPrincipal currentUser) {
        GridResponse response = gridService.getNextIntervalGrid(currentUser);
        return ResponseEntity.ok(response);
    }
}
