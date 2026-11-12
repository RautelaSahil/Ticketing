package com.ticketing.exception;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when one or more rooms in a batch reservation request are unavailable,
 * causing the entire all-or-nothing batch transaction to be aborted.
 */
@Getter
public class BatchBookingConflictException extends RuntimeException {

    private final List<String> unavailableRooms;

    public BatchBookingConflictException(String message, List<String> unavailableRooms) {
        super(message);
        this.unavailableRooms = unavailableRooms != null ? Collections.unmodifiableList(unavailableRooms) : List.of();
    }
}
