const mongoose = require('mongoose');

const AccountDeletionRequestSchema = new mongoose.Schema({
    requestId: { type: String, unique: true },
    userIdentifier: { type: String, required: true },
    userId: String,
    reason: String,
    status: { type: String, default: 'pending' },
    requestedAt: { type: Date, default: Date.now },
    processedAt: Date,
    processedBy: String,
    notes: String
});

module.exports = mongoose.model('AccountDeletionRequest', AccountDeletionRequestSchema);
