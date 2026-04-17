const mongoose = require('mongoose');

const SessionSchema = new mongoose.Schema({
    sessionId: { type: String, unique: true },
    clientId: String,
    astrologerId: String,
    clientConnectedAt: Number,
    astrologerConnectedAt: Number,
    actualBillingStart: Number,
    sessionEndAt: Number,
    status: { type: String, enum: ['ringing', 'answered', 'rejected', 'missed', 'active', 'ended'], default: 'ringing' },

    // Legacy/Compatibility Fields
    fromUserId: String,
    toUserId: String,
    type: String,
    startTime: Number,
    endTime: Number,
    duration: Number,
    totalEarned: Number,
    totalCharged: Number
});

SessionSchema.index({ clientId: 1 });
SessionSchema.index({ astrologerId: 1 });
SessionSchema.index({ status: 1 });

module.exports = mongoose.model('Session', SessionSchema);
