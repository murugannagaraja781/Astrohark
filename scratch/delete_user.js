const mongoose = require('mongoose');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../.env') });
const User = require('../models/User');

async function deleteUser() {
    try {
        await mongoose.connect(process.env.MONGODB_URI);
        const phone = '9597907410';
        const prefixedPhone = '91' + phone;

        const result = await User.deleteMany({ phone: { $in: [phone, prefixedPhone] } });
        console.log(`Deleted ${result.deletedCount} user(s) with phone ${phone} or ${prefixedPhone}`);
        
        process.exit(0);
    } catch (err) {
        console.error('Delete failed:', err);
        process.exit(1);
    }
}
deleteUser();
