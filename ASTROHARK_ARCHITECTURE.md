# Astrohark Project Architecture

## Overview
Astrohark is an Android application built for real-time astrological consultations. It features robust real-time communication, voice/image sharing, and billing management.

## Tech Stack
* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (with some legacy XML if applicable)
* **Architecture:** MVVM (Model-View-ViewModel)
* **Asynchronous Programming:** Coroutines & Flow/StateFlow
* **Real-time Communication:** Socket.IO
* **Networking:** Retrofit (Node.js Backend)

## Core Components
### 1. Presentation Layer (UI & ViewModels)
* **Activities/Fragments/Compose Screens:** Observe UI states from ViewModels and send user intents.
* **ViewModels:** Manage UI state using `StateFlow` and handle business logic. Keep logic lifecycle-aware using `viewModelScope`.

### 2. Domain/Data Layer (Repositories)
* **Repositories (e.g., ChatRepository):** Act as the single source of truth. They abstract the data origin (Network vs Local vs Socket) from the ViewModels.

### 3. Remote/Network Layer
* **SocketManager:** Handles real-time bi-directional communication using Socket.IO.
* **ApiInterface:** Handles standard HTTP REST API calls via Retrofit.

## Important Architectural Rules
1. **Unidirectional Data Flow (UDF):** UI components should only observe state. State mutations must happen within ViewModels.
2. **StateFlow over LiveData:** Use `StateFlow` for state management.
3. **No UI in Data Layer:** Repositories and SocketManager should never reference Android `Context` or UI elements.
4. **Lifecycle Management:** Always cancel coroutines and unregister socket listeners when the UI is destroyed to prevent memory leaks.
5. **Role-Based Logic:** Keep Astrologer and Client logic distinct but cleanly separated using role enums/constants.
