const mongoose = require('mongoose');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../.env') });
const User = require('../models/User');

async function listAstrologers() {
    try {
        await mongoose.connect(process.env.MONGODB_URI);
        const users = await User.find({ role: 'astrologer' }).select('name phone approvalStatus isOnline isAvailable');
        console.log(JSON.stringify(users, null, 2));
        process.exit(0);
    } catch (err) {
        process.exit(1);
    }
}
listAstrologers();
