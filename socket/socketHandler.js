const crypto = require('crypto');
const User = require('../models/User');
const Session = require('../models/Session');
const ChatMessage = require('../models/ChatMessage');
const { sendFcmV1Push } = require('../services/push.service');
const {
    userSockets,
    socketToUser,
    userActiveSession,
    activeSessions,
    sessionDisconnectTimeouts,
    savedAstroStatus,
    offlineTimeouts,
    SESSION_GRACE_PERIOD
} = require('../services/socketStore');
const { broadcastAstroUpdate } = require('../services/astrologer.service');
const { endSessionRecord } = require('../services/billing.service');

function getOtherUserIdFromSession(sessionId, userId) {
    const s = activeSessions.get(sessionId);
    if (!s) return null;
    return s.users.find(u => u !== userId);
}

module.exports = (io, SERVER_URL) => {
    io.on('connection', (socket) => {
        console.log('Socket connected:', socket.id);

        // Register User / Astrologer
        socket.on('register', (data, cb) => {
            try {
                const { phone } = data || {};
                const userId = data.userId || socketToUser.get(socket.id);
                const query = phone ? { phone } : (userId ? { userId } : null);

                if (!query) {
                    if (typeof cb === 'function') cb({ ok: false, error: 'No identifier provided' });
                    return;
                }

                User.findOne(query).then(user => {
                    if (!user) {
                        if (typeof cb === 'function') cb({ ok: false, error: 'User not found' });
                        return;
                    }

                    const userId = user.userId;
                    userSockets.set(userId, socket.id);
                    socketToUser.set(socket.id, userId);

                    if (typeof cb === 'function') cb({
                        ok: true,
                        userId: user.userId,
                        role: user.role,
                        name: user.name,
                        walletBalance: user.walletBalance,
                        superWalletBalance: user.superWalletBalance || 0,
                        totalEarnings: user.totalEarnings || 0
                    });

                    // Cancel pending SESSION timeout
                    if (sessionDisconnectTimeouts.has(userId)) {
                        clearTimeout(sessionDisconnectTimeouts.get(userId));
                        sessionDisconnectTimeouts.delete(userId);
                    }

                    if (user.role === 'astrologer') {
                        if (user.isAvailable) {
                            user.isOnline = true;
                            user.save().then(() => broadcastAstroUpdate(io, SERVER_URL));
                        } else {
                            broadcastAstroUpdate(io, SERVER_URL);
                        }
                    }
                    if (user.role === 'superadmin') {
                        socket.join('superadmin');
                    }

                    socket.join(userId);
                });
            } catch (err) {
                console.error('register error', err);
                if (typeof cb === 'function') cb({ ok: false, error: 'Internal error' });
            }
        });

        // Rejoin Session
        socket.on('rejoin-session', (data) => {
            const { sessionId } = data || {};
            const userId = socketToUser.get(socket.id);
            if (sessionId && userId) {
                socket.join(sessionId);
                socket.to(sessionId).emit('peer-reconnected', { userId });
            }
        });

        // Toggle Status
        socket.on('toggle-status', async (data) => {
            const userId = data.userId || socketToUser.get(socket.id);
            if (!userId) return;
            try {
                let user = await User.findOne({ userId });
                if (!user || user.role !== 'astrologer') return;

                if (data.type === 'chat') user.isChatOnline = !!data.online;
                if (data.type === 'audio') user.isAudioOnline = !!data.online;
                if (data.type === 'video') user.isVideoOnline = !!data.online;

                user.isOnline = user.isChatOnline || user.isAudioOnline || user.isVideoOnline;
                user.isAvailable = user.isOnline;
                user.lastSeen = new Date();
                await user.save();
                broadcastAstroUpdate(io, SERVER_URL);
            } catch (e) { console.error(e); }
        });

        // Signaling
        socket.on('signal', (data) => {
            const { sessionId, toUserId, signal } = data || {};
            const fromUserId = socketToUser.get(socket.id);
            if (toUserId) {
                io.to(toUserId).emit('signal', { sessionId, fromUserId, signal });
            }
        });

        // End Session
        socket.on('end-session', async (data) => {
            const { sessionId } = data || {};
            if (sessionId) {
                endSessionRecord(sessionId, () => broadcastAstroUpdate(io, SERVER_URL));
            }
        });

        // Chat Message
        socket.on('chat-message', async (data) => {
            const { toUserId, sessionId, content, timestamp, messageId } = data || {};
            const fromUserId = socketToUser.get(socket.id);
            if (!fromUserId || !toUserId || !content || !messageId) return;

            socket.emit('message-status', { messageId, status: 'sent' });

            ChatMessage.create({
                messageId, sessionId, fromUserId, toUserId,
                text: content.text, timestamp: timestamp || Date.now()
            }).catch(e => console.error('ChatSave Error', e));

            io.to(toUserId).emit('chat-message', {
                fromUserId, content, sessionId, timestamp: timestamp || Date.now(), messageId
            });

            // FCM Push
            const toUser = await User.findOne({ userId: toUserId });
            if (toUser && toUser.fcmToken) {
                const payload = {
                    type: 'CHAT_MESSAGE',
                    sessionId: sessionId || '',
                    callerId: fromUserId,
                    text: (content.text || 'New message').substring(0, 200),
                    messageId,
                    timestamp: Date.now().toString()
                };
                sendFcmV1Push(toUser.fcmToken, payload, null, toUserId);
            }
        });

        // Disconnect Handler
        socket.on('disconnect', async () => {
            const userId = socketToUser.get(socket.id);
            if (userId) {
                console.log(`Socket disconnected: ${socket.id}, userId=${userId}`);

                // Cleanup global state
                socketToUser.delete(socket.id);
                if (userSockets.get(userId) === socket.id) {
                    userSockets.delete(userId);
                }

                const sid = userActiveSession.get(userId);
                
                // Set a timeout to mark as offline if no reconnection
                const offlineGraceTimeoutId = setTimeout(async () => {
                    const stillConnected = userSockets.has(userId);
                    if (!stillConnected) {
                        await User.updateOne({ userId }, { isOnline: false, isAvailable: false });
                        broadcastAstroUpdate(io, SERVER_URL);
                    }
                }, SESSION_GRACE_PERIOD);

                if (sid) {
                    if (sessionDisconnectTimeouts.has(userId)) {
                        clearTimeout(sessionDisconnectTimeouts.get(userId));
                    }

                    const timeoutId = setTimeout(() => {
                        sessionDisconnectTimeouts.delete(userId);
                        const s = activeSessions.get(sid);
                        if (s) {
                            const otherUserId = getOtherUserIdFromSession(sid, userId);
                            endSessionRecord(sid, () => broadcastAstroUpdate(io, SERVER_URL));
                            if (otherUserId) {
                                io.to(otherUserId).emit('session-ended', {
                                    sessionId: sid,
                                    reason: 'partner_disconnected'
                                });
                            }
                        }
                    }, SESSION_GRACE_PERIOD);

                    sessionDisconnectTimeouts.set(userId, timeoutId);
                }
            }
        });
    });
};
