export interface User {
  id: string;
  username: string;
  profileName: string;
  roles: string[];
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  profileName: string;
  password: string;
}

export interface RoomMetadata {
  floor?: number;
  hasProjector?: boolean;
}

export interface RoomGridItem {
  roomId: string;
  roomNumber: string;
  capacity: number;
  metadata?: RoomMetadata;
  occupied: boolean;
  yours: boolean;
}

export interface GridResponse {
  serverTime: string;
  slotStart: string;
  slotEnd: string;
  maxRoomsPerRequest: number;
  rooms: RoomGridItem[];
}

export interface ActiveReservation {
  reservationId: string;
  roomId: string;
  roomNumber: string;
  capacity: number;
  metadata?: RoomMetadata;
  slotStart: string;
  slotEnd: string;
  remainingSeconds: number;
}

export interface ActiveRoomsResponse {
  serverTime: string;
  activeReservations: ActiveReservation[];
}

export interface BookRoomRequest {
  roomId: string;
  slotStart: string;
}

export interface ReservationResponse {
  reservationId: string;
  roomId: string;
  roomNumber: string;
  slotStart: string;
  slotEnd: string;
  status: string;
  createdAt: string;
}

export interface LeaveResponse {
  message: string;
  reservationId: string;
  status: string;
}

export interface BookBatchRequest {
  roomIds: string[];
  slotStart: string;
}

export interface BatchBookingResponse {
  slotStart: string;
  slotEnd: string;
  reservations: ReservationResponse[];
  message: string;
}

export interface BatchBookingError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  unavailableRooms?: string[];
}

export type SlotAction = 'BOOK' | 'JOIN_QUEUE' | 'UNAVAILABLE' | 'YOURS_BOOKED' | 'YOURS_SCHEDULED';

export interface SlotTimelineItem {
  slotStart: string;
  slotEnd: string;
  action: SlotAction;
  waitingCount: number;
  cancellable: boolean;
  cancellationDeadline?: string;
}

export interface SlotTimelineResponse {
  roomId: string;
  roomNumber: string;
  serverTime: string;
  windowStart: string;
  windowEnd: string;
  slots: SlotTimelineItem[];
}

export interface FutureBookRequest {
  roomId: string;
  slotStart: string;
}

export interface FutureBookingResponse {
  reservationId: string;
  roomId: string;
  roomNumber: string;
  slotStart: string;
  slotEnd: string;
  status: 'BOOKED' | 'SCHEDULED';
  message: string;
}

export interface CancelReservationResponse {
  reservationId: string;
  previousStatus: 'BOOKED' | 'SCHEDULED';
  cancelledAt: string;
  message: string;
}

export interface FutureReservationSummary {
  reservationId: string;
  roomId: string;
  roomNumber: string;
  slotStart: string;
  slotEnd: string;
  status: 'BOOKED' | 'SCHEDULED';
  cancellable: boolean;
  cancellationDeadline: string;
  createdAt: string;
}

export interface MyFutureReservationsResponse {
  serverTime: string;
  totalCount: number;
  reservations: FutureReservationSummary[];
}

