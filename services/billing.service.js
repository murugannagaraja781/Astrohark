const User = require('../models/User');
const Session = require('../models/Session');
const PairMonth = require('../models/PairMonth');
const BillingLedger = require('../models/BillingLedger');
const crypto = require('crypto');
const { activeSessions, userActiveSession, sessionDisconnectTimeouts } = require('./socketStore');
const { broadcastAstroUpdate } = require('./astrologer.service');
// presenceService removed to break circular dependency. Required inside endSessionRecord.


// Removed SLAB_RATES

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
        if (client.isNewUser) {
            const remainingMinutes = Math.max(1, 5 - (minuteIndex || 1) + 1);
            pricePerMin = client.walletBalance > 0 ? (client.walletBalance / remainingMinutes) : 5;
        } else if (astro.price && parseFloat(astro.price) > 0) {
            pricePerMin = parseFloat(astro.price);
        } else {
            if (session.type === 'audio') pricePerMin = 15;
            if (session.type === 'video') pricePerMin = 20;
        }

        console.log(`[Billing] Session ${sessionId} | Type: ${session.type} | Price: ${pricePerMin}/min | Minute: ${minuteIndex}`);

        const activeSess = activeSessions.get(sessionId);

        let amountToCharge = pricePerMin;
        let astroShare = amountToCharge * 0.50; // 50% for Astrologer
        let adminShare = amountToCharge - astroShare; // 50% for Admin
        let reason = '';

        if (type === 'first_60_full') {
            reason = 'first_60';
        } else if (type === 'early_exit') {
            reason = 'first_60_min_charge';
        } else if (type === 'slab' || type === 'ongoing') {
            reason = 'ongoing_minute';
        } else if (type === 'fraction') {
            reason = 'fraction_roundup';
        } else {
            return;
        }

        const totalToDeduct = amountToCharge;
        if (client.walletBalance > 0) {
            let actualDeduct = Math.min(client.walletBalance, totalToDeduct);
            if (actualDeduct < totalToDeduct) {
                // Adjust pro-rated astro share if balance is lower than min price
                astroShare = actualDeduct * 0.50;
                adminShare = actualDeduct - astroShare;
            }

            let mainDeduct = actualDeduct * 0.7;
            let superDeduct = actualDeduct * 0.3;

            if (client.superWalletBalance > 0) {
                if (client.superWalletBalance >= superDeduct) {
                    client.superWalletBalance -= superDeduct;
                } else {
                    const availableSuper = client.superWalletBalance;
                    client.superWalletBalance = 0;
                    mainDeduct += (superDeduct - availableSuper);
                }
            } else {
                mainDeduct = actualDeduct;
            }

            // Atomic update to client balance
            const updatedClient = await User.findOneAndUpdate(
                { userId: client.userId },
                { $inc: { walletBalance: -mainDeduct, superWalletBalance: -superDeduct } },
                { new: true }
            );

            // Atomic update to astrologer balance & earnings
            let updatedAstro = astro;
            if (astroShare > 0) {
                updatedAstro = await User.findOneAndUpdate(
                    { userId: astro.userId },
                    { $inc: { walletBalance: astroShare, totalEarnings: astroShare } },
                    { new: true }
                );
            }

            // --- Record in BillingLedger ---
            await BillingLedger.create({
                billingId: crypto.randomUUID(),
                sessionId,
                minuteIndex,
                chargedToClient: actualDeduct,
                creditedToAstrologer: astroShare,
                adminAmount: adminShare,
                reason
            }).catch(e => console.error('[Billing] Ledger Creation Error:', e));

            // Update running totals in activeSession map
            const s = activeSessions.get(sessionId);
            if (s) {
                s.totalDeducted = (s.totalDeducted || 0) + actualDeduct;
                s.totalEarned = (s.totalEarned || 0) + astroShare;
            }

            // Notify Wallets in real time
            if (io) {
                const newClientBal = updatedClient ? updatedClient.walletBalance : 0;
                const newAstroBal = updatedAstro ? updatedAstro.walletBalance : astro.walletBalance;

                io.to(client.userId).emit('wallet-update', { balance: newClientBal });
                io.to(astro.userId).emit('wallet-update', { balance: newAstroBal });
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
    console.log(`[Billing][endSessionRecord] sessionId=${sessionId}, billableSeconds=${billableSeconds}`);

    await Session.updateOne({ sessionId }, {
        endTime,
        duration: billableSeconds * 1000,
        totalEarned: s.totalEarned || 0,
        totalCharged: s.totalDeducted || 0,
        status: 'ended'
    });

    if (s.clientId && billableSeconds > 0) {
        await User.updateOne({ userId: s.clientId }, { isNewUser: false });
    }

    if (s.astrologerId && billableSeconds > 0) {
        await User.updateOne({ userId: s.astrologerId }, { $inc: { orderCount: 1 } });
    }

    if (s.pairMonthId) {
        await PairMonth.updateOne(
            { _id: s.pairMonthId },
            { $inc: { slabLockedAt: billableSeconds } }
        );
    }

    if (billableSeconds > 0 && billableSeconds <= 60) {
        await processBillingCharge(sessionId, billableSeconds, 1, 'early_exit');
    } else if (billableSeconds > 60) {
        const lastBilled = s.lastBilledMinute || 1;
        const totalMinutes = Math.ceil(billableSeconds / 60);

        if (totalMinutes > lastBilled) {
            for (let i = lastBilled + 1; i <= totalMinutes; i++) {
                const isFraction = (i === totalMinutes && (billableSeconds % 60) !== 0);
                const billingType = isFraction ? 'fraction' : 'ongoing';
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

    if (s.astrologerId) {
        require('./presence.service').setBusy(s.astrologerId, false, io);

    } else {
        User.updateMany({ userId: { $in: s.users }, role: 'astrologer' }, { isBusy: false })
            .then(() => { if (broadcastAstroUpdate) broadcastAstroUpdate(); })
            .catch(e => console.error('Error clearing busy:', e));
    }
}

module.exports = {
    processBillingCharge,
    endSessionRecord,
    setIo,
    setSlabRates: (rates) => { SLAB_RATES = rates; }
};
