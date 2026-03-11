const mongoose = require('mongoose');

const BannerSchema = new mongoose.Schema({
    imageUrl: { type: String, required: true },
    title: String,
    subtitle: String,
    ctaText: { type: String, default: 'Learn More' },
    order: { type: Number, default: 0 },
    isActive: { type: Boolean, default: true },
    offerPercentage: { type: Number, default: 0 },
    expiryDate: { type: Date },
    createdAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model('Banner', BannerSchema);
