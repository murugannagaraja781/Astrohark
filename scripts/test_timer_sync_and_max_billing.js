require('dotenv').config();
const io = require('socket.io-client');
const mongoose = require('mongoose');
const assert = require('assert');

const MONGO_URI = process.env.MONGODB_URI || process.env.MONGO_URI;
const PORT = process.env.PORT || 3000;
const URL = `http://localhost:${PORT}`;

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

async function runTest() {
    console.log(`Connecting to MongoDB & sockets on ${URL}...`);
    await mongoose.connect(MONGO_URI);
    console.log('✓ DB Connected');

    const User = require('../models/User');

    const clientId = 'test_client_sync_99';
    const astroId = 'test_astro_sync_99';

    await User.findOneAndUpdate(
        { userId: clientId },
        { userId: clientId, name: 'Test Client Timer', phone: '8999999991', role: 'client', walletBalance: 1000, isNewUser: false, isOnline: true },
        { upsert: true, returnDocument: 'after' }
    );

    await User.findOneAndUpdate(
        { userId: astroId },
        { userId: astroId, name: 'Test Astro Timer', phone: '9999999991', role: 'astrologer', price: 20, isOnline: true, isAvailable: true },
        { upsert: true, returnDocument: 'after' }
    );

    const clientSocket = io(URL, { reconnection: false });
    const astroSocket = io(URL, { reconnection: false });

    let sessionId;
    let timerSyncReceived = 0;
    let sessionEndedSummary = null;

    try {
        await new Promise((resolve, reject) => {
            clientSocket.emit('register', { userId: clientId }, (res) => {
                if (res && res.ok) resolve(res);
                else reject(res ? res.error : 'Client register failed');
            });
        });

        await new Promise((resolve, reject) => {
            astroSocket.emit('register', { userId: astroId }, (res) => {
                if (res && res.ok) resolve(res);
                else reject(res ? res.error : 'Astro register failed');
            });
        });
        console.log('✓ Sockets registered for clientId & astroId');

        // Request session
        sessionId = await new Promise((resolve, reject) => {
            clientSocket.emit('request-session', { toUserId: astroId, type: 'chat' }, (res) => {
                if (res && res.sessionId) resolve(res.sessionId);
                else reject(res ? res.error : 'request-session failed');
            });
        });
        console.log('✓ Session requested:', sessionId);

        // Listen for session-timer-sync
        clientSocket.on('session-timer-sync', (data) => {
            timerSyncReceived++;
            console.log(`[Client Received] session-timer-sync: elapsed=${data.elapsedSeconds}s`);
        });

        astroSocket.on('session-timer-sync', (data) => {
            console.log(`[Astro Received] session-timer-sync: elapsed=${data.elapsedSeconds}s`);
        });

        clientSocket.on('session-ended', (data) => {
            sessionEndedSummary = data;
            console.log('[Client Received] session-ended summary:', data);
        });

        // Trigger session connect
        astroSocket.emit('session-connect', { sessionId });
        await sleep(300);
        clientSocket.emit('session-connect', { sessionId });

        console.log('Waiting 5 seconds for billing to start and timer-sync ticks...');
        await sleep(5000);

        assert(timerSyncReceived > 0, 'FAIL: No session-timer-sync events received!');
        console.log(`✓ session-timer-sync test PASSED (${timerSyncReceived} ticks received).`);

        // Now test MINIMUM / LOWER duration policy per user correction:
        // Server ticker will be ~4s. Client reports 3s.
        console.log('Emitting end-session with clientDuration = 3s (server ticker ~4s)...');
        clientSocket.emit('end-session', {
            sessionId,
            clientDuration: 3,
            duration: 3
        });

        await sleep(2000);

        assert(sessionEndedSummary, 'FAIL: session-ended summary not received');
        assert.strictEqual(sessionEndedSummary.summary.duration, 3, `FAIL: Expected duration 3s (lower timer), got ${sessionEndedSummary?.summary?.duration}`);
        console.log('✓ MINIMUM/LOWER duration billing policy test PASSED! Selected 3s over higher server ticker.');

        console.log('\n=============================================');
        console.log('ALL TIMER SYNC & LOWER DURATION BILLING TESTS PASSED 🚀');
        console.log('=============================================\n');

    } catch (err) {
        console.error('❌ Test Failed:', err);
        process.exitCode = 1;
    } finally {
        clientSocket.disconnect();
        astroSocket.disconnect();
        await mongoose.connection.close();
        process.exit();
    }
}

runTest();
