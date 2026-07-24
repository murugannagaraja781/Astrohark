const mongoose = require('mongoose');

const BillingLedgerSchema = new mongoose.Schema({
    billingId: { type: String, unique: true },
    sessionId: { type: String, required: true, index: true },
    minuteIndex: { type: Number, required: true },
    chargedToClient: Number,
    creditedToAstrologer: Number,
    adminAmount: Number,
    reason: { type: String },
    createdAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model('BillingLedger', BillingLedgerSchema);
