package com.ticketing.controller;

import com.ticketing.exception.ResourceConflictException;
import com.ticketing.exception.ResourceNotFoundException;
import com.ticketing.service.BatchBookingService;
import com.ticketing.service.BookingService;
import com.ticketing.service.CancelService;
import com.ticketing.service.FutureBookingService;
import com.ticketing.service.MyReservationsService;
import com.ticketing.service.SlotTimelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ErrorMappingIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private FutureBookingService futureBookingService;

    @Mock
    private BookingService bookingService;

    @Mock
    private BatchBookingService batchBookingService;

    @Mock
    private CancelService cancelService;

    @Mock
    private SlotTimelineService slotTimelineService;

    @Mock
    private MyReservationsService myReservationsService;

    @BeforeEach
    void setUp() {
        BookingController bookingController = new BookingController(bookingService, batchBookingService, futureBookingService);
        CancelController cancelController = new CancelController(cancelService);
        RoomTimelineController timelineController = new RoomTimelineController(slotTimelineService);
        MyReservationsController myReservationsController = new MyReservationsController(myReservationsService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(bookingController, cancelController, timelineController, myReservationsController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("400 Bad Request when slot is misaligned or out-of-window")
    void futureBook_misalignedBoundary_returns400() throws Exception {
        when(futureBookingService.reserveFutureSlot(any(), any()))
                .thenThrow(new IllegalArgumentException("Slot start time must be on a 5-minute boundary with zero seconds and nanos"));

        mockMvc.perform(post("/api/reservations/future-book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\": \"r1\", \"slotStart\": \"2026-11-10T12:07:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Slot start time must be on a 5-minute boundary with zero seconds and nanos"));
    }

    @Test
    @DisplayName("404 Not Found when room does not exist")
    void futureBook_roomNotFound_returns404() throws Exception {
        when(futureBookingService.reserveFutureSlot(any(), any()))
                .thenThrow(new ResourceNotFoundException("Room not found with ID: r-999"));

        mockMvc.perform(post("/api/reservations/future-book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\": \"r-999\", \"slotStart\": \"2026-11-10T12:15:00Z\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Room not found with ID: r-999"));
    }

    @Test
    @DisplayName("409 Conflict when user reaches reservation limit")
    void futureBook_limitReached_returns409() throws Exception {
        when(futureBookingService.reserveFutureSlot(any(), any()))
                .thenThrow(new ResourceConflictException("You have reached your limit of 12 upcoming reservations"));

        mockMvc.perform(post("/api/reservations/future-book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\": \"r1\", \"slotStart\": \"2026-11-10T12:15:00Z\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("You have reached your limit of 12 upcoming reservations"));
    }

    @Test
    @DisplayName("409 Conflict when user has already reserved this slot")
    void futureBook_alreadyReservedByUser_returns409() throws Exception {
        when(futureBookingService.reserveFutureSlot(any(), any()))
                .thenThrow(new ResourceConflictException("You have already reserved this room for this interval"));

        mockMvc.perform(post("/api/reservations/future-book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\": \"r1\", \"slotStart\": \"2026-11-10T12:15:00Z\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("You have already reserved this room for this interval"));
    }

    @Test
    @DisplayName("409 Conflict when slot is already held and inside 10-minute cutoff")
    void futureBook_heldInside10m_returns409() throws Exception {
        when(futureBookingService.reserveFutureSlot(any(), any()))
                .thenThrow(new ResourceConflictException("This slot is already taken and cannot be queued within 10 minutes of start"));

        mockMvc.perform(post("/api/reservations/future-book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\": \"r1\", \"slotStart\": \"2026-11-10T12:05:00Z\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("This slot is already taken and cannot be queued within 10 minutes of start"));
    }

    @Test
    @DisplayName("400 Bad Request when cancelling inside 10 minutes of slot start")
    void cancel_inside10m_returns400() throws Exception {
        when(cancelService.cancelReservation(any(), any()))
                .thenThrow(new IllegalArgumentException("Cannot cancel within 10 minutes of the slot start time"));

        mockMvc.perform(post("/api/reservations/cancel/res-123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Cannot cancel within 10 minutes of the slot start time"));
    }

    @Test
    @DisplayName("404 Not Found when cancelling nonexistent reservation")
    void cancel_notFound_returns404() throws Exception {
        when(cancelService.cancelReservation(any(), any()))
                .thenThrow(new ResourceNotFoundException("Reservation not found with ID: res-unknown"));

        mockMvc.perform(post("/api/reservations/cancel/res-unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Reservation not found with ID: res-unknown"));
    }
}
