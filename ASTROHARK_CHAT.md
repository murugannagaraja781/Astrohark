# Astrohark Chat Module (Detailed Architecture)

This document provides a deep dive into the internal functions, state variables, and data flow of the Astrohark Chat Module.

---

## 1. ChatActivity.kt (UI & Lifecycle Layer)
`ChatActivity` is the entry point for the chat session. It uses Jetpack Compose for rendering the UI and handles the Android lifecycle, intents, and notification cleanup.

### Key State Variables
* `toUserId`, `sessionId`: Essential identifiers for routing messages.
* `clientBirthData` (`JSONObject`): Stores the client's astrological details for chart generation.
* `chatDurationSeconds`, `remainingSeconds`: Tracks the ongoing call duration and the client's wallet balance limits.
* `pendingAccept`: A flag used to ensure `acceptSession` is only emitted after the socket successfully reconnects.

### Core Lifecycle Functions
* `handleIntent(intent: Intent?)`: Parses incoming data (e.g., from push notifications). Calls `viewModel.loadHistory(sessionId)` and `viewModel.joinSessionSafe(sessionId)`.
* `setupObservers()`: Listens to LiveData from the ViewModel (e.g., `sessionSummary`, `availableMinutes`, `sessionEnded`).
* `finishSessionAndNavigate()`: Clears active notifications, resets the global `CallState`, and routes the user back to the appropriate Dashboard.
* `endChat()`: Triggers the disconnect flow by calling `viewModel.endSession()`.

### Jetpack Compose UI Components
* `ChatScreen()`: The main Scaffold containing the TopAppBar (with live timers) and the message list (`LazyColumn`).
* `ChatBubble()`: Renders individual messages. Features WhatsApp-style UI (green/white bubbles) and supports **Swipe-to-Reply** using `SwipeToDismissBox`.
* `ChatInputBar()`: Handles text input, media attachments (via `ActivityResultContracts.GetContent()`), and Voice Recording (via `VoiceRecorder` utility).
* `KpChartDialog()`: Triggered by Astrologers to view the client's birth chart dynamically.

---

## 2. ChatViewModel.kt (Presentation & Logic Layer)
The ViewModel manages the state of the chat, bridges the UI with the Repository, and handles complex asynchronous tasks like media uploads.

### Important LiveData / StateFlows
* `history (LiveData<List<ChatMessage>>)`: The main list of messages displayed on screen.
* `typingStatus (LiveData<Boolean>)`: Toggles the "Typing..." indicator in the App Bar.
* `isAstrologerViewingChart (LiveData<Boolean>)`: Shows a yellow banner to the client when the astrologer is reviewing their chart.
* `availableMinutes` / `billingInfo`: Real-time wallet and timer sync from the socket.
* `sessionSummary (LiveData<SessionSummary>)`: Emits when the call finishes, containing earnings/deductions.

### Core Functions
* `sendMessage(data: JSONObject)`: 
  1. Optimistically saves the message to the Local Room DB (`ChatMessageEntity`).
  2. Emits the message via the repository to the Socket server.
* `uploadFileAndSend(file, type, sessionId, toUserId)`: 
  Uploads audio/images to the Node.js backend via Retrofit API, waits for the `url`, and then emits a socket message containing that URL.
* `startListeners()` / `stopListeners()`: 
  Hooks into the repository to listen for incoming messages, read receipts (`message-status`), and typing events. Automatically handles sending "Read" receipts back when a message is received.
* `loadHistory(sessionId)`: 
  Immediately loads data from the local DB for fast UI rendering, while parallelly calling `fetchHistoryFromServer` to sync missing messages.
* `acceptSession(sessionId, toUserId)` / `joinSessionSafe(sessionId)`: 
  Contains robust retry logic (polling `SocketManager.getSocket()?.connected()`) to ensure the user is registered before emitting critical session events.

---

## 3. ChatRepository.kt (Data & Network Abstraction)
The repository acts as the single source of truth, abstracting both the Local Room Database (`chatDao`) and the Remote Socket connection (`SocketManager`).

### Local DB Operations (Room)
* `getMessages(sessionId)`: Returns a reactive `Flow<List<ChatMessageEntity>>` that auto-updates the ViewModel when new messages are inserted.
* `saveMessage(message: ChatMessageEntity)`: Inserts a new message.
* `updateMessageStatus(messageId, status)`: Updates a message from "sent" to "delivered" or "read".

### Remote Socket Operations (SocketManager Wrappers)
* `sendMessage()`, `sendTyping()`, `sendStopTyping()`: Emits events to the socket.
* `markDelivered()`, `markRead()`: Emits `message-status` events.
* `listenIncoming(onMessage)`: Listens for `chat-message` events from the server.
* `fetchHistoryFromServer(sessionId, limit, before)`: Uses `suspendCancellableCoroutine` to await the `get-history` socket ack, parses the JSON response, and saves missing messages directly into the Room DB.

---

## Data Flow for Sending a Message
1. User types and clicks Send in `ChatInputBar`.
2. `ChatActivity` creates a JSON payload and calls `viewModel.sendMessage(payload)`.
3. `ChatViewModel` creates a `ChatMessageEntity` (status="sent") and calls `repository.saveMessage()`.
4. `ChatViewModel` calls `repository.sendMessage(payload)`.
5. `ChatRepository` emits `chat-message` via `SocketManager`.
6. Room DB detects the insertion and emits the new list to `viewModel.history`, instantly updating the UI.

## Data Flow for Receiving a Message
1. `SocketManager` receives `chat-message` and triggers `repository.listenIncoming`.
2. `ChatViewModel` parses the payload, creates a `ChatMessageEntity`, and calls `repository.saveMessage()`.
3. `ChatViewModel` automatically calls `repository.markRead()` to send a double-tick back to the sender.
4. Room DB emits the updated list to `viewModel.history`, updating the UI.
