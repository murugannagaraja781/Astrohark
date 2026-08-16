const crypto = require('crypto');
const mongoose = require('mongoose');

async function generateUniqueReferralCode(name) {
    let base = (name || 'ASTRO').replace(/[^A-Za-z0-9]/g, '').substring(0, 4).toUpperCase();
    if (!base || base.length < 3) base = 'ASTRO';

    const User = mongoose.models.User || require('../models/User');

    for (let attempts = 0; attempts < 15; attempts++) {
        const randomStr = crypto.randomBytes(3).toString('hex').toUpperCase(); // 6 hex chars = 16.7M combinations
        const code = `${base}${randomStr}`;
        const existing = await User.findOne({ referralCode: code }).select('_id').lean();
        if (!existing) {
            return code;
        }
    }

    return `REF${Date.now().toString(36).toUpperCase()}${crypto.randomBytes(2).toString('hex').toUpperCase()}`;
}

module.exports = { generateUniqueReferralCode };

