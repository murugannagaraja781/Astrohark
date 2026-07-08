const mongoose = require('mongoose');

const feedbackSchema = new mongoose.Schema({
  userId: { type: String, required: true },
  userName: { type: String, required: true },
  astrologerId: { type: String },
  astrologerName: { type: String },
  rating: { type: Number, default: 5 },
  comment: { type: String, required: true },
  sessionType: { type: String }, // chat, call, video
  astrologerReply: { type: String, default: null },
  repliedAt: { type: Date, default: null },
  createdAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model('Feedback', feedbackSchema);
