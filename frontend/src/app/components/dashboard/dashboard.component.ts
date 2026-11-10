import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { RoomService } from '../../services/room.service';
import { ActiveReservation, RoomGridItem } from '../../models/app.models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="dashboard-layout">
      <!-- Top Navigation -->
      <header class="top-nav">
        <div class="brand">
          <span class="logo-icon">🏢</span>
          <h1>Room Reservation System</h1>
        </div>
        <div class="user-meta">
          @if (authService.currentUser(); as user) {
            <span class="user-greeting">
              Logged in as: <strong>{{ user.profileName }}</strong>
              <span class="user-handle">({{ user.username }})</span>
            </span>
            <button (click)="onLogout()" class="btn-logout">Log Out</button>
          }
        </div>
      </header>

      <main class="main-content">
        <!-- Toast / Alerts -->
        @if (notificationMessage()) {
          <div class="banner-toast" [class]="notificationType()">
            {{ notificationMessage() }}
            <button class="btn-close" (click)="clearNotification()">✕</button>
          </div>
        }

        <!-- 1. MY ACTIVE ROOM PANEL -->
        <section class="section active-panel-section">
          <div class="section-header">
            <h2>📍 My Active Occupancy</h2>
            <span class="section-tagline">Rooms you currently occupy in the active 5-minute interval</span>
          </div>

          @if (activeReservations().length > 0) {
            <div class="active-cards-grid">
              @for (res of activeReservations(); track res.reservationId) {
                <div class="active-room-card">
                  <div class="active-card-top">
                    <span class="active-badge">ACTIVE OCCUPANCY</span>
                    <span class="timer-display">⏱️ {{ formatRemaining(res.remainingSeconds) }}</span>
                  </div>
                  <div class="room-title">Room {{ res.roomNumber }}</div>
                  <div class="room-details">
                    <span>👥 Capacity: {{ res.capacity }}</span>
                    @if (res.metadata?.floor) {
                      <span>🏢 Floor {{ res.metadata?.floor }}</span>
                    }
                    @if (res.metadata?.hasProjector) {
                      <span>📽️ Projector Available</span>
                    }
                  </div>
                  <div class="time-window">
                    Slot: {{ res.slotStart | date:'HH:mm:ss' }} – {{ res.slotEnd | date:'HH:mm:ss' }}
                  </div>
                  <button (click)="openLeaveModal(res)" class="btn-leave">
                    Leave Room Now
                  </button>
                </div>
              }
            </div>
          } @else {
            <div class="empty-active-box">
              <span class="empty-icon">🪑</span>
              <p>You do not currently occupy any room in this active 5-minute window.</p>
              <small>If you booked a room for the next interval, it will appear here once that slot starts.</small>
            </div>
          }
        </section>

        <!-- 2. NEXT-INTERVAL ROOM GRID -->
        <section class="section grid-section">
          <div class="section-header grid-header-row">
            <div>
              <h2>🗓️ Next-Interval Room Availability</h2>
              <p class="slot-banner">
                Booking Interval: <strong>{{ slotStart() | date:'HH:mm:ss' }} – {{ slotEnd() | date:'HH:mm:ss' }}</strong>
              </p>
            </div>
            <div class="slot-flip-countdown">
              <span class="countdown-label">Interval Flips In:</span>
              <span class="countdown-value">{{ formatRemaining(secondsUntilFlip()) }}</span>
            </div>
          </div>

          <div class="room-grid">
            @for (room of rooms(); track room.roomId) {
              <div
                class="room-card"
                [class.card-yours]="room.yours"
                [class.card-occupied]="room.occupied && !room.yours"
                [class.card-available]="!room.occupied"
              >
                <div class="card-status-strip">
                  @if (room.yours) {
                    <span class="badge badge-yours">YOUR RESERVATION</span>
                  } @else if (room.occupied) {
                    <span class="badge badge-occupied">OCCUPIED</span>
                  } @else {
                    <span class="badge badge-available">AVAILABLE</span>
                  }
                </div>

                <div class="card-body">
                  <div class="card-room-number">Room {{ room.roomNumber }}</div>
                  <div class="card-specs">
                    <span>👥 Capacity: {{ room.capacity }}</span>
                    @if (room.metadata?.floor) {
                      <span>🏢 Floor {{ room.metadata?.floor }}</span>
                    }
                    @if (room.metadata?.hasProjector) {
                      <span>📽️ Projector</span>
                    }
                  </div>
                </div>

                <div class="card-footer">
                  @if (room.yours) {
                    <span class="note-yours">Starts at {{ slotStart() | date:'HH:mm' }}</span>
                  } @else if (room.occupied) {
                    <button class="btn-action btn-disabled" disabled>Unavailable</button>
                  } @else {
                    <button (click)="openBookModal(room)" class="btn-action btn-book">
                      Book Room
                    </button>
                  }
                </div>
              </div>
            }
          </div>
        </section>
      </main>

      <!-- Confirmation Modal: Book Room -->
      @if (pendingBookRoom()) {
        <div class="modal-backdrop">
          <div class="modal-card">
            <h3>Confirm Room Reservation</h3>
            <p>
              Are you sure you want to reserve <strong>Room {{ pendingBookRoom()?.roomNumber }}</strong>?
            </p>
            <div class="modal-info-box">
              <div><strong>Slot Start:</strong> {{ slotStart() | date:'HH:mm:ss' }}</div>
              <div><strong>Slot End:</strong> {{ slotEnd() | date:'HH:mm:ss' }}</div>
              <div><strong>Duration:</strong> Exactly 5 minutes</div>
            </div>
            <p class="modal-warning">
              ⚠️ <em>Notice: Every room reservation lasts exactly 5 minutes. Bookings for upcoming slots cannot be cancelled before slot start.</em>
            </p>
            <div class="modal-actions">
              <button class="btn-secondary" (click)="closeBookModal()" [disabled]="actionLoading()">Cancel</button>
              <button class="btn-primary" (click)="confirmBooking()" [disabled]="actionLoading()">
                {{ actionLoading() ? 'Booking...' : 'Confirm Reservation' }}
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Confirmation Modal: Leave Room -->
      @if (pendingLeaveReservation()) {
        <div class="modal-backdrop">
          <div class="modal-card">
            <h3>Vacate Occupied Room</h3>
            <p>
              Are you sure you want to leave <strong>Room {{ pendingLeaveReservation()?.roomNumber }}</strong>?
            </p>
            <p class="modal-warning">
              This will immediately vacate the room for the remainder of this 5-minute interval.
            </p>
            <div class="modal-actions">
              <button class="btn-secondary" (click)="closeLeaveModal()" [disabled]="actionLoading()">Cancel</button>
              <button class="btn-danger" (click)="confirmLeave()" [disabled]="actionLoading()">
                {{ actionLoading() ? 'Leaving...' : 'Confirm Leave' }}
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .dashboard-layout {
      min-height: 100vh;
      background: #f8fafc;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      color: #1e293b;
    }
    .top-nav {
      background: #ffffff;
      border-bottom: 1px solid #e2e8f0;
      padding: 1rem 2rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .brand { display: flex; align-items: center; gap: 0.75rem; }
    .brand h1 { margin: 0; font-size: 1.25rem; color: #0f172a; }
    .logo-icon { font-size: 1.5rem; }
    .user-meta { display: flex; align-items: center; gap: 1rem; }
    .user-greeting { font-size: 0.9rem; color: #475569; }
    .user-handle { color: #64748b; font-size: 0.8rem; }
    .btn-logout {
      background: #f1f5f9;
      border: 1px solid #cbd5e1;
      padding: 0.4rem 0.85rem;
      border-radius: 6px;
      font-size: 0.85rem;
      cursor: pointer;
      font-weight: 500;
    }
    .btn-logout:hover { background: #e2e8f0; }

    .main-content {
      max-width: 1200px;
      margin: 2rem auto;
      padding: 0 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 2rem;
    }

    .banner-toast {
      padding: 0.85rem 1.25rem;
      border-radius: 8px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 0.95rem;
      font-weight: 500;
    }
    .banner-toast.success { background: #dcfce7; color: #166534; border: 1px solid #bbf7d0; }
    .banner-toast.error { background: #fee2e2; color: #991b1b; border: 1px solid #fecaca; }
    .btn-close { background: none; border: none; font-size: 1.1rem; cursor: pointer; color: inherit; }

    .section {
      background: #ffffff;
      border-radius: 12px;
      border: 1px solid #e2e8f0;
      padding: 1.75rem;
      box-shadow: 0 1px 3px rgba(0,0,0,0.03);
    }
    .section-header h2 { margin: 0 0 0.25rem; font-size: 1.25rem; color: #0f172a; }
    .section-tagline { font-size: 0.85rem; color: #64748b; }

    /* Active Cards */
    .active-cards-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 1.25rem;
      margin-top: 1.25rem;
    }
    .active-room-card {
      background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%);
      border: 2px solid #86efac;
      border-radius: 10px;
      padding: 1.25rem;
      box-shadow: 0 4px 12px rgba(34, 197, 94, 0.12);
    }
    .active-card-top {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.75rem;
    }
    .active-badge {
      background: #15803d;
      color: white;
      font-size: 0.7rem;
      font-weight: 700;
      letter-spacing: 0.5px;
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
    }
    .timer-display {
      font-size: 1.1rem;
      font-weight: 700;
      color: #166534;
      font-variant-numeric: tabular-nums;
    }
    .room-title { font-size: 1.35rem; font-weight: 700; color: #0f172a; margin-bottom: 0.5rem; }
    .room-details { display: flex; flex-wrap: wrap; gap: 0.75rem; font-size: 0.85rem; color: #334155; margin-bottom: 0.75rem; }
    .time-window { font-size: 0.8rem; color: #475569; margin-bottom: 1rem; }
    .btn-leave {
      width: 100%;
      padding: 0.65rem;
      background: #dc2626;
      color: white;
      border: none;
      border-radius: 6px;
      font-weight: 600;
      cursor: pointer;
      transition: background 0.2s;
    }
    .btn-leave:hover { background: #b91c1c; }

    .empty-active-box {
      margin-top: 1.25rem;
      padding: 2rem;
      text-align: center;
      background: #f8fafc;
      border: 1px dashed #cbd5e1;
      border-radius: 8px;
    }
    .empty-icon { font-size: 2rem; display: block; margin-bottom: 0.5rem; }
    .empty-active-box p { margin: 0 0 0.25rem; font-weight: 500; color: #475569; }
    .empty-active-box small { color: #94a3b8; }

    /* Grid Section */
    .grid-header-row {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 1.5rem;
    }
    .slot-banner { margin: 0.35rem 0 0; font-size: 0.95rem; color: #334155; }
    .slot-flip-countdown {
      background: #f1f5f9;
      border: 1px solid #cbd5e1;
      padding: 0.5rem 1rem;
      border-radius: 8px;
      text-align: right;
    }
    .countdown-label { display: block; font-size: 0.75rem; color: #64748b; text-transform: uppercase; font-weight: 600; }
    .countdown-value { font-size: 1.2rem; font-weight: 800; color: #2563eb; font-variant-numeric: tabular-nums; }

    .room-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 1.25rem;
    }
    .room-card {
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      background: #ffffff;
      padding: 1.25rem;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      transition: transform 0.15s, box-shadow 0.15s;
    }
    .room-card:hover { transform: translateY(-2px); box-shadow: 0 6px 16px rgba(0,0,0,0.06); }
    .card-available { border-top: 4px solid #22c55e; }
    .card-occupied { border-top: 4px solid #ef4444; background: #fffbfb; }
    .card-yours { border-top: 4px solid #3b82f6; background: #f8faff; }

    .badge {
      display: inline-block;
      font-size: 0.7rem;
      font-weight: 700;
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      letter-spacing: 0.4px;
    }
    .badge-available { background: #dcfce7; color: #15803d; }
    .badge-occupied { background: #fee2e2; color: #b91c1c; }
    .badge-yours { background: #dbeafe; color: #1e40af; }

    .card-body { margin: 1rem 0; }
    .card-room-number { font-size: 1.25rem; font-weight: 700; color: #0f172a; margin-bottom: 0.5rem; }
    .card-specs { display: flex; flex-direction: column; gap: 0.35rem; font-size: 0.85rem; color: #64748b; }

    .card-footer { margin-top: 0.5rem; }
    .btn-action {
      width: 100%;
      padding: 0.65rem;
      border-radius: 6px;
      font-weight: 600;
      font-size: 0.9rem;
      cursor: pointer;
      border: none;
    }
    .btn-book { background: #16a34a; color: white; }
    .btn-book:hover { background: #15803d; }
    .btn-disabled { background: #e2e8f0; color: #94a3b8; cursor: not-allowed; }
    .note-yours { display: block; text-align: center; font-size: 0.85rem; font-weight: 600; color: #2563eb; }

    /* Modals */
    .modal-backdrop {
      position: fixed;
      top: 0; left: 0; right: 0; bottom: 0;
      background: rgba(15, 23, 42, 0.6);
      display: flex;
      justify-content: center;
      align-items: center;
      z-index: 1000;
    }
    .modal-card {
      background: white;
      padding: 2rem;
      border-radius: 12px;
      max-width: 440px;
      width: 90%;
      box-shadow: 0 10px 30px rgba(0,0,0,0.2);
    }
    .modal-card h3 { margin: 0 0 0.75rem; color: #0f172a; }
    .modal-info-box {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
      padding: 0.75rem 1rem;
      margin: 1rem 0;
      font-size: 0.875rem;
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }
    .modal-warning { font-size: 0.825rem; color: #b45309; line-height: 1.4; margin-bottom: 1.5rem; }
    .modal-actions { display: flex; justify-content: flex-end; gap: 0.75rem; }
    .btn-secondary {
      background: #f1f5f9;
      border: 1px solid #cbd5e1;
      padding: 0.6rem 1rem;
      border-radius: 6px;
      font-weight: 600;
      cursor: pointer;
    }
    .btn-primary {
      background: #2563eb;
      color: white;
      border: none;
      padding: 0.6rem 1.2rem;
      border-radius: 6px;
      font-weight: 600;
      cursor: pointer;
    }
    .btn-danger {
      background: #dc2626;
      color: white;
      border: none;
      padding: 0.6rem 1.2rem;
      border-radius: 6px;
      font-weight: 600;
      cursor: pointer;
    }
  `],
})
export class DashboardComponent implements OnInit, OnDestroy {
  authService = inject(AuthService);
  private roomService = inject(RoomService);
  private router = inject(Router);

  rooms = signal<RoomGridItem[]>([]);
  activeReservations = signal<ActiveReservation[]>([]);
  slotStart = signal<string>('');
  slotEnd = signal<string>('');
  serverTime = signal<string>('');
  secondsUntilFlip = signal<number>(0);

  notificationMessage = signal<string | null>(null);
  notificationType = signal<'success' | 'error'>('success');

  pendingBookRoom = signal<RoomGridItem | null>(null);
  pendingLeaveReservation = signal<ActiveReservation | null>(null);
  actionLoading = signal<boolean>(false);

  private timerIntervalId: any = null;
  private pollIntervalId: any = null;
  private serverTimeOffsetMs = 0;

  ngOnInit(): void {
    this.refreshAll();

    // 1-second countdown ticker
    this.timerIntervalId = setInterval(() => {
      this.tick();
    }, 1000);

    // 15-second polling for multi-user sync
    this.pollIntervalId = setInterval(() => {
      this.refreshAll();
    }, 15000);
  }

  ngOnDestroy(): void {
    if (this.timerIntervalId) clearInterval(this.timerIntervalId);
    if (this.pollIntervalId) clearInterval(this.pollIntervalId);
  }

  refreshAll(): void {
    this.loadGrid();
    this.loadActiveRooms();
  }

  loadGrid(): void {
    this.roomService.getGrid().subscribe({
      next: (res) => {
        this.rooms.set(res.rooms);
        this.slotStart.set(res.slotStart);
        this.slotEnd.set(res.slotEnd);
        this.serverTime.set(res.serverTime);

        const serverNowMs = new Date(res.serverTime).getTime();
        this.serverTimeOffsetMs = serverNowMs - Date.now();

        this.updateFlipCountdown();
      },
      error: (err) => {
        if (err.status === 401) {
          this.router.navigate(['/login']);
        }
      },
    });
  }

  loadActiveRooms(): void {
    this.roomService.getActiveRooms().subscribe({
      next: (res) => {
        this.activeReservations.set(res.activeReservations);
      },
      error: (err) => {
        if (err.status === 401) {
          this.router.navigate(['/login']);
        }
      },
    });
  }

  private tick(): void {
    const currentServerTimeMs = Date.now() + this.serverTimeOffsetMs;

    // Tick down active reservations
    this.activeReservations.update((reservations) =>
      reservations.map((res) => {
        const endMs = new Date(res.slotEnd).getTime();
        const rem = Math.max(0, Math.floor((endMs - currentServerTimeMs) / 1000));
        return { ...res, remainingSeconds: rem };
      }).filter((res) => res.remainingSeconds > 0)
    );

    // Tick down flip timer
    this.updateFlipCountdown();
  }

  private updateFlipCountdown(): void {
    if (!this.slotStart()) return;
    const currentServerTimeMs = Date.now() + this.serverTimeOffsetMs;
    const targetSlotStartMs = new Date(this.slotStart()).getTime();
    const remaining = Math.max(0, Math.floor((targetSlotStartMs - currentServerTimeMs) / 1000));
    this.secondsUntilFlip.set(remaining);

    // If flip timer reaches zero, refresh grid to roll over
    if (remaining === 0) {
      setTimeout(() => this.refreshAll(), 1000);
    }
  }

  openBookModal(room: RoomGridItem): void {
    this.pendingBookRoom.set(room);
  }

  closeBookModal(): void {
    this.pendingBookRoom.set(null);
  }

  confirmBooking(): void {
    const room = this.pendingBookRoom();
    if (!room) return;

    this.actionLoading.set(true);
    this.roomService.bookRoom({ roomId: room.roomId, slotStart: this.slotStart() }).subscribe({
      next: (res) => {
        this.actionLoading.set(false);
        this.closeBookModal();
        this.showNotification(`Successfully reserved Room ${res.roomNumber} for the next interval!`, 'success');
        this.refreshAll();
      },
      error: (err) => {
        this.actionLoading.set(false);
        this.closeBookModal();
        const message = err?.error?.message || 'Failed to book room';
        this.showNotification(message, 'error');
        this.refreshAll();
      },
    });
  }

  openLeaveModal(res: ActiveReservation): void {
    this.pendingLeaveReservation.set(res);
  }

  closeLeaveModal(): void {
    this.pendingLeaveReservation.set(null);
  }

  confirmLeave(): void {
    const res = this.pendingLeaveReservation();
    if (!res) return;

    this.actionLoading.set(true);
    this.roomService.leaveRoom(res.reservationId).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.closeLeaveModal();
        this.showNotification(`Vacated Room ${res.roomNumber} successfully.`, 'success');
        this.refreshAll();
      },
      error: (err) => {
        this.actionLoading.set(false);
        this.closeLeaveModal();
        const message = err?.error?.message || 'Failed to vacate room';
        this.showNotification(message, 'error');
        this.refreshAll();
      },
    });
  }

  showNotification(msg: string, type: 'success' | 'error'): void {
    this.notificationMessage.set(msg);
    this.notificationType.set(type);
    setTimeout(() => {
      if (this.notificationMessage() === msg) {
        this.notificationMessage.set(null);
      }
    }, 6000);
  }

  clearNotification(): void {
    this.notificationMessage.set(null);
  }

  formatRemaining(seconds: number): string {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  }

  onLogout(): void {
    this.authService.logout().subscribe({
      next: () => {
        this.router.navigate(['/login']);
      },
      error: () => {
        this.router.navigate(['/login']);
      },
    });
  }
}
