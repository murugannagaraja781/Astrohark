const crypto = require('crypto');
const mongoose = require('mongoose');

async function generateUniqueReferralCode(name) {
    let base = (name || 'ASTRO').replace(/[^A-Za-z0-9]/g, '').substring(0, 4).toUpperCase();
    if (!base || base.length < 3) base = 'ASTRO';

    const User = mongoose.models.User || require('../models/User');

    for (let attempts = 0; attempts < 15; attempts++) {
        const randomNum = Math.floor(1000 + Math.random() * 9000);
        const code = `${base}${randomNum}`;
        const existing = await User.findOne({ referralCode: code }).select('_id').lean();
        if (!existing) {
            return code;
        }
    }

    // Fallback with 6 random hex characters if numeric collisions persist
    for (let attempts = 0; attempts < 10; attempts++) {
        const code = `${base}${crypto.randomBytes(3).toString('hex').toUpperCase()}`;
        const existing = await User.findOne({ referralCode: code }).select('_id').lean();
        if (!existing) {
            return code;
        }
    }

    return `REF${Date.now().toString(36).toUpperCase()}`;
}

module.exports = { generateUniqueReferralCode };

