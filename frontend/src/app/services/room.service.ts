import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ActiveRoomsResponse,
  BatchBookingResponse,
  BookBatchRequest,
  BookRoomRequest,
  CancelReservationResponse,
  FutureBookingResponse,
  FutureBookRequest,
  GridResponse,
  LeaveResponse,
  MyFutureReservationsResponse,
  ReservationResponse,
  SlotTimelineResponse,
} from '../models/app.models';

@Injectable({
  providedIn: 'root',
})
export class RoomService {
  private http = inject(HttpClient);

  getGrid(): Observable<GridResponse> {
    return this.http.get<GridResponse>('/api/rooms/grid');
  }

  bookRoom(request: BookRoomRequest): Observable<ReservationResponse> {
    return this.http.post<ReservationResponse>('/api/reservations/book', request);
  }

  bookBatch(request: BookBatchRequest): Observable<BatchBookingResponse> {
    return this.http.post<BatchBookingResponse>('/api/reservations/book-batch', request);
  }

  getActiveRooms(): Observable<ActiveRoomsResponse> {
    return this.http.get<ActiveRoomsResponse>('/api/reservations/active');
  }

  leaveRoom(reservationId: string): Observable<LeaveResponse> {
    return this.http.post<LeaveResponse>(`/api/reservations/leave/${reservationId}`, {});
  }

  // Phase 4: Room 24-hour timeline
  getRoomTimeline(roomId: string): Observable<SlotTimelineResponse> {
    return this.http.get<SlotTimelineResponse>(`/api/rooms/${roomId}/timeline`);
  }

  // Phase 4: Future book or join queue
  futureBook(request: FutureBookRequest): Observable<FutureBookingResponse> {
    return this.http.post<FutureBookingResponse>('/api/reservations/future-book', request);
  }

  // Phase 4: My upcoming reservations
  getMyFutureReservations(): Observable<MyFutureReservationsResponse> {
    return this.http.get<MyFutureReservationsResponse>('/api/reservations/my-future');
  }

  // Phase 4: Cancel upcoming reservation
  cancelReservation(reservationId: string): Observable<CancelReservationResponse> {
    return this.http.post<CancelReservationResponse>(`/api/reservations/cancel/${reservationId}`, {});
  }
}
