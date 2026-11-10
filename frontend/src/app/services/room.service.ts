import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ActiveRoomsResponse,
  BookRoomRequest,
  GridResponse,
  LeaveResponse,
  ReservationResponse,
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

  getActiveRooms(): Observable<ActiveRoomsResponse> {
    return this.http.get<ActiveRoomsResponse>('/api/reservations/active');
  }

  leaveRoom(reservationId: string): Observable<LeaveResponse> {
    return this.http.post<LeaveResponse>(`/api/reservations/leave/${reservationId}`, {});
  }
}
