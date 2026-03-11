const mongoose = require('mongoose');

const ChatMessageSchema = new mongoose.Schema({
    messageId: { type: String, unique: true },
    sessionId: String,
    fromUserId: String,
    toUserId: String,
    text: String,
    type: { type: String, default: 'text' },
    timestamp: { type: Number, default: Date.now },
    createdAt: { type: Date, default: Date.now }
});

ChatMessageSchema.index({ sessionId: 1 });
ChatMessageSchema.index({ fromUserId: 1 });
ChatMessageSchema.index({ toUserId: 1 });

module.exports = mongoose.model('ChatMessage', ChatMessageSchema);
