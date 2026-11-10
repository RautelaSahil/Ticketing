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
