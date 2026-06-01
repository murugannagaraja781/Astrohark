# Astrohark Socket Events Documentation

## Connection Management
* **`register`**: Authenticates the user socket. `{"userId": "..."}`
* **`disconnect`**: Client disconnected from the server.

## Session / Call Management
* **`request-session`**: Emitted to request a consultation. `{"toUserId", "fromUserId", "type", "callType", "birthData"}`
* **`session-answered`**: Received when the other party answers.
* **`incoming-session`**: Received when someone calls the user.
* **`end-session`**: Emitted to cleanly close an active session. `{"sessionId": "..."}`
* **`cancel-call`**: Emitted if the caller drops before answering.
* **`session-ended`**: Received when the session ends (includes summary/billing).
* **`call-cancelled`**: Received when the incoming call is cancelled.

## Chat & Messaging
* **`chat-message`**: Core event for sending/receiving messages.
* **`message-status`**: Read/Delivered receipts.
* **`typing` / `stop-typing`**: Typing indicators.
* **`get-history`**: Fetch previous messages for a session.

## WebRTC & Signaling (For Audio/Video)
* **`signal`**: SDP offers, answers, and ICE candidates. Requires `fromUserId` for server auth.
* **`ping` / `pong`**: Connection health checks.

## Billing & Wallet
* **`billing-started`**: Received when billing initiates. Contains `startTime`, `clientBalance`, `ratePerMinute`, `availableMinutes`.
* **`wallet-update`**: Received when the wallet balance changes.

## Astrologer Specific
* **`update-service-status`**: Emitted to toggle online/offline for services (chat/call).
* **`astrologer-update`**: Real-time updates regarding astrologer availability.
