# Recent Calling & Billing Fixes Documentation

## Overview
This document summarizes the root causes and resolutions for two major issues identified in the Astrohark backend service:
1. **New User Call Disconnection Issue**: First-time calls made by new users were disconnecting/cutting immediately.
2. **Billing Engine Processing Failure**: Billing charges were not deducting wallet balance or updating astrologer earnings.

---

## 1. New User First Call Disconnection Fix

### Root Cause
- **Premature DB Mutation**: In `socket/callHandler.js`, during `session-connect` (second 0 of call startup), `client.isNewUser = false` was being updated and saved to MongoDB immediately.
- **Falsified Insufficient Balance Disconnect**: Because `client.isNewUser` was mutated to `false` at connection:
  1. `availableMinutes` evaluated to `Math.floor(clientBalance / ratePerMinute)`. For a new user with ₹0–₹5 balance, this returned `0`.
  2. In `server.js` (ticker loop), the server re-fetched the client from DB. Finding `isNewUser = false`, it evaluated the call against full astrologer rates (e.g. ₹20–₹30/min). Since the new user had less than 1 full minute's balance at regular rates, the ticker emitted `session-ended` (`insufficient_balance`) and disconnected the call immediately.

### Resolution
- **[socket/callHandler.js](file:///Users/wohozo/Documents/Astrohark/socket/callHandler.js)**: Removed premature mutation of `client.isNewUser = false` during `session-connect`. The flag is preserved during the active call so the 5-minute new user promo works properly, and is updated to `false` in `billingService.endSessionRecord` only after the first session completes.
- **[server.js](file:///Users/wohozo/Documents/Astrohark/server.js)**: Updated the ticker balance check to check `session.isNewUser || client.isNewUser` and skip disconnecting active promo new users.

---

## 2. Billing Processing Failure Fix

### Root Cause
- **ReferenceError (Variable Initialization Order)**: In `services/billing.service.js`, inside `processBillingCharge()`:
  ```javascript
  // Line 29 (BUG):
  const isNewUserPromo = activeSess?.isNewUser || client.isNewUser;
  ...
  // Line 42 (BUG):
  const activeSess = activeSessions.get(sessionId);
  ```
  `activeSess` was accessed on line 29 before it was declared on line 42. Node.js threw `ReferenceError: Cannot access 'activeSess' before initialization`, causing `processBillingCharge` to crash into its catch block silently every minute. As a result, wallet deduction, astrologer earnings, and ledger creation failed.

- **Ticker Interval Resets**: In `server.js`, `setInterval(tickSessions, 1000)` was inside the socket connection handler, causing the 1-second ticker interval to clear and restart every time a socket connected/reconnected.

### Resolution
- **[services/billing.service.js](file:///Users/wohozo/Documents/Astrohark/services/billing.service.js)**: Moved `const activeSess = activeSessions.get(sessionId);` above `isNewUserPromo` calculation.
- **[server.js](file:///Users/wohozo/Documents/Astrohark/server.js)**: Updated `global.tickInterval` initialization to ensure the 1-second ticker runs globally without getting cleared on socket connections.

---

## Summary of Modified Files

| File | Change Description |
| :--- | :--- |
| **`socket/callHandler.js`** | Removed premature `isNewUser = false` DB update during `session-connect`. |
| **`services/billing.service.js`** | Fixed `activeSess` ReferenceError in `processBillingCharge()`. |
| **`server.js`** | Updated ticker balance check for promo users and stabilized `global.tickInterval`. |

---

## Deployment Note
These fixes are 100% server-side. Restarting the backend Node.js process (`pm2 restart all`) applies all fixes instantly without requiring any Android APK rebuild or app update.
