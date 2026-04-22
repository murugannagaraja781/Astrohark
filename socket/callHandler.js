const crypto = require('crypto');
const User = require('../models/User');
const Session = require('../models/Session');
const ChatMessage = require('../models/ChatMessage');
const PairMonth = require('../models/PairMonth'); // Added for billing logic
const { sendFcmV1Push } = require('../services/push.service');
const { userSockets, socketToUser, activeSessions, userActiveSession, sessionDisconnectTimeouts, SESSION_GRACE_PERIOD } = require('../services/socketStore');
const billingService = require('../services/billing.service');
const presenceService = require('../services/presence.service');
const { broadcastAstroUpdate } = require('../services/astrologer.service');

// Helper to get partner ID
function getOtherUserIdFromSession(sessionId, userId) {
    const s = activeSessions.get(sessionId);
    if (!s) return null;
    return s.users?.find(u => u !== userId);
}

module.exports = (io, socket, SERVER_URL, broadcastAstroUpdate) => {
    const logError = (event, err) => {
        console.error(`[CallHandler][${event}] error:`, err);
    };

    // --- Request Session (Call Initiation) ---
    socket.on('request-session', async (data, cb) => {
        try {
            const { toUserId, type, birthData } = data || {};
            let fromUserId = socketToUser.get(socket.id) || data.fromUserId;

            if (!fromUserId) {
                console.warn(`[CallHandler][request-session] unauthorized from socket ${socket.id}`);
                return typeof cb === 'function' && cb({ ok: false, error: 'Not registered' });
            }

            if (!toUserId || !type) return typeof cb === 'function' && cb({ ok: false, error: 'Missing fields' });

            console.log(`[CallHandler][request-session] from=${fromUserId}, to=${toUserId}, type=${type}`);

            const toUser = await User.findOne({ userId: toUserId });
            const fromUser = await User.findOne({ userId: fromUserId });

            if (!toUser) {
                console.warn(`[CallHandler][request-session] target user ${toUserId} not found`);
                return typeof cb === 'function' && cb({ ok: false, error: 'User not found' });
            }

            // Self-registration fix
            if (!socketToUser.has(socket.id) && fromUserId) {
                socketToUser.set(socket.id, fromUserId);
                userSockets.set(fromUserId, socket.id);
                socket.join(fromUserId);
            }

            if (fromUser && fromUser.role === 'client' && (fromUser.walletBalance || 0) <= 0) {
                console.warn(`[CallHandler][request-session] Insufficient balance for ${fromUserId}`);
                return typeof cb === 'function' && cb({ ok: false, error: 'Insufficient Main Balance.' });
            }

            if (userActiveSession.has(toUserId)) {
                const existingId = userActiveSession.get(toUserId);
                const existingS = activeSessions.get(existingId);
                if (!existingS) {
                    userActiveSession.delete(toUserId);
                } else if (existingS.users.includes(fromUserId)) {
                    // DEDUPLICATION: If a session between these same two users already exists and is recent (within 15s), reuse it.
                    // This prevents "End Session" loops when users click the call button multiple times or during network retries.
                    const age = Date.now() - (existingS.startedAt || 0);
                    if (existingS.status === 'ringing' || age < 15000) {
                        console.log(`[CallHandler][request-session] Deduplicating request. Reusing active session ${existingId}`);
                        return typeof cb === 'function' && cb({ ok: true, sessionId: existingId });
                    }
                    console.log(`[CallHandler][request-session] Ending stale session ${existingId} to start new one.`);
                    await billingService.endSessionRecord(existingId, broadcastAstroUpdate);
                } else {
                    return typeof cb === 'function' && cb({ ok: false, error: 'User busy' });
                }
            }

            const sessionId = crypto.randomUUID();
            let clientId = null;
            let astrologerId = null;

            if (fromUser?.role === 'client') clientId = fromUserId;
            if (fromUser?.role === 'astrologer') astrologerId = fromUserId;
            if (toUser?.role === 'client') clientId = toUserId;
            if (toUser?.role === 'astrologer') astrologerId = toUserId;

            await Session.create({
                sessionId, fromUserId, toUserId, type, startTime: Date.now(),
                clientId, astrologerId, status: 'ringing'
            });

            activeSessions.set(sessionId, {
                type,
                users: [fromUserId, toUserId],
                startedAt: Date.now(),
                clientId,
                astrologerId,
                status: 'ringing',
                elapsedBillableSeconds: 0,
                lastBilledMinute: 0,
                actualBillingStart: null,
                totalDeducted: 0,
                totalEarned: 0
            });
            userActiveSession.set(fromUserId, sessionId);
            userActiveSession.set(toUserId, sessionId);

            console.log(`[CallHandler][request-session] Emitting incoming-session to room ${toUserId} | callerName: ${fromUser?.name}`);
            io.to(toUserId).emit('incoming-session', {
                sessionId, 
                fromUserId, // Legacy
                callerId: fromUserId, // Map to FCM style
                callerName: fromUser?.name || 'User',
                type, 
                birthData: birthData || null
            });

            if (toUser.fcmToken) {
                const isVideo = type === 'video';
                const fcmData = {
                    type: 'INCOMING_CALL', sessionId, callType: type,
                    callerName: fromUser?.name || 'User',
                    callerId: fromUserId, timestamp: Date.now().toString(),
                    birthData: JSON.stringify(birthData || {})
                };
                const fcmNotif = {
                    title: `📞 Incoming ${isVideo ? 'Video' : 'Audio'} Call`,
                    body: `${fromUser?.name || 'Someone'} is calling you for ${isVideo ? 'video' : 'audio'} consultation`
                };
                sendFcmV1Push(toUser.fcmToken, fcmData, fcmNotif, toUserId).catch(e => logError('FCM', e));
            }

            if (typeof cb === 'function') cb({ ok: true, sessionId });

            // Timeout for no answer (30 seconds)
            setTimeout(async () => {
                const s = activeSessions.get(sessionId);
                if (s && s.status === 'ringing') {
                    console.log(`[CallHandler][Timeout] Missed call from ${fromUserId} to ${toUserId}`);
                    
                    io.to(fromUserId).emit('session-ended', { sessionId, reason: 'no_answer' });
                    io.to(toUserId).emit('session-ended', { sessionId, reason: 'missed' });
                    
                    userActiveSession.delete(fromUserId);
                    userActiveSession.delete(toUserId);
                    // AUTO-OFFLINE LOGIC: Modified as per USER REQUEST (Astrologer stays online)
                    const astro = await User.findOne({ userId: toUserId });
                    if (astro && astro.role === 'astrologer') {
                        /*
                        astro.isOnline = false;
                        astro.isChatOnline = false;
                        astro.isAudioOnline = false;
                        astro.isVideoOnline = false;
                        astro.isAvailable = false;
                        await astro.save();
                        
                        if (broadcastAstroUpdate) broadcastAstroUpdate();

                        // Notify Super Admin
                        io.to('superadmin').emit('admin-notification', {
                            text: `Missed Call Alert: ${astro.name} marked OFFLINE due to no response.`,
                            type: 'missed_call'
                        });
                        */

                        // Log to file
                        const fs = require('fs');
                        const path = require('path');
                        const logPath = path.join(__dirname, '../missed_calls_log.txt');
                        const logEntry = `${new Date().toLocaleString()} | Astro: ${astro.name} (${astro.userId}) | Client: ${fromUserId} | Session: ${sessionId}\n`;
                        fs.appendFile(logPath, logEntry, (err) => {
                            if (err) console.error('[CallHandler] Failed to log missed call:', err);
                        });
                    }
                }
            }, 30000);

        } catch (err) {
            logError('request-session', err);
            if (typeof cb === 'function') cb({ ok: false, error: 'Internal error' });
        }
    });

    // --- Answer Session ---
    socket.on('answer-session', async (data, cb) => {
        try {
            const { sessionId, toUserId, type, accept } = data || {};
            let fromUserId = socketToUser.get(socket.id) || data.fromUserId;
            
            if (!fromUserId || !sessionId || !toUserId) {
                console.warn(`[CallHandler][answer-session] missing data for session ${sessionId}`);
                return typeof cb === 'function' && cb({ ok: false, error: 'Missing data' });
            }

            // Update status to prevent timeout cleanup
            const s = activeSessions.get(sessionId);
            if (s) {
                s.status = accept ? 'answered' : 'rejected';
                if (!accept) {
                    billingService.endSessionRecord(sessionId, broadcastAstroUpdate);
                }
            }
            
            // Mark in DB as well
            Session.updateOne({ sessionId }, { status: accept ? 'answered' : 'rejected' }).catch(() => {});

            io.to(toUserId).emit('session-answered', {
                sessionId, fromUserId, type, accept: !!accept
            });

            console.log(`[CallHandler][answer-session] ${accept ? '✅ ACCEPTED' : '❌ REJECTED'} | session=${sessionId}, type=${type} | by=${fromUserId} for=${toUserId}`);
            if (typeof cb === 'function') cb({ ok: true });
        } catch (err) {
            logError('answer-session', err);
            if (typeof cb === 'function') cb({ ok: false, error: 'Internal error' });
        }
    });

    // --- Answer session from Android Native (doesn't have toUserId) ---
    socket.on('answer-session-native', async (data, cb) => {
        try {
            const { sessionId, accept, callType } = data || {};
            const astrologerId = socketToUser.get(socket.id);

            if (!astrologerId || !sessionId) {
                if (typeof cb === 'function') cb({ ok: false, error: 'Invalid data' });
                return;
            }

            const session = activeSessions.get(sessionId);
            if (session) {
                session.status = accept ? 'answered' : 'rejected';
            }
            Session.updateOne({ sessionId }, { status: accept ? 'answered' : 'rejected' }).catch(() => {});

            if (!session) {
                const dbSession = await Session.findOne({ sessionId });
                if (!dbSession) {
                    if (typeof cb === 'function') cb({ ok: false, error: 'Session not found' });
                    return;
                }

                const fromUserId = dbSession.fromUserId;
                if (accept) {
                    io.to(fromUserId).emit('session-answered', {
                        sessionId, 
                        fromUserId: astrologerId, // Legacy
                        astrologerId: astrologerId, // Specific
                        type: callType || dbSession.type, 
                        accept: true
                    });
                    if (typeof cb === 'function') cb({ ok: true, fromUserId });
                } else {
                    io.to(fromUserId).emit('session-answered', {
                        sessionId, fromUserId: astrologerId, type: callType || dbSession.type, accept: false
                    });
                    billingService.endSessionRecord(sessionId, broadcastAstroUpdate);
                    if (typeof cb === 'function') cb({ ok: true });
                }
                return;
            }

            const fromUserId = session.users.find(u => u !== astrologerId);
            if (accept) {
                io.to(fromUserId).emit('session-answered', {
                    sessionId, fromUserId: astrologerId, type: callType || session.type, accept: true
                });
                console.log(`[CallHandler][answer-session-native] ✅ ACCEPTED | session=${sessionId} | by=${astrologerId} for=${fromUserId}`);
                if (typeof cb === 'function') cb({ ok: true, fromUserId });
            } else {
                io.to(fromUserId).emit('session-answered', {
                    sessionId, fromUserId: astrologerId, type: callType || session.type, accept: false
                });
                console.log(`[CallHandler][answer-session-native] ❌ REJECTED | session=${sessionId} | by=${astrologerId} for=${fromUserId}`);
                billingService.endSessionRecord(sessionId, broadcastAstroUpdate);
                if (typeof cb === 'function') cb({ ok: true });
            }
        } catch (err) {
            logError('answer-session-native', err);
            if (typeof cb === 'function') cb({ ok: false, error: 'Internal error' });
        }
    });

    // --- Signaling (WebRTC) ---
    socket.on('signal', (data) => {
        try {
            const { sessionId, toUserId, signal } = data || {};
            const fromUserId = socketToUser.get(socket.id) || data.fromUserId;
            
            const signalType = signal?.type || (signal?.candidate ? 'candidate' : 'unknown');
            console.log(`[CallHandler][signal] type=${signalType} | from=${fromUserId} to=${toUserId} | session=${sessionId}`);

            if (toUserId) {
                console.log(`[CallHandler][signal] Forwarding ${signalType} to room ${toUserId}`);
                io.to(toUserId).emit('signal', { sessionId, fromUserId, signal });
            }
        } catch (err) {
            logError('signal', err);
        }
    });

    // --- Session Connect (Billing initialization) ---
    socket.on('session-connect', async (data) => {
        try {
            const { sessionId } = data || {};
            const userId = socketToUser.get(socket.id);
            if (!userId || !sessionId) return;

            console.log(`[CallHandler][session-connect] User ${userId} joined session ${sessionId}`);

            const session = await Session.findOne({ sessionId });
            if (!session) return;

            const now = Date.now();
            let updated = false;

            if (userId === session.clientId && !session.clientConnectedAt) {
                session.clientConnectedAt = now;
                updated = true;
            } else if (userId === session.astrologerId && !session.astrologerConnectedAt) {
                session.astrologerConnectedAt = now;
                updated = true;
            }

            if (updated) await session.save();

            // Check if both are connected to start billing
            if (session.clientConnectedAt && session.astrologerConnectedAt && !session.actualBillingStart) {
                const billingStart = Math.max(session.clientConnectedAt, session.astrologerConnectedAt) + 2000;
                session.actualBillingStart = billingStart;
                session.status = 'active';
                await session.save();

                const activeSession = activeSessions.get(sessionId);
                if (activeSession) {
                    activeSession.status = 'active';
                    activeSession.actualBillingStart = billingStart;
                    activeSession.elapsedBillableSeconds = 0;
                    activeSession.lastBilledMinute = 1;
                    activeSession.clientId = session.clientId;
                    activeSession.astrologerId = session.astrologerId;
                    activeSession.totalDeducted = 0;
                    activeSession.totalEarned = 0;
                    
                    // Init Slab for Pair
                    const currentMonth = new Date().toISOString().slice(0, 7);
                    const pairId = `${session.clientId}_${session.astrologerId}`;
                    let pairRec = await PairMonth.findOne({ pairId, yearMonth: currentMonth });
                    if (!pairRec) {
                        pairRec = await PairMonth.create({
                            pairId, clientId: session.clientId, astrologerId: session.astrologerId,
                            yearMonth: currentMonth, currentSlab: 3
                        });
                    }
                    activeSession.pairMonthId = pairRec._id;
                    activeSession.currentSlab = pairRec.currentSlab;
                    activeSession.initialPairSeconds = pairRec.slabLockedAt || 0;
                }

                // Notify both
                const client = await User.findOne({ userId: session.clientId });
                const astro = await User.findOne({ userId: session.astrologerId });
                const clientBalance = client?.walletBalance || 0;
                const ratePerMinute = astro?.price || 10;
                const availableMinutes = Math.floor(clientBalance / ratePerMinute);

                io.to(session.clientId).emit('billing-started', { startTime: billingStart, clientBalance, availableMinutes });
                io.to(session.astrologerId).emit('billing-started', { startTime: billingStart, clientBalance, ratePerMinute, availableMinutes });
                
                console.log(`[CallHandler][session-connect] 🚀 BOTH CONNECTED. Call Active. Billing started for ${sessionId}`);
                    
                    // MARK BUSY
                    presenceService.setBusy(session.astrologerId, true, io);
            }
        } catch (err) {
            logError('session-connect', err);
        }
    });

    // --- End Session (Call Termination) ---
    socket.on('end-session', async (data) => {
        try {
            const { sessionId } = data || {};
            if (sessionId) {
                console.log(`[CallHandler][end-session] terminating session ${sessionId}`);
                billingService.endSessionRecord(sessionId, () => {
                    if (broadcastAstroUpdate) broadcastAstroUpdate();
                });
            }
        } catch (err) {
            logError('end-session', err);
        }
    });

    // --- Save Intake Details ---
    socket.on('save-intake-details', async (data, cb) => {
        try {
            const userId = socketToUser.get(socket.id);
            if (!userId) return;

            const u = await User.findOne({ userId });
            if (u) {
                u.birthDetails = {
                    dob: `${data.year}-${String(data.month).padStart(2, '0')}-${String(data.day).padStart(2, '0')}`,
                    tob: `${String(data.hour).padStart(2, '0')}:${String(data.minute).padStart(2, '0')}`,
                    pob: data.city, lat: data.latitude, lon: data.longitude
                };
                u.name = data.name;
                u.intakeDetails = {
                    gender: data.gender, marital: data.marital,
                    occupation: data.occupation, topic: data.topic, partner: data.partner
                };
                await u.save();
                if (typeof cb === 'function') cb({ ok: true });

                const sessionId = userActiveSession.get(userId);
                if (sessionId) {
                    const partnerId = getOtherUserIdFromSession(sessionId, userId);
                    if (partnerId) {
                        io.to(partnerId).emit('client-birth-chart', { sessionId, fromUserId: userId, birthData: data });
                    }
                }
            }
        } catch (err) {
            logError('save-intake-details', err);
        }
    });

    // --- Monitor Disconnects during calls ---
    socket.on('disconnect', () => {
        const userId = socketToUser.get(socket.id);
        if (userId && userActiveSession.has(userId)) {
            const sessionId = userActiveSession.get(userId);
            console.log(`[CallHandler][disconnect] User ${userId} disconnected during active session ${sessionId}`);
        }
    });
};
