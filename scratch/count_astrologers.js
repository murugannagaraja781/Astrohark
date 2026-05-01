const mongoose = require('mongoose');
require('dotenv').config();
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../.env') });
const User = require('../models/User');

async function countAstrologers() {
    try {
        const uri = process.env.MONGODB_URI || 'mongodb://localhost:27017/astrohark';
        await mongoose.connect(uri);
        
        const total = await User.countDocuments({ role: 'astrologer' });
        const approved = await User.countDocuments({ role: 'astrologer', approvalStatus: 'approved' });
        const pending = await User.countDocuments({ role: 'astrologer', approvalStatus: 'pending' });
        const online = await User.countDocuments({ role: 'astrologer', isOnline: true });
        const available = await User.countDocuments({ role: 'astrologer', isAvailable: true });

        console.log('--- Astrologer Statistics ---');
        console.log(`Total Registered: ${total}`);
        console.log(`Approved: ${approved}`);
        console.log(`Pending Approval: ${pending}`);
        console.log(`Currently Online: ${online}`);
        console.log(`Currently Available: ${available}`);
        console.log('----------------------------');

        process.exit(0);
    } catch (err) {
        console.error('Failed to count:', err);
        process.exit(1);
    }
}

countAstrologers();
