import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { RoomService } from '../../services/room.service';
import {
  ActiveReservation,
  FutureReservationSummary,
  RoomGridItem,
  SlotAction,
  SlotTimelineItem,
  SlotTimelineResponse,
} from '../../models/app.models';

interface HourlySlotGroup {
  hourLabel: string;
  slots: SlotTimelineItem[];
}

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

      <!-- Navigation Tabs -->
      <div class="nav-tabs-bar">
        <div class="tabs-container">
          <button
            class="tab-btn"
            [class.active]="activeTab() === 'next-interval'"
            (click)="switchTab('next-interval')"
          >
            ⚡ Next Interval Grid (5m)
          </button>
          <button
            class="tab-btn"
            [class.active]="activeTab() === 'timeline'"
            (click)="switchTab('timeline')"
          >
            📅 24-Hour Room Timeline
          </button>
          <button
            class="tab-btn"
            [class.active]="activeTab() === 'my-bookings'"
            (click)="switchTab('my-bookings')"
          >
            📋 My Upcoming Reservations
            @if (myUpcomingReservations().length > 0) {
              <span class="tab-badge">{{ myUpcomingReservations().length }}</span>
            }
          </button>
        </div>
      </div>

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

        <!-- ========================================== -->
        <!-- TAB 1: NEXT INTERVAL GRID (PHASES 1-3)    -->
        <!-- ========================================== -->
        @if (activeTab() === 'next-interval') {
          <!-- MY ACTIVE ROOM PANEL -->
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

          <!-- NEXT-INTERVAL ROOM GRID -->
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

            <!-- Multi-Room Batch Selection Control Bar -->
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
        }

        <!-- ========================================== -->
        <!-- TAB 2: 24-HOUR ROOM TIMELINE (PHASE 4)     -->
        <!-- ========================================== -->
        @if (activeTab() === 'timeline') {
          <section class="section timeline-section">
            <div class="section-header">
              <div class="timeline-header-top">
                <div>
                  <h2>📅 24-Hour Room Timeline</h2>
                  <span class="section-tagline">
                    Select a room to view and book any 5-minute slot up to 24 hours ahead.
                  </span>
                </div>
                <div class="timeline-legend">
                  <span class="legend-item"><span class="legend-dot dot-book"></span> Free (Book Now)</span>
                  <span class="legend-item"><span class="legend-dot dot-queue"></span> Held (Join Queue &gt;10m)</span>
                  <span class="legend-item"><span class="legend-dot dot-unavailable"></span> Unavailable (&le;10m)</span>
                  <span class="legend-item"><span class="legend-dot dot-yours-booked"></span> Confirmed</span>
                  <span class="legend-item"><span class="legend-dot dot-yours-scheduled"></span> Waiting in Queue</span>
                </div>
              </div>
            </div>

            <!-- Room Selector Pills -->
            <div class="room-selector-pills">
              <span class="selector-label">Choose Room:</span>
              @for (room of rooms(); track room.roomId) {
                <button
                  class="room-pill"
                  [class.active]="selectedTimelineRoomId() === room.roomId"
                  (click)="selectTimelineRoom(room.roomId)"
                >
                  Room {{ room.roomNumber }}
                </button>
              }
            </div>

            <!-- Timeline Content -->
            @if (timelineLoading()) {
              <div class="loading-box">
                <span class="spinner">⏳</span>
                <p>Loading 24-hour schedule for Room {{ currentTimelineRoomNumber() }}...</p>
              </div>
            } @else if (timelineData(); as timeline) {
              <div class="timeline-room-summary">
                <span class="summary-room">Schedule for <strong>Room {{ timeline.roomNumber }}</strong></span>
                <span class="summary-range">
                  Window: {{ timeline.windowStart | date:'medium' }} – {{ timeline.windowEnd | date:'medium' }}
                </span>
                <button class="btn-refresh-timeline" (click)="loadTimeline(timeline.roomId)">
                  ↻ Refresh Timeline
                </button>
              </div>

              <!-- Hourly Slot Groups -->
              <div class="hourly-groups-container">
                @for (group of hourlyGroups(); track group.hourLabel) {
                  <div class="hourly-group-card">
                    <div class="hourly-group-header">
                      <span class="hour-clock-icon">🕒</span>
                      <span class="hour-label">{{ group.hourLabel }}</span>
                    </div>
                    <div class="hourly-slots-grid">
                      @for (slot of group.slots; track slot.slotStart) {
                        <div
                          class="slot-cell"
                          [class.slot-action-book]="slot.action === 'BOOK'"
                          [class.slot-action-queue]="slot.action === 'JOIN_QUEUE'"
                          [class.slot-action-unavailable]="slot.action === 'UNAVAILABLE'"
                          [class.slot-action-yours-booked]="slot.action === 'YOURS_BOOKED'"
                          [class.slot-action-yours-scheduled]="slot.action === 'YOURS_SCHEDULED'"
                        >
                          <div class="slot-time-range">
                            {{ slot.slotStart | date:'HH:mm' }}
                          </div>

                          <div class="slot-action-area">
                            @if (slot.action === 'BOOK') {
                              <button
                                class="btn-slot btn-slot-book"
                                (click)="openFutureBookModal(timeline.roomId, timeline.roomNumber, slot)"
                              >
                                Book
                              </button>
                            } @else if (slot.action === 'JOIN_QUEUE') {
                              <button
                                class="btn-slot btn-slot-queue"
                                (click)="openFutureBookModal(timeline.roomId, timeline.roomNumber, slot)"
                              >
                                Queue
                                @if (slot.waitingCount > 0) {
                                  <span class="queue-count-badge">({{ slot.waitingCount }})</span>
                                }
                              </button>
                            } @else if (slot.action === 'UNAVAILABLE') {
                              <span class="slot-tag-unavailable" title="Held by someone else and <= 10m away (cannot queue)">
                                Taken (&le;10m)
                              </span>
                            } @else if (slot.action === 'YOURS_BOOKED') {
                              <span class="slot-tag-confirmed">
                                ✓ Confirmed
                              </span>
                            } @else if (slot.action === 'YOURS_SCHEDULED') {
                              <span class="slot-tag-scheduled">
                                ⏳ Queued
                              </span>
                            }
                          </div>
                        </div>
                      }
                    </div>
                  </div>
                }
              </div>
            }
          </section>
        }

        <!-- ========================================== -->
        <!-- TAB 3: MY UPCOMING RESERVATIONS (PHASE 4) -->
        <!-- ========================================== -->
        @if (activeTab() === 'my-bookings') {
          <section class="section my-bookings-section">
            <div class="section-header my-bookings-header">
              <div>
                <h2>📋 My Upcoming Reservations</h2>
                <span class="section-tagline">
                  Manage your future reservations. Cancellations are strictly permitted when more than 10 minutes remain.
                </span>
              </div>
              <div class="user-cap-meter">
                <span class="meter-label">Active Limit:</span>
                <span class="meter-count" [class.cap-warning]="myUpcomingReservations().length >= 10">
                  {{ myUpcomingReservations().length }} / 12 Max
                </span>
              </div>
            </div>

            @if (myUpcomingReservations().length > 0) {
              <div class="my-bookings-list">
                @for (res of myUpcomingReservations(); track res.reservationId) {
                  <div class="my-booking-row" [class.row-scheduled]="res.status === 'SCHEDULED'">
                    <div class="row-left">
                      <div class="row-status-pill">
                        @if (res.status === 'BOOKED') {
                          <span class="pill-confirmed">✓ CONFIRMED</span>
                        } @else {
                          <span class="pill-queue">⏳ WAITING LIST</span>
                        }
                      </div>
                      <div class="row-room-title">Room {{ res.roomNumber }}</div>
                      <div class="row-slot-times">
                        📅 {{ res.slotStart | date:'mediumDate' }} &nbsp;•&nbsp;
                        <strong>{{ res.slotStart | date:'HH:mm' }} – {{ res.slotEnd | date:'HH:mm' }}</strong>
                      </div>
                    </div>

                    <div class="row-middle">
                      @if (res.cancellable) {
                        <div class="cancel-deadline-info">
                          <span class="deadline-label">Cancel Deadline:</span>
                          <span class="deadline-time">{{ res.cancellationDeadline | date:'shortTime' }}</span>
                        </div>
                      } @else {
                        <div class="cancel-deadline-passed">
                          ⚠️ Cannot cancel (&le;10m from start)
                        </div>
                      }
                    </div>

                    <div class="row-right">
                      @if (res.cancellable) {
                        <button
                          class="btn-cancel-res"
                          (click)="openCancelModal(res)"
                          [disabled]="actionLoading()"
                        >
                          ✕ Cancel Reservation
                        </button>
                      } @else {
                        <button
                          class="btn-cancel-res btn-disabled"
                          disabled
                          title="Cancellation is not allowed within 10 minutes of slot start time"
                        >
                          Non-Cancellable
                        </button>
                      }
                    </div>
                  </div>
                }
              </div>
            } @else {
              <div class="empty-upcoming-box">
                <span class="empty-icon">🗓️</span>
                <p>You have no upcoming future reservations or queue entries.</p>
                <small>Visit the "24-Hour Room Timeline" tab to reserve future slots up to 24 hours ahead.</small>
              </div>
            }
          </section>
        }
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

      <!-- Confirmation Modal: Future Booking / Queue Joining -->
      @if (pendingFutureBookModal(); as modal) {
        <div class="modal-backdrop">
          <div class="modal-card">
            <h3>
              {{ modal.slot.action === 'BOOK' ? 'Confirm Room Reservation' : 'Join Waiting List Queue' }}
            </h3>

            <div class="modal-booking-summary">
              <div class="modal-summary-line">
                <span>Room:</span> <strong>Room {{ modal.roomNumber }}</strong>
              </div>
              <div class="modal-summary-line">
                <span>Interval:</span>
                <strong>{{ modal.slot.slotStart | date:'mediumDate' }} at {{ modal.slot.slotStart | date:'HH:mm' }} – {{ modal.slot.slotEnd | date:'HH:mm' }}</strong>
              </div>
              <div class="modal-summary-line">
                <span>Outcome:</span>
                @if (modal.slot.action === 'BOOK') {
                  <strong class="text-green">Free slot — You will be confirmed BOOKED immediately</strong>
                } @else {
                  <strong class="text-amber">Held by someone else — You will join as SCHEDULED on the waiting list</strong>
                }
              </div>
            </div>

            <div class="modal-cancellation-warning">
              ⚠️ <strong>Strict Cancellation Rule:</strong> This reservation can only be cancelled while more than 10 minutes remain before slot start. Once inside 10 minutes, cancellation is strictly forbidden.
            </div>

            <div class="modal-actions">
              <button class="btn-secondary" (click)="closeFutureBookModal()" [disabled]="actionLoading()">
                Cancel
              </button>
              <button
                [class]="modal.slot.action === 'BOOK' ? 'btn-primary' : 'btn-amber'"
                (click)="confirmFutureBook()"
                [disabled]="actionLoading()"
              >
                {{ actionLoading() ? 'Processing...' : (modal.slot.action === 'BOOK' ? 'Confirm Reservation' : 'Join Waiting List') }}
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Confirmation Modal: Cancel Reservation -->
      @if (pendingCancelReservation(); as res) {
        <div class="modal-backdrop">
          <div class="modal-card">
            <h3>Cancel Reservation</h3>
            <p>
              Are you sure you want to cancel your reservation for <strong>Room {{ res.roomNumber }}</strong>?
            </p>
            <div class="modal-info-box">
              <div><strong>Status:</strong> {{ res.status }}</div>
              <div><strong>Date & Time:</strong> {{ res.slotStart | date:'mediumDate' }} at {{ res.slotStart | date:'HH:mm' }}</div>
              <div><strong>Cancellation Deadline:</strong> {{ res.cancellationDeadline | date:'shortTime' }}</div>
            </div>
            <p class="modal-warning">
              This action cannot be undone. If you are BOOKED, the slot will be released.
            </p>
            <div class="modal-actions">
              <button class="btn-secondary" (click)="closeCancelModal()" [disabled]="actionLoading()">Keep Reservation</button>
              <button class="btn-danger" (click)="confirmCancel()" [disabled]="actionLoading()">
                {{ actionLoading() ? 'Cancelling...' : 'Confirm Cancellation' }}
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Confirmation Modal: Leave Active Room -->
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

    /* Nav Tabs Bar */
    .nav-tabs-bar {
      background: white;
      border-bottom: 1px solid #e2e8f0;
      padding: 0 2rem;
    }
    .tabs-container {
      max-width: 1200px;
      margin: 0 auto;
      display: flex;
      gap: 1rem;
    }
    .tab-btn {
      background: none;
      border: none;
      padding: 1rem 0.75rem;
      font-size: 0.95rem;
      font-weight: 600;
      color: #64748b;
      cursor: pointer;
      border-bottom: 3px solid transparent;
      display: flex;
      align-items: center;
      gap: 0.5rem;
      transition: all 0.2s;
    }
    .tab-btn:hover { color: #0f172a; }
    .tab-btn.active {
      color: #2563eb;
      border-bottom-color: #2563eb;
    }
    .tab-badge {
      background: #2563eb;
      color: white;
      font-size: 0.75rem;
      padding: 0.15rem 0.45rem;
      border-radius: 12px;
      font-weight: 700;
    }

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

    /* ========================================== */
    /* PHASE 4: TIMELINE STYLES                   */
    /* ========================================== */
    .timeline-header-top {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      flex-wrap: wrap;
      gap: 1rem;
    }
    .timeline-legend {
      display: flex;
      flex-wrap: wrap;
      gap: 1rem;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      padding: 0.5rem 0.85rem;
      border-radius: 6px;
      font-size: 0.775rem;
    }
    .legend-item { display: flex; align-items: center; gap: 0.35rem; color: #475569; font-weight: 500; }
    .legend-dot { width: 10px; height: 10px; border-radius: 3px; display: inline-block; }
    .dot-book { background: #22c55e; }
    .dot-queue { background: #f59e0b; }
    .dot-unavailable { background: #cbd5e1; }
    .dot-yours-booked { background: #3b82f6; }
    .dot-yours-scheduled { background: #a855f7; }

    .room-selector-pills {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      flex-wrap: wrap;
      margin-bottom: 1.5rem;
    }
    .selector-label { font-size: 0.875rem; font-weight: 600; color: #475569; }
    .room-pill {
      background: white;
      border: 2px solid #e2e8f0;
      padding: 0.45rem 1rem;
      border-radius: 20px;
      font-size: 0.875rem;
      font-weight: 600;
      color: #334155;
      cursor: pointer;
      transition: all 0.15s;
    }
    .room-pill:hover { border-color: #cbd5e1; }
    .room-pill.active {
      background: #2563eb;
      border-color: #2563eb;
      color: white;
      box-shadow: 0 2px 6px rgba(37,99,235,0.25);
    }

    .timeline-room-summary {
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: #f1f5f9;
      padding: 0.75rem 1.25rem;
      border-radius: 8px;
      margin-bottom: 1.5rem;
      flex-wrap: wrap;
      gap: 0.75rem;
    }
    .summary-room { font-size: 0.95rem; color: #0f172a; }
    .summary-range { font-size: 0.8rem; color: #64748b; }
    .btn-refresh-timeline {
      background: white;
      border: 1px solid #cbd5e1;
      padding: 0.35rem 0.75rem;
      border-radius: 6px;
      font-size: 0.8rem;
      cursor: pointer;
      font-weight: 600;
      color: #475569;
    }
    .btn-refresh-timeline:hover { background: #e2e8f0; }

    .hourly-groups-container {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }
    .hourly-group-card {
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      background: #ffffff;
      padding: 1rem;
    }
    .hourly-group-header {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin-bottom: 0.75rem;
      padding-bottom: 0.5rem;
      border-bottom: 1px solid #f1f5f9;
    }
    .hour-clock-icon { font-size: 1rem; }
    .hour-label { font-weight: 700; font-size: 0.9rem; color: #1e293b; }

    .hourly-slots-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(85px, 1fr));
      gap: 0.6rem;
    }
    .slot-cell {
      border: 1px solid #e2e8f0;
      border-radius: 6px;
      padding: 0.5rem 0.35rem;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.35rem;
      text-align: center;
      transition: all 0.15s;
    }
    .slot-cell.slot-action-book {
      background: #f0fdf4;
      border-color: #bbf7d0;
    }
    .slot-cell.slot-action-queue {
      background: #fffbeb;
      border-color: #fde68a;
    }
    .slot-cell.slot-action-unavailable {
      background: #f8fafc;
      border-color: #e2e8f0;
      opacity: 0.75;
    }
    .slot-cell.slot-action-yours-booked {
      background: #eff6ff;
      border-color: #93c5fd;
    }
    .slot-cell.slot-action-yours-scheduled {
      background: #faf5ff;
      border-color: #d8b4fe;
    }

    .slot-time-range {
      font-size: 0.8rem;
      font-weight: 700;
      color: #334155;
      font-variant-numeric: tabular-nums;
    }
    .slot-action-area { width: 100%; }
    .btn-slot {
      width: 100%;
      padding: 0.3rem 0.2rem;
      font-size: 0.725rem;
      font-weight: 700;
      border-radius: 4px;
      border: none;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.2rem;
    }
    .btn-slot-book { background: #16a34a; color: white; }
    .btn-slot-book:hover { background: #15803d; }
    .btn-slot-queue { background: #d97706; color: white; }
    .btn-slot-queue:hover { background: #b45309; }
    .queue-count-badge { font-size: 0.65rem; background: rgba(0,0,0,0.15); padding: 0.1rem 0.25rem; border-radius: 3px; }

    .slot-tag-unavailable {
      display: block;
      font-size: 0.65rem;
      font-weight: 700;
      color: #94a3b8;
      background: #e2e8f0;
      padding: 0.25rem 0.1rem;
      border-radius: 4px;
    }
    .slot-tag-confirmed {
      display: block;
      font-size: 0.65rem;
      font-weight: 700;
      color: #1d4ed8;
      background: #dbeafe;
      padding: 0.25rem 0.1rem;
      border-radius: 4px;
    }
    .slot-tag-scheduled {
      display: block;
      font-size: 0.65rem;
      font-weight: 700;
      color: #7e22ce;
      background: #f3e8ff;
      padding: 0.25rem 0.1rem;
      border-radius: 4px;
    }

    /* ========================================== */
    /* PHASE 4: MY BOOKINGS STYLES                */
    /* ========================================== */
    .my-bookings-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      flex-wrap: wrap;
      gap: 1rem;
    }
    .user-cap-meter {
      background: #f8fafc;
      border: 1px solid #cbd5e1;
      padding: 0.5rem 1rem;
      border-radius: 8px;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .meter-label { font-size: 0.8rem; font-weight: 600; color: #475569; }
    .meter-count { font-size: 1rem; font-weight: 800; color: #1e293b; }
    .meter-count.cap-warning { color: #b45309; }

    .my-bookings-list {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    .my-booking-row {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-left: 4px solid #3b82f6;
      border-radius: 8px;
      padding: 1.25rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 1rem;
    }
    .my-booking-row.row-scheduled {
      border-left-color: #a855f7;
      background: #faf5ff;
    }

    .row-left { display: flex; flex-direction: column; gap: 0.35rem; }
    .row-status-pill { margin-bottom: 0.2rem; }
    .pill-confirmed {
      background: #dbeafe;
      color: #1e40af;
      font-size: 0.7rem;
      font-weight: 800;
      padding: 0.2rem 0.6rem;
      border-radius: 4px;
    }
    .pill-queue {
      background: #f3e8ff;
      color: #6b21a8;
      font-size: 0.7rem;
      font-weight: 800;
      padding: 0.2rem 0.6rem;
      border-radius: 4px;
    }
    .row-room-title { font-size: 1.15rem; font-weight: 700; color: #0f172a; }
    .row-slot-times { font-size: 0.85rem; color: #475569; }

    .row-middle { display: flex; flex-direction: column; gap: 0.2rem; }
    .cancel-deadline-info { font-size: 0.8rem; color: #15803d; font-weight: 600; }
    .deadline-label { color: #475569; font-weight: 500; }
    .cancel-deadline-passed { font-size: 0.8rem; color: #b45309; font-weight: 600; }

    .btn-cancel-res {
      background: #fee2e2;
      color: #991b1b;
      border: 1px solid #fecaca;
      padding: 0.5rem 1rem;
      border-radius: 6px;
      font-weight: 600;
      font-size: 0.85rem;
      cursor: pointer;
      transition: all 0.15s;
    }
    .btn-cancel-res:hover:not(:disabled) { background: #fca5a5; }

    .empty-upcoming-box {
      text-align: center;
      padding: 3rem 1.5rem;
      background: #f8fafc;
      border: 1px dashed #cbd5e1;
      border-radius: 8px;
    }

    /* Modal extras for Phase 4 */
    .modal-booking-summary {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 1rem;
      margin: 1rem 0;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      font-size: 0.9rem;
    }
    .modal-summary-line { display: flex; justify-content: space-between; gap: 1rem; }
    .text-green { color: #15803d; }
    .text-amber { color: #b45309; }
    .modal-cancellation-warning {
      background: #fffbeb;
      border-left: 3px solid #f59e0b;
      padding: 0.75rem;
      border-radius: 6px;
      font-size: 0.825rem;
      color: #92400e;
      line-height: 1.4;
      margin-bottom: 1.5rem;
    }
    .btn-amber {
      background: #d97706;
      color: white;
      border: none;
      padding: 0.6rem 1.2rem;
      border-radius: 6px;
      font-weight: 600;
      cursor: pointer;
    }
    .btn-amber:hover { background: #b45309; }

    /* Modals base */
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
    .loading-box { text-align: center; padding: 2rem; color: #64748b; }
    .spinner { font-size: 2rem; display: block; margin-bottom: 0.5rem; }
  `],
})
export class DashboardComponent implements OnInit, OnDestroy {
  authService = inject(AuthService);
  private roomService = inject(RoomService);
  private router = inject(Router);

  // Tab State: 'next-interval' | 'timeline' | 'my-bookings'
  activeTab = signal<'next-interval' | 'timeline' | 'my-bookings'>('next-interval');

  // Next-Interval Grid State
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

  // Phase 4: Room 24h Timeline State
  selectedTimelineRoomId = signal<string | null>(null);
  timelineData = signal<SlotTimelineResponse | null>(null);
  timelineLoading = signal<boolean>(false);
  currentTimelineRoomNumber = computed(() => {
    const r = this.rooms().find((rm) => rm.roomId === this.selectedTimelineRoomId());
    return r ? r.roomNumber : '';
  });

  // Computed hourly grouping of 288 slots (12 slots per hour across 24 hours)
  hourlyGroups = computed<HourlySlotGroup[]>(() => {
    const data = this.timelineData();
    if (!data || !data.slots || data.slots.length === 0) return [];

    const map = new Map<string, SlotTimelineItem[]>();
    for (const slot of data.slots) {
      const d = new Date(slot.slotStart);
      const hourKey = d.toLocaleDateString([], { month: 'short', day: 'numeric' }) +
        ' — ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
      // Group by date + hour (e.g. "Nov 12, 14:00")
      const hourBlock = d.toLocaleDateString([], { month: 'short', day: 'numeric' }) + ' ' +
        d.getHours().toString().padStart(2, '0') + ':00 – ' +
        ((d.getHours() + 1) % 24).toString().padStart(2, '0') + ':00';

      if (!map.has(hourBlock)) {
        map.set(hourBlock, []);
      }
      map.get(hourBlock)!.push(slot);
    }

    return Array.from(map.entries()).map(([hourLabel, slots]) => ({
      hourLabel,
      slots,
    }));
  });

  // Phase 4: My Upcoming Reservations State
  myUpcomingReservations = signal<FutureReservationSummary[]>([]);

  // Modals & Pending Actions
  pendingBatchModal = signal<boolean>(false);
  pendingLeaveReservation = signal<ActiveReservation | null>(null);
  pendingFutureBookModal = signal<{ roomId: string; roomNumber: string; slot: SlotTimelineItem } | null>(null);
  pendingCancelReservation = signal<FutureReservationSummary | null>(null);
  actionLoading = signal<boolean>(false);

  // Notifications
  notificationMessage = signal<string | null>(null);
  notificationType = signal<'success' | 'error'>('success');
  unavailableRoomsList = signal<string[]>([]);

  private timerIntervalId: any = null;
  private pollIntervalId: any = null;
  private serverTimeOffsetMs = 0;

  ngOnInit(): void {
    this.refreshAll();

    // 1-second countdown ticker
    this.timerIntervalId = setInterval(() => {
      this.tick();
    }, 1000);

    // 10-second polling for grid updates
    this.pollIntervalId = setInterval(() => {
      this.pollBackgroundData();
    }, 10000);
  }

  ngOnDestroy(): void {
    if (this.timerIntervalId) clearInterval(this.timerIntervalId);
    if (this.pollIntervalId) clearInterval(this.pollIntervalId);
  }

  switchTab(tab: 'next-interval' | 'timeline' | 'my-bookings'): void {
    this.activeTab.set(tab);
    if (tab === 'timeline') {
      if (!this.selectedTimelineRoomId() && this.rooms().length > 0) {
        this.selectTimelineRoom(this.rooms()[0].roomId);
      } else if (this.selectedTimelineRoomId()) {
        this.loadTimeline(this.selectedTimelineRoomId()!);
      }
    } else if (tab === 'my-bookings') {
      this.loadMyBookings();
    }
  }

  refreshAll(): void {
    this.loadGrid();
    this.loadActiveRooms();
    this.loadMyBookings();
  }

  private pollBackgroundData(): void {
    this.loadGrid();
    this.loadActiveRooms();
    if (this.activeTab() === 'my-bookings') {
      this.loadMyBookings();
    } else if (this.activeTab() === 'timeline' && this.selectedTimelineRoomId()) {
      this.loadTimeline(this.selectedTimelineRoomId()!);
    }
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

        // Default timeline room if not yet chosen
        if (!this.selectedTimelineRoomId() && res.rooms.length > 0) {
          this.selectedTimelineRoomId.set(res.rooms[0].roomId);
        }
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

  // --- Phase 4: Timeline Methods ---

  selectTimelineRoom(roomId: string): void {
    this.selectedTimelineRoomId.set(roomId);
    this.loadTimeline(roomId);
  }

  loadTimeline(roomId: string): void {
    this.timelineLoading.set(true);
    this.roomService.getRoomTimeline(roomId).subscribe({
      next: (res) => {
        this.timelineLoading.set(false);
        this.timelineData.set(res);
      },
      error: (err) => {
        this.timelineLoading.set(false);
        const msg = err?.error?.message || 'Failed to load room timeline';
        this.showNotification(msg, 'error');
      },
    });
  }

  openFutureBookModal(roomId: string, roomNumber: string, slot: SlotTimelineItem): void {
    this.pendingFutureBookModal.set({ roomId, roomNumber, slot });
  }

  closeFutureBookModal(): void {
    this.pendingFutureBookModal.set(null);
  }

  confirmFutureBook(): void {
    const modal = this.pendingFutureBookModal();
    if (!modal) return;

    this.actionLoading.set(true);
    this.roomService.futureBook({ roomId: modal.roomId, slotStart: modal.slot.slotStart }).subscribe({
      next: (res) => {
        this.actionLoading.set(false);
        this.closeFutureBookModal();
        const outcomeMsg = res.status === 'BOOKED'
          ? `✓ Confirmed booking for Room ${res.roomNumber} (${this.formatTimeSlot(res.slotStart, res.slotEnd)})`
          : `⏳ Joined waiting list queue for Room ${res.roomNumber} (${this.formatTimeSlot(res.slotStart, res.slotEnd)})`;
        this.showNotification(outcomeMsg, 'success');
        this.loadTimeline(modal.roomId);
        this.loadMyBookings();
      },
      error: (err) => {
        this.actionLoading.set(false);
        this.closeFutureBookModal();
        const msg = err?.error?.message || 'Failed to book future slot';
        this.showNotification(msg, 'error');
        this.loadTimeline(modal.roomId);
      },
    });
  }

  // --- Phase 4: My Upcoming Bookings Methods ---

  loadMyBookings(): void {
    this.roomService.getMyFutureReservations().subscribe({
      next: (res) => {
        this.myUpcomingReservations.set(res.reservations);
      },
      error: (err) => {
        if (err.status === 401) {
          this.router.navigate(['/login']);
        }
      },
    });
  }

  openCancelModal(res: FutureReservationSummary): void {
    this.pendingCancelReservation.set(res);
  }

  closeCancelModal(): void {
    this.pendingCancelReservation.set(null);
  }

  confirmCancel(): void {
    const res = this.pendingCancelReservation();
    if (!res) return;

    this.actionLoading.set(true);
    this.roomService.cancelReservation(res.reservationId).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.closeCancelModal();
        this.showNotification(`Cancelled reservation for Room ${res.roomNumber} successfully.`, 'success');
        this.loadMyBookings();
        if (this.selectedTimelineRoomId()) {
          this.loadTimeline(this.selectedTimelineRoomId()!);
        }
      },
      error: (err) => {
        this.actionLoading.set(false);
        this.closeCancelModal();
        const msg = err?.error?.message || 'Failed to cancel reservation';
        this.showNotification(msg, 'error');
        this.loadMyBookings();
      },
    });
  }

  // --- Flip and Timer Countdown Helpers ---

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

    // If flip timer reaches zero, refresh everything
    if (remaining === 0) {
      this.clearSelection();
      setTimeout(() => {
        this.refreshAll();
        if (this.selectedTimelineRoomId()) {
          this.loadTimeline(this.selectedTimelineRoomId()!);
        }
      }, 1000);
    }
  }

  // --- Multi-Room Selection Methods (Phase 3) ---

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

  // --- Leave Room Methods (Phase 2) ---

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
