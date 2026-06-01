# Astrohark API Documentation

## Overview
Astrohark relies on a Retrofit-based `ApiInterface.kt` to communicate with the Node.js backend for operations that do not require real-time socket connections.

## Common Endpoints (Expected Structure)
*(Note: Based on standard Astrohark architecture patterns)*

### 1. Authentication
* **Login/Signup:** `/api/auth/login`, `/api/auth/verify-otp`
* **Token Management:** JWT tokens are typically passed in the `Authorization: Bearer <token>` header via OkHttp Interceptors.

### 2. User & Astrologer Profiles
* **Get Profile:** Fetch user or astrologer details.
* **Update Profile:** Update name, bio, skills, pricing.

### 3. Wallet & Payments
* **Get Balance:** Fetch current wallet balance.
* **Add Money:** Initiate payment gateway flows.
* **Withdrawals:** For Astrologers to request payouts.

### 4. Media & Uploads
* **Upload Image/Audio:** `/api/upload` - Usually handled as `MultipartBody.Part` in Retrofit to upload to AWS S3 or the backend server, which returns a URL to be sent via Socket.IO.

## Best Practices for API Integration
* Use `suspend` functions in `ApiInterface`.
* Wrap API calls in `runCatching` or a custom `Resource`/`Result` sealed class to safely handle HTTP errors and network failures.
* Never hardcode base URLs; read from `Constants` or `BuildConfig`.
