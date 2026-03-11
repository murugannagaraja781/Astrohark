// In-Memory stores for sockets and sessions
const userSockets = new Map(); // userId -> socketId
const socketToUser = new Map(); // socketId -> userId
const userActiveSession = new Map(); // userId -> sessionId
const activeSessions = new Map(); // sessionId -> { type, users... }
const pendingMessages = new Map();
const offlineTimeouts = new Map(); // userId -> timeoutId
const savedAstroStatus = new Map(); // userId -> { chat, audio, video, timestamp }
const sessionDisconnectTimeouts = new Map(); // userId -> timeoutId
const paymentTokens = new Map();

module.exports = {
    userSockets,
    socketToUser,
    userActiveSession,
    activeSessions,
    pendingMessages,
    offlineTimeouts,
    savedAstroStatus,
    sessionDisconnectTimeouts,
    paymentTokens
};
