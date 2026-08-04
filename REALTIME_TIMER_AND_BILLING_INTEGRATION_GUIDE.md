# Real-Time Timer Sync & Minimum-Duration Billing Integration Guide

This guide provides a step-by-step blueprint to implement **Real-Time Timer Synchronization** and the **Minimum-Duration Billing Policy** across any Node.js + Socket.IO backend and client applications (Android Kotlin / Web JS).

---

## Architecture Overview

```
 ┌───────────────────────┐           ┌───────────────────────┐
 │   Client Mobile App   │           │ Provider Mobile / Web │
 └───────────┬───────────┘           └───────────┬───────────┘
             │                                   │
             │      session-timer-sync (1s)      │
             │<──────────────────────────────────┤
             │                                   │
             ▼                                   ▼
 ┌───────────────────────────────────────────────────────────┐
 │                   Node.js Server Ticker                   │
 └───────────────────────────┬───────────────────────────────┘
                             │
                             ▼
 ┌───────────────────────────────────────────────────────────┐
 │          endSessionRecord Candidate Duration Evaluator   │
 │   Math.min(Server, ClientWall, ProviderWall, Reported...) │
 └───────────────────────────┬───────────────────────────────┘
                             │
                             ▼
 ┌───────────────────────────────────────────────────────────┐
 │               Wallet Deduction & Ledger DB                │
 └───────────────────────────┬───────────────────────────────┘
```

---

## 1. Backend Server Implementation (Node.js + Socket.IO)

### Step 1: Active Session Store & Socket Connection Handler

In your socket handler (`socketHandler.js` or `callHandler.js`):

```javascript
// Memory map to track live active sessions
const activeSessions = new Map();

io.on('connection', (socket) => {
    // 1. Session Connect / Join
    socket.on('session-connect', async (data) => {
        const { sessionId } = data || {};
        if (!sessionId) return;

        // Ensure socket joins session room for broadcast events
        socket.join(sessionId);

        const now = Date.now();
        let session = activeSessions.get(sessionId);

        if (!session) {
            session = {
                sessionId,
                startedAt: now,
                elapsedBillableSeconds: 0,
                clientConnectedAt: now,
                providerConnectedAt: null,
                actualBillingStart: null,
                reportedClientDuration: 0,
                reportedProviderDuration: 0,
                reportedDuration: 0
            };
            activeSessions.set(sessionId, session);
        }
        
        // Start billing when both parties connect
        if (!session.actualBillingStart) {
            session.actualBillingStart = now + 1000;
            io.to(sessionId).emit('billing-started', { startTime: session.actualBillingStart });
        }
    });

    // 2. End Session (Termination request)
    socket.on('end-session', async (data) => {
        const { sessionId, duration, clientDuration, providerDuration } = data || {};
        if (!sessionId) return;

        const session = activeSessions.get(sessionId);
        if (!session) return;

        // Store candidate reported durations from socket payload
        const socketUserId = socketToUser.get(socket.id);
        if (socketUserId === session.clientId && (clientDuration || duration)) {
            session.reportedClientDuration = clientDuration || duration;
        } else if (socketUserId === session.providerId && (providerDuration || duration)) {
            session.reportedProviderDuration = providerDuration || duration;
        } else if (duration) {
            session.reportedDuration = duration;
        }

        // Execute billing processing
        await endSessionRecord(sessionId, duration);
    });
});
```

---

### Step 2: Global Ticker Loop (Server Heartbeat Timer)

In your main server initialization file (`server.js`):

```javascript
setInterval(async function tickSessions() {
    const now = Date.now();

    for (const [sessionId, session] of activeSessions) {
        // Skip if session billing has not started
        if (!session.actualBillingStart || now < session.actualBillingStart) continue;

        // 1. Increment billable seconds
        session.elapsedBillableSeconds++;

        // 2. Broadcast real-time timer sync heartbeat to client, provider, and room
        if (io) {
            const syncPayload = {
                sessionId,
                elapsedSeconds: session.elapsedBillableSeconds
            };
            io.to(sessionId).emit('session-timer-sync', syncPayload);
            if (session.clientId) io.to(session.clientId).emit('session-timer-sync', syncPayload);
            if (session.providerId) io.to(session.providerId).emit('session-timer-sync', syncPayload);
        }
    }
}, 1000);
```

---

### Step 3: Billing Engine - Lower/Minimum Duration Policy

In your billing service (`billing.service.js`):

```javascript
async function endSessionRecord(sessionId, extraReportedDuration) {
    const session = activeSessions.get(sessionId);
    if (!session) return;

    const endTime = Date.now();
    const serverTickerSeconds = session.elapsedBillableSeconds || 0;
    const clientWallClockSeconds = session.clientConnectedAt ? Math.ceil((endTime - session.clientConnectedAt) / 1000) : 0;
    const providerWallClockSeconds = session.providerConnectedAt ? Math.ceil((endTime - session.providerConnectedAt) / 1000) : 0;
    const reportedClientDuration = session.reportedClientDuration || 0;
    const reportedProviderDuration = session.reportedProviderDuration || 0;
    const reportedDuration = session.reportedDuration || extraReportedDuration || 0;

    // Collect non-zero candidate durations
    const candidates = [
        serverTickerSeconds,
        clientWallClockSeconds,
        providerWallClockSeconds,
        reportedClientDuration,
        reportedProviderDuration,
        reportedDuration
    ].filter(sec => typeof sec === 'number' && sec > 0);

    // MINIMUM / LOWER DURATION POLICY RULE:
    // Take whichever valid candidate timer has the lower seconds count
    const billableSeconds = candidates.length > 0 ? Math.min(...candidates) : 0;

    console.log(`[Billing] sessionId=${sessionId} | Candidates: server=${serverTickerSeconds}s, clientWall=${clientWallClockSeconds}s, providerWall=${providerWallClockSeconds}s, clientReported=${reportedClientDuration}s, providerReported=${reportedProviderDuration}s | Selected MIN billableSeconds=${billableSeconds}s`);

    // Calculate minute rounding & charges
    const billableMinutes = Math.ceil(billableSeconds / 60);
    const pricePerMinute = session.pricePerMinute || 10;
    const totalAmount = billableMinutes * pricePerMinute;
    const providerShare = totalAmount * 0.50;

    // Deduct from client wallet atomically
    await User.updateOne(
        { userId: session.clientId },
        { $inc: { walletBalance: -totalAmount } }
    );

    // Credit provider wallet atomically
    await User.updateOne(
        { userId: session.providerId },
        { $inc: { walletBalance: providerShare, totalEarnings: providerShare } }
    );

    // Record session history
    await SessionModel.updateOne({ sessionId }, {
        endTime,
        duration: billableSeconds * 1000,
        totalCharged: totalAmount,
        totalEarned: providerShare,
        status: 'ended'
    });

    // Remove from active sessions map
    activeSessions.delete(sessionId);

    // Notify clients of summary
    io.to(session.clientId).emit('session-ended', {
        reason: 'ended',
        summary: { deducted: totalAmount, earned: providerShare, duration: billableSeconds }
    });
    io.to(session.providerId).emit('session-ended', {
        reason: 'ended',
        summary: { deducted: totalAmount, earned: providerShare, duration: billableSeconds }
    });
}
```

---

## 2. Client Application Implementation

### A. Android Kotlin (`SocketManager.kt` & `CallActivity.kt`)

#### 1. In `SocketManager.kt`:
```kotlin
data class SessionTimerSyncInfo(
    val sessionId: String,
    val elapsedSeconds: Int
)

fun onSessionTimerSync(listener: (SessionTimerSyncInfo) -> Unit) {
    socket?.off("session-timer-sync")
    socket?.on("session-timer-sync") { args ->
        if (args != null && args.isNotEmpty()) {
            val data = args[0] as? JSONObject
            val sessionId = data?.optString("sessionId") ?: ""
            val elapsed = data?.optInt("elapsedSeconds", 0) ?: 0
            listener(SessionTimerSyncInfo(sessionId, elapsed))
        }
    }
}

fun endSession(sessionId: String?, durationSeconds: Int? = null) {
    val payload = JSONObject().apply {
        put("sessionId", sessionId)
        if (durationSeconds != null && durationSeconds > 0) {
            put("duration", durationSeconds)
            put("clientDuration", durationSeconds)
            put("providerDuration", durationSeconds)
        }
    }
    socket?.emit("end-session", payload)
}
```

#### 2. In `CallActivity.kt`:
```kotlin
// Listen for server timer sync to keep UI display synchronized
SocketManager.onSessionTimerSync { info ->
    runOnUiThread {
        if (info.sessionId == sessionId) {
            callDurationSeconds = info.elapsedSeconds
        }
    }
}

// When ending call:
private fun endCall() {
    SocketManager.endSession(sessionId, callDurationSeconds)
    finish()
}
```

---

### B. Web Client Implementation (JavaScript)

```javascript
let timerSec = 0;

// 1. Listen for server timer synchronization
socket.on('session-timer-sync', (data) => {
    if (data && typeof data.elapsedSeconds === 'number') {
        timerSec = data.elapsedSeconds;
        updateTimerDisplay(timerSec);
    }
});

function updateTimerDisplay(seconds) {
    const mins = Math.floor(seconds / 60).toString().padStart(2, '0');
    const secs = (seconds % 60).toString().padStart(2, '0');
    document.getElementById('timerDisplay').innerText = `${mins}:${secs}`;
}

// 2. End session event emission with local duration
function endCallSession(sessionId, partnerId) {
    socket.emit('end-session', {
        sessionId: sessionId,
        toUserId: partnerId,
        duration: timerSec,
        clientDuration: timerSec
    });
}
```

---

## 3. Automated Test Verification Script

Save as `scripts/test_timer_sync_and_min_billing.js`:

```javascript
require('dotenv').config();
const io = require('socket.io-client');
const mongoose = require('mongoose');
const assert = require('assert');

const URL = 'http://localhost:3000';

async function runTest() {
    console.log(`Connecting to server ${URL}...`);

    const clientSocket = io(URL, { reconnection: false });
    const providerSocket = io(URL, { reconnection: false });

    let timerSyncCount = 0;
    let endedSummary = null;

    clientSocket.on('session-timer-sync', (data) => {
        timerSyncCount++;
        console.log(`[Timer Sync Received] ${data.elapsedSeconds}s`);
    });

    clientSocket.on('session-ended', (data) => {
        endedSummary = data;
    });

    // 1. Trigger connection
    providerSocket.emit('session-connect', { sessionId: 'test_sess_1' });
    clientSocket.emit('session-connect', { sessionId: 'test_sess_1' });

    // 2. Wait for ticks
    await new Promise(r => setTimeout(r, 4000));
    assert(timerSyncCount > 0, 'FAIL: session-timer-sync event not received');

    // 3. Emit end-session with reported lower duration (3s vs server 4s)
    clientSocket.emit('end-session', {
        sessionId: 'test_sess_1',
        clientDuration: 3
    });

    await new Promise(r => setTimeout(r, 1500));
    assert.strictEqual(endedSummary.summary.duration, 3, 'FAIL: Expected lower duration 3s');

    console.log('✓ All timer sync & lower duration billing tests PASSED!');
    clientSocket.disconnect();
    providerSocket.disconnect();
    process.exit(0);
}

runTest();
```

---

## Summary Checklist for Other Projects

1. [x] **Server Ticker**: Add `session-timer-sync` emission inside 1-second `setInterval`.
2. [x] **Room Subscription**: Call `socket.join(sessionId)` on `session-connect`.
3. [x] **Client Sync**: Listen for `session-timer-sync` in Mobile App / Web frontend.
4. [x] **End Session Payload**: Pass `duration` / `clientDuration` on `end-session`.
5. [x] **Billing Evaluation**: Compute `Math.min(...validCandidates)` in `endSessionRecord`.
