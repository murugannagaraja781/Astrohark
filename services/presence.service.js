const User = require('../models/User');
const { 
    userSockets, 
    socketToUser, 
    userActiveSession, 
    savedAstroStatus,
    sessionDisconnectTimeouts,
    SESSION_GRACE_PERIOD
} = require('./socketStore');
const { broadcastAstroUpdate } = require('./astrologer.service');
const billingService = require('./billing.service');
const Session = require('../models/Session');
const { activeSessions } = require('./socketStore');

/**
 * PresenceService
 * Handles all logic for User Online/Offline/Busy states.
 */
class PresenceService {
    /**
     * Mark a user as connected via Socket.
     */
    async handleConnect(socket, userId, io) {
        if (!userId) return;

        // 1. Cancel any pending disconnect timeouts
        if (sessionDisconnectTimeouts.has(userId)) {
            clearTimeout(sessionDisconnectTimeouts.get(userId));
            sessionDisconnectTimeouts.delete(userId);
            console.log(`[Presence] Cancelled disconnect grace period for ${userId} (reconnected).`);
        }

        // 2. Update Maps
        socketToUser.set(socket.id, userId);
        userSockets.set(userId, socket.id);
        socket.join(userId);

        try {
            const user = await User.findOne({ userId });
            if (user) {
                // If user is an astrologer and they are marked as "Available" (Manual Toggle),
                // ensure they are marked "Online" in the DB.
                if (user.role === 'astrologer' && user.isAvailable) {
                    user.isOnline = true;
                    user.lastSeen = new Date();
                    await user.save();
                    broadcastAstroUpdate(io, process.env.SERVER_URL);
                    console.log(`[Presence] Astrologer ${user.name} (${userId}) connected and verified ONLINE.`);
                } else if (user.role === 'client') {
                    user.isOnline = true;
                    user.lastSeen = new Date();
                    await user.save();
                    console.log(`[Presence] Client ${user.name || userId} connected.`);
                }
            }
        } catch (err) {
            console.error('[Presence][handleConnect] Error:', err);
        }
    }

    /**
     * Handle Socket Disconnect.
     * Note: Per user request, we DO NOT mark astrologers as Offline here if isAvailable is true.
     */
    async handleDisconnect(socket, io) {
        const userId = socketToUser.get(socket.id);
        if (!userId) return;

        console.log(`[Presence] Socket disconnected: ${socket.id} for user ${userId}`);

        // Cleanup Maps
        socketToUser.delete(socket.id);
        if (userSockets.get(userId) === socket.id) {
            userSockets.delete(userId);
        }

        try {
            const user = await User.findOne({ userId });
            if (user) {
                user.lastSeen = new Date();
                await user.save();
                console.log(`[Presence] ${user.name} disconnected. (Stayed Online in DB: ${user.isOnline})`);
            }

            // --- Session Grace Period ---
            const sid = userActiveSession.get(userId);
            if (sid) {
                console.log(`[Session] User ${userId} disconnected. Starting grace period for Session ${sid}`);

                if (sessionDisconnectTimeouts.has(userId)) {
                    clearTimeout(sessionDisconnectTimeouts.get(userId));
                }

                const timeoutId = setTimeout(async () => {
                    console.log(`[Session] Grace period expired for ${userId}. Ending Session ${sid}`);
                    sessionDisconnectTimeouts.delete(userId);

                    const s = activeSessions.get(sid);
                    if (s) {
                        Session.updateOne({ sessionId: sid }, { endTime: Date.now() }).catch(() => { });
                        
                        const otherUserId = s.users.find(u => u !== userId);
                        billingService.endSessionRecord(sid, () => broadcastAstroUpdate(io, process.env.SERVER_URL));

                        if (otherUserId) {
                            io.to(otherUserId).emit('session-ended', { sessionId: sid, reason: 'partner_disconnected' });
                        }
                    }
                }, SESSION_GRACE_PERIOD);

                sessionDisconnectTimeouts.set(userId, timeoutId);
            }
        } catch (err) {
            console.error('[Presence][handleDisconnect] Error:', err);
        }
    }

    /**
     * Explicit Status Update (Manual Toggle in UI)
     */
    async updateStatus(userId, isOnline, io) {
        try {
            const user = await User.findOne({ userId });
            if (user) {
                user.isChatOnline = isOnline;
                user.isAudioOnline = isOnline;
                user.isVideoOnline = isOnline;
                user.isOnline = isOnline;
                user.isAvailable = isOnline; // Manual toggle is the source of truth
                user.lastSeen = new Date();
                await user.save();
                
                broadcastAstroUpdate(io, process.env.SERVER_URL);
                console.log(`[Presence] ${user.name} manually set status to: ${isOnline ? 'ONLINE' : 'OFFLINE'}`);
                return { ok: true, user };
            }
            return { ok: false, error: 'User not found' };
        } catch (err) {
            console.error('[Presence][updateStatus] Error:', err);
            return { ok: false, error: 'Internal server error' };
        }
    }

    /**
     * Explicit Logout
     */
    async handleLogout(socket, io) {
        const userId = socketToUser.get(socket.id);
        if (!userId) return { ok: false, error: 'Not logged in' };

        try {
            const user = await User.findOne({ userId });
            if (user) {
                user.isOnline = false;
                user.isAvailable = false;
                user.isChatOnline = false;
                user.isAudioOnline = false;
                user.isVideoOnline = false;
                user.lastSeen = new Date();
                await user.save();

                broadcastAstroUpdate(io, process.env.SERVER_URL);
                console.log(`[Presence] ${user.name} logged out explicitly.`);
            }

            userSockets.delete(userId);
            socketToUser.delete(socket.id);
            return { ok: true };
        } catch (err) {
            console.error('[Presence][handleLogout] Error:', err);
            return { ok: false, error: 'Internal server error' };
        }
    }

    /**
     * Set Busy State (Called when call starts/ends)
     */
    async setBusy(userId, isBusy, io) {
        try {
            const user = await User.findOne({ userId });
            if (user && user.role === 'astrologer') {
                user.isBusy = isBusy;
                await user.save();
                broadcastAstroUpdate(io, process.env.SERVER_URL);
                console.log(`[Presence] ${user.name} busy state: ${isBusy}`);
            }
        } catch (err) {
            console.error('[Presence][setBusy] Error:', err);
        }
    }
}

module.exports = new PresenceService();
