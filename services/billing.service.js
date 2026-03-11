const User = require('../models/User');
const Session = require('../models/Session');
const PairMonth = require('../models/PairMonth');
const { activeSessions, userActiveSession, sessionDisconnectTimeouts } = require('./socketStore');

let SLAB_RATES = {
    1: 0.30,
    2: 0.35,
    3: 0.40,
    4: 0.50
};

// Global io instance will be set from server.js or socketHandler
let io;
const setIo = (ioInstance) => { io = ioInstance; };

async function processBillingCharge(sessionId, durationSeconds, minuteIndex, type) {
    try {
        const session = await Session.findOne({ sessionId });
        if (!session) return;

        const astro = await User.findOne({ userId: session.astrologerId });
        if (!astro) return;

        const client = await User.findOne({ userId: session.clientId });
        if (!client) return;

        let pricePerMin = 10;
        if (astro.price && astro.price > 0) {
            pricePerMin = parseInt(astro.price);
        } else {
            if (session.type === 'audio') pricePerMin = 15;
            if (session.type === 'video') pricePerMin = 20;
        }

        console.log(`[Billing] Session ${sessionId} | Type: ${session.type} | Price: ${pricePerMin}/min | Minute: ${minuteIndex}`);

        let amountToCharge = 0;
        let astroShare = 0;
        let adminShare = 0;
        let reason = '';

        if (type === 'first_60_full') {
            amountToCharge = pricePerMin;
            adminShare = amountToCharge;
            astroShare = 0;
            reason = 'first_60';
        } else if (type === 'early_exit') {
            amountToCharge = pricePerMin;
            adminShare = amountToCharge;
            astroShare = 0;
            reason = 'first_60_min_charge';
        } else if (type === 'slab') {
            const activeSess = activeSessions.get(sessionId);
            const currentSlab = activeSess?.currentSlab || 3;
            const rate = SLAB_RATES[currentSlab] || 0.30;

            amountToCharge = pricePerMin;
            astroShare = amountToCharge * rate;
            adminShare = amountToCharge - astroShare;
            reason = `slab_${currentSlab}`;
        } else if (type === 'fraction') {
            amountToCharge = pricePerMin;
            adminShare = amountToCharge;
            astroShare = 0;
            reason = 'fraction_roundup';
        } else {
            return;
        }

        const totalToDeduct = amountToCharge;
        if (client.walletBalance >= totalToDeduct) {
            let mainDeduct = totalToDeduct * 0.7;
            let superDeduct = totalToDeduct * 0.3;

            if (client.superWalletBalance > 0) {
                if (client.superWalletBalance >= superDeduct) {
                    client.superWalletBalance -= superDeduct;
                } else {
                    const availableSuper = client.superWalletBalance;
                    client.superWalletBalance = 0;
                    mainDeduct += (superDeduct - availableSuper);
                }
            } else {
                mainDeduct = totalToDeduct;
            }

            client.walletBalance -= mainDeduct;
            await client.save();

            if (astroShare > 0) {
                astro.walletBalance += astroShare;
                astro.totalEarnings = (astro.totalEarnings || 0) + astroShare;
                await astro.save();
            }

            // Update running totals in activeSession map
            const s = activeSessions.get(sessionId);
            if (s) {
                s.totalDeducted = (s.totalDeducted || 0) + totalToDeduct;
                s.totalEarned = (s.totalEarned || 0) + astroShare;
            }
        }
    } catch (e) {
        console.error('processBillingCharge error:', e);
    }
}

async function endSessionRecord(sessionId, broadcastAstroUpdate) {
    const s = activeSessions.get(sessionId);
    if (!s) return;

    const endTime = Date.now();
    const billableSeconds = s.elapsedBillableSeconds || 0;

    await Session.updateOne({ sessionId }, {
        endTime,
        duration: billableSeconds * 1000,
        totalEarned: s.totalEarned || 0,
        totalCharged: s.totalDeducted || 0,
        status: 'ended'
    });

    if (s.pairMonthId) {
        await PairMonth.updateOne(
            { _id: s.pairMonthId },
            { $inc: { slabLockedAt: billableSeconds } }
        );
    }

    if (billableSeconds > 0 && billableSeconds < 60) {
        await processBillingCharge(sessionId, billableSeconds, 1, 'early_exit');
    } else if (billableSeconds > 60) {
        const lastBilled = s.lastBilledMinute || 1;
        const totalMinutes = Math.ceil(billableSeconds / 60);

        if (totalMinutes > lastBilled) {
            for (let i = lastBilled + 1; i <= totalMinutes; i++) {
                const isFraction = (i === totalMinutes && (billableSeconds % 60) !== 0);
                const billingType = isFraction ? 'fraction' : 'slab';
                await processBillingCharge(sessionId, 60, i, billingType);
            }
        }
    }

    activeSessions.delete(sessionId);
    if (s.users) {
        s.users.forEach((u) => {
            if (userActiveSession.get(u) === sessionId) {
                userActiveSession.delete(u);
            }
            if (sessionDisconnectTimeouts.has(u)) {
                clearTimeout(sessionDisconnectTimeouts.get(u));
                sessionDisconnectTimeouts.delete(u);
            }
        });
    }

    const payload = {
        reason: 'ended',
        summary: {
            deducted: s.totalDeducted || 0,
            earned: s.totalEarned || 0,
            duration: billableSeconds
        }
    };

    if (io) {
        if (s.clientId) io.to(s.clientId).emit('session-ended', payload);
        if (s.astrologerId) io.to(s.astrologerId).emit('session-ended', payload);
    }

    User.updateMany({ userId: { $in: s.users }, role: 'astrologer' }, { isBusy: false })
        .then(() => {
            if (broadcastAstroUpdate) broadcastAstroUpdate();
        })
        .catch(e => console.error('Error clearing busy:', e));
}

module.exports = {
    processBillingCharge,
    endSessionRecord,
    setIo,
    setSlabRates: (rates) => { SLAB_RATES = rates; }
};
