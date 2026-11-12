import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
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
            <div class="toast-content">
              <span>{{ notificationMessage() }}</span>
              @if (unavailableRoomsList().length > 0) {
                <div class="unavailable-tags">
                  Unavailable:
                  @for (rm of unavailableRoomsList(); track rm) {
                    <span class="tag-rm">Room {{ rm }}</span>
                  }
                </div>
              }
            </div>
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
              <small>If you booked rooms for the next interval, they will appear here once that slot starts.</small>
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

          <!-- Phase 3: Multi-Room Batch Selection Control Bar -->
          <div class="batch-control-bar">
            <div class="batch-meta">
              <span class="selection-pill" [class.cap-reached]="isCapReached()">
                📌 Selected: <strong>{{ selectedRoomIds().length }}</strong> of {{ maxRoomsPerRequest() }} rooms
              </span>
              @if (isCapReached()) {
                <span class="cap-warning-badge">⚠️ Limit reached (Max {{ maxRoomsPerRequest() }} per request)</span>
              } @else if (selectedRoomIds().length === 0) {
                <span class="selection-hint">Click free rooms to select up to {{ maxRoomsPerRequest() }} rooms for an all-or-nothing batch booking.</span>
              }
            </div>
            <div class="batch-actions">
              @if (selectedRoomIds().length > 0) {
                <button class="btn-clear" (click)="clearSelection()" [disabled]="actionLoading()">
                  Clear Selection
                </button>
                <button class="btn-book-batch" (click)="openBatchModal()" [disabled]="actionLoading()">
                  ⚡ Book Selected ({{ selectedRoomIds().length }} Room{{ selectedRoomIds().length > 1 ? 's' : '' }})
                </button>
              }
            </div>
          </div>

          <div class="room-grid">
            @for (room of rooms(); track room.roomId) {
              <div
                class="room-card"
                [class.card-yours]="room.yours"
                [class.card-occupied]="room.occupied && !room.yours"
                [class.card-available]="!room.occupied"
                [class.card-selected]="isSelected(room.roomId)"
                [class.card-cap-disabled]="!room.occupied && !isSelected(room.roomId) && isCapReached()"
                (click)="onCardClick(room)"
              >
                <div class="card-status-strip">
                  @if (room.yours) {
                    <span class="badge badge-yours">YOUR RESERVATION</span>
                  } @else if (room.occupied) {
                    <span class="badge badge-occupied">OCCUPIED</span>
                  } @else if (isSelected(room.roomId)) {
                    <span class="badge badge-selected">✓ SELECTED</span>
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
                  } @else if (isSelected(room.roomId)) {
                    <button
                      class="btn-action btn-selected"
                      (click)="$event.stopPropagation(); toggleRoomSelection(room)"
                    >
                      ✓ Selected (Click to Deselect)
                    </button>
                  } @else if (isCapReached()) {
                    <button class="btn-action btn-disabled" disabled title="Maximum 5 rooms per request reached">
                      Limit Reached (5/5)
                    </button>
                  } @else {
                    <button
                      class="btn-action btn-book"
                      (click)="$event.stopPropagation(); toggleRoomSelection(room)"
                    >
                      + Select Room
                    </button>
                  }
                </div>
              </div>
            }
          </div>
        </section>
      </main>

      <!-- Confirmation Modal: Batch Booking (All-or-Nothing) -->
      @if (pendingBatchModal()) {
        <div class="modal-backdrop">
          <div class="modal-card">
            <h3>Confirm Multi-Room Batch Booking</h3>
            <p>
              You are reserving <strong>{{ selectedRoomIds().length }} room(s)</strong> together:
            </p>
            <div class="modal-room-chips">
              @for (r of getSelectedRoomObjects(); track r.roomId) {
                <span class="room-chip">
                  Room <strong>{{ r.roomNumber }}</strong> (Cap: {{ r.capacity }})
                </span>
              }
            </div>
            <div class="modal-info-box">
              <div><strong>Slot Start:</strong> {{ slotStart() | date:'HH:mm:ss' }}</div>
              <div><strong>Slot End:</strong> {{ slotEnd() | date:'HH:mm:ss' }}</div>
              <div><strong>Duration:</strong> Exactly 5 minutes</div>
              <div><strong>Total Rooms:</strong> {{ selectedRoomIds().length }} / {{ maxRoomsPerRequest() }}</div>
            </div>
            <p class="modal-all-or-nothing">
              🛡️ <strong>All-or-Nothing ACID Guarantee:</strong> If even one room is taken by another user before confirmation, the entire transaction rolls back and zero rooms are booked.
            </p>
            <div class="modal-actions">
              <button class="btn-secondary" (click)="closeBatchModal()" [disabled]="actionLoading()">Cancel</button>
              <button class="btn-primary" (click)="confirmBatchBooking()" [disabled]="actionLoading()">
                {{ actionLoading() ? 'Reserving Batch...' : 'Confirm All-or-Nothing Booking' }}
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
      margin: 0 auto;
      padding: 2rem 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 2.5rem;
    }

    /* Toast */
    .banner-toast {
      padding: 1rem 1.25rem;
      border-radius: 8px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-weight: 500;
      font-size: 0.95rem;
      box-shadow: 0 4px 12px rgba(0,0,0,0.05);
    }
    .banner-toast.success { background: #dcfce7; color: #166534; border: 1px solid #bbf7d0; }
    .banner-toast.error { background: #fee2e2; color: #991b1b; border: 1px solid #fecaca; }
    .toast-content { display: flex; flex-direction: column; gap: 0.35rem; }
    .unavailable-tags { display: flex; align-items: center; gap: 0.5rem; font-size: 0.85rem; font-weight: 600; }
    .tag-rm { background: #b91c1c; color: white; padding: 0.15rem 0.5rem; border-radius: 4px; }
    .btn-close { background: none; border: none; font-size: 1.1rem; cursor: pointer; color: inherit; }

    /* Sections */
    .section {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 1.75rem;
      box-shadow: 0 1px 3px rgba(0,0,0,0.03);
    }
    .section-header { margin-bottom: 1.5rem; }
    .section-header h2 { margin: 0 0 0.25rem; font-size: 1.35rem; color: #0f172a; }
    .section-tagline { font-size: 0.875rem; color: #64748b; }

    /* Active Room Panel */
    .active-cards-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 1.25rem;
    }
    .active-room-card {
      background: #ecfdf5;
      border: 2px solid #10b981;
      border-radius: 10px;
      padding: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    .active-card-top {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .active-badge {
      background: #10b981;
      color: white;
      font-size: 0.7rem;
      font-weight: 700;
      letter-spacing: 0.05em;
      padding: 0.2rem 0.6rem;
      border-radius: 4px;
    }
    .timer-display {
      font-size: 1rem;
      font-weight: 700;
      color: #065f46;
      font-variant-numeric: tabular-nums;
    }
    .room-title { font-size: 1.25rem; font-weight: 700; color: #064e3b; }
    .room-details { display: flex; gap: 1rem; font-size: 0.85rem; color: #047857; flex-wrap: wrap; }
    .time-window { font-size: 0.8rem; color: #059669; font-weight: 500; }
    .btn-leave {
      background: #ef4444;
      color: white;
      border: none;
      padding: 0.6rem 1rem;
      border-radius: 6px;
      font-weight: 600;
      cursor: pointer;
      margin-top: 0.5rem;
    }
    .btn-leave:hover { background: #dc2626; }

    .empty-active-box {
      text-align: center;
      padding: 2.5rem 1rem;
      background: #f8fafc;
      border: 1px dashed #cbd5e1;
      border-radius: 8px;
    }
    .empty-icon { font-size: 2rem; display: block; margin-bottom: 0.5rem; }
    .empty-active-box p { margin: 0; font-weight: 500; color: #475569; }
    .empty-active-box small { color: #94a3b8; margin-top: 0.25rem; display: block; }

    /* Next-Interval Grid */
    .grid-header-row {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      flex-wrap: wrap;
      gap: 1rem;
      margin-bottom: 1rem;
    }
    .slot-banner { margin: 0.25rem 0 0; font-size: 0.95rem; color: #475569; }
    .slot-flip-countdown {
      background: #eff6ff;
      border: 1px solid #bfdbfe;
      padding: 0.6rem 1rem;
      border-radius: 8px;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .countdown-label { font-size: 0.8rem; font-weight: 600; color: #1e40af; text-transform: uppercase; }
    .countdown-value { font-size: 1.15rem; font-weight: 800; color: #1d4ed8; font-variant-numeric: tabular-nums; }

    /* Batch Selection Control Bar */
    .batch-control-bar {
      background: #f1f5f9;
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      padding: 0.85rem 1.25rem;
      margin-bottom: 1.5rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 1rem;
    }
    .batch-meta { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; }
    .selection-pill {
      background: white;
      border: 1px solid #cbd5e1;
      padding: 0.35rem 0.75rem;
      border-radius: 20px;
      font-size: 0.875rem;
      color: #334155;
    }
    .selection-pill.cap-reached {
      border-color: #f59e0b;
      background: #fffbeb;
      color: #92400e;
    }
    .cap-warning-badge {
      font-size: 0.8rem;
      color: #b45309;
      font-weight: 600;
    }
    .selection-hint { font-size: 0.825rem; color: #64748b; }
    .batch-actions { display: flex; align-items: center; gap: 0.75rem; }
    .btn-clear {
      background: white;
      border: 1px solid #cbd5e1;
      padding: 0.5rem 0.9rem;
      border-radius: 6px;
      font-size: 0.85rem;
      cursor: pointer;
      font-weight: 600;
      color: #475569;
    }
    .btn-clear:hover { background: #e2e8f0; }
    .btn-book-batch {
      background: #2563eb;
      color: white;
      border: none;
      padding: 0.55rem 1.2rem;
      border-radius: 6px;
      font-size: 0.9rem;
      font-weight: 700;
      cursor: pointer;
      box-shadow: 0 2px 4px rgba(37,99,235,0.2);
    }
    .btn-book-batch:hover { background: #1d4ed8; }

    /* Room Cards */
    .room-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
      gap: 1.5rem;
    }
    .room-card {
      border: 2px solid #e2e8f0;
      border-radius: 10px;
      background: white;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      transition: all 0.2s ease-in-out;
      cursor: default;
    }
    .room-card.card-available {
      border-color: #86efac;
      cursor: pointer;
    }
    .room-card.card-available:hover {
      border-color: #22c55e;
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(34,197,94,0.15);
    }
    .room-card.card-selected {
      border-color: #2563eb;
      background: #f0f7ff;
      box-shadow: 0 0 0 3px rgba(37,99,235,0.25);
    }
    .room-card.card-cap-disabled {
      opacity: 0.6;
      border-color: #cbd5e1;
      cursor: not-allowed;
    }
    .room-card.card-cap-disabled:hover {
      transform: none;
      box-shadow: none;
      border-color: #cbd5e1;
    }
    .room-card.card-occupied {
      border-color: #fca5a5;
      background: #fff5f5;
    }
    .room-card.card-yours {
      border-color: #93c5fd;
      background: #eff6ff;
    }

    .card-status-strip { padding: 0.5rem 1rem; border-bottom: 1px solid #f1f5f9; }
    .badge {
      font-size: 0.7rem;
      font-weight: 800;
      letter-spacing: 0.05em;
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
    }
    .badge-available { background: #dcfce7; color: #15803d; }
    .badge-selected { background: #2563eb; color: white; }
    .badge-occupied { background: #fee2e2; color: #b91c1c; }
    .badge-yours { background: #dbeafe; color: #1d4ed8; }

    .card-body { padding: 1.25rem 1rem; flex: 1; }
    .card-room-number { font-size: 1.25rem; font-weight: 700; color: #0f172a; margin-bottom: 0.5rem; }
    .card-specs { display: flex; flex-direction: column; gap: 0.25rem; font-size: 0.85rem; color: #64748b; }

    .card-footer { padding: 0.85rem 1rem; background: #fafafa; border-top: 1px solid #f1f5f9; }
    .btn-action {
      width: 100%;
      padding: 0.6rem;
      border-radius: 6px;
      font-weight: 600;
      font-size: 0.875rem;
      cursor: pointer;
      border: none;
    }
    .btn-book { background: #16a34a; color: white; }
    .btn-book:hover { background: #15803d; }
    .btn-selected { background: #2563eb; color: white; }
    .btn-selected:hover { background: #1d4ed8; }
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
      max-width: 480px;
      width: 90%;
      box-shadow: 0 10px 30px rgba(0,0,0,0.2);
    }
    .modal-card h3 { margin: 0 0 0.75rem; color: #0f172a; }
    .modal-room-chips { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-bottom: 1rem; }
    .room-chip { background: #eff6ff; border: 1px solid #bfdbfe; color: #1e40af; padding: 0.35rem 0.75rem; border-radius: 6px; font-size: 0.85rem; }
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
    .modal-all-or-nothing { font-size: 0.825rem; color: #1e3a8a; line-height: 1.4; margin-bottom: 1.5rem; background: #f0f7ff; padding: 0.75rem; border-radius: 6px; border-left: 3px solid #2563eb; }
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
  maxRoomsPerRequest = signal<number>(5);

  // Multi-room batch selection state
  selectedRoomIds = signal<string[]>([]);
  isCapReached = computed(() => this.selectedRoomIds().length >= this.maxRoomsPerRequest());

  notificationMessage = signal<string | null>(null);
  notificationType = signal<'success' | 'error'>('success');
  unavailableRoomsList = signal<string[]>([]);

  pendingBatchModal = signal<boolean>(false);
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

    // 10-second polling for multi-user sync
    this.pollIntervalId = setInterval(() => {
      this.refreshAll();
    }, 10000);
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
        this.maxRoomsPerRequest.set(res.maxRoomsPerRequest || 5);

        // Drop any selected rooms that became occupied by others
        this.selectedRoomIds.update((ids) =>
          ids.filter((id) => {
            const rm = res.rooms.find((r) => r.roomId === id);
            return rm && !rm.occupied;
          })
        );

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

    // If flip timer reaches zero, clear selection and reload grid for next slot
    if (remaining === 0) {
      this.clearSelection();
      setTimeout(() => this.refreshAll(), 1000);
    }
  }

  // --- Multi-Room Selection Methods ---

  isSelected(roomId: string): boolean {
    return this.selectedRoomIds().includes(roomId);
  }

  onCardClick(room: RoomGridItem): void {
    if (room.occupied || room.yours) return;
    this.toggleRoomSelection(room);
  }

  toggleRoomSelection(room: RoomGridItem): void {
    if (room.occupied || room.yours) return;

    if (this.isSelected(room.roomId)) {
      this.selectedRoomIds.update((ids) => ids.filter((id) => id !== room.roomId));
    } else {
      if (this.isCapReached()) {
        this.showNotification(`You can select up to ${this.maxRoomsPerRequest()} rooms per request.`, 'error');
        return;
      }
      this.selectedRoomIds.update((ids) => [...ids, room.roomId]);
    }
  }

  clearSelection(): void {
    this.selectedRoomIds.set([]);
  }

  getSelectedRoomObjects(): RoomGridItem[] {
    const ids = this.selectedRoomIds();
    return this.rooms().filter((r) => ids.includes(r.roomId));
  }

  openBatchModal(): void {
    if (this.selectedRoomIds().length === 0) return;
    this.pendingBatchModal.set(true);
  }

  closeBatchModal(): void {
    this.pendingBatchModal.set(false);
  }

  confirmBatchBooking(): void {
    const ids = this.selectedRoomIds();
    if (ids.length === 0) return;

    this.actionLoading.set(true);
    this.roomService.bookBatch({ roomIds: ids, slotStart: this.slotStart() }).subscribe({
      next: (res) => {
        this.actionLoading.set(false);
        this.closeBatchModal();
        this.clearSelection();
        this.showNotification(
          `Successfully reserved ${res.reservations.length} room(s) for interval ${this.formatTimeSlot(res.slotStart, res.slotEnd)}!`,
          'success'
        );
        this.refreshAll();
      },
      error: (err) => {
        this.actionLoading.set(false);
        this.closeBatchModal();

        const unavailable: string[] = err?.error?.unavailableRooms || [];
        this.unavailableRoomsList.set(unavailable);

        if (unavailable.length > 0) {
          // Drop the colliding rooms from current selection
          this.selectedRoomIds.update((currentIds) =>
            currentIds.filter((id) => {
              const rm = this.rooms().find((r) => r.roomId === id);
              return rm && !unavailable.includes(rm.roomNumber);
            })
          );
          this.showNotification(
            `Batch booking failed: Room(s) ${unavailable.join(', ')} were just reserved by another user. All-or-nothing rollback applied.`,
            'error'
          );
        } else {
          const message = err?.error?.message || 'Failed to book rooms';
          this.showNotification(message, 'error');
        }

        this.refreshAll();
      },
    });
  }

  // --- Leave Room Methods ---

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
      next: (resp) => {
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

  // --- Notifications & Helpers ---

  showNotification(msg: string, type: 'success' | 'error'): void {
    this.notificationMessage.set(msg);
    this.notificationType.set(type);
    if (type === 'success') {
      this.unavailableRoomsList.set([]);
    }
    setTimeout(() => {
      if (this.notificationMessage() === msg) {
        this.clearNotification();
      }
    }, 7000);
  }

  clearNotification(): void {
    this.notificationMessage.set(null);
    this.unavailableRoomsList.set([]);
  }

  formatRemaining(totalSeconds: number): string {
    const mins = Math.floor(totalSeconds / 60);
    const secs = totalSeconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  }

  formatTimeSlot(startIso: string, endIso: string): string {
    if (!startIso || !endIso) return '';
    const start = new Date(startIso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
    const end = new Date(endIso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
    return `${start} – ${end}`;
  }

  onLogout(): void {
    this.authService.logout().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login']),
    });
  }
}
