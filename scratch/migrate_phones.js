const mongoose = require('mongoose');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../.env') });
const User = require('../models/User');

async function migrate() {
    try {
        const uri = process.env.MONGODB_URI || 'mongodb://localhost:27017/astrohark';
        await mongoose.connect(uri);
        console.log('Connected to MongoDB');

        const users = await User.find({ phone: { $regex: /^\d{10}$/ } });
        console.log(`Found ${users.length} users with 10-digit phone numbers.`);

        for (const user of users) {
            const oldPhone = user.phone;
            const newPhone = '91' + oldPhone;
            
            // Check if newPhone already exists
            const existing = await User.findOne({ phone: newPhone });
            if (existing) {
                console.log(`Skipping migration for ${user.name} (${oldPhone}) as ${newPhone} already exists.`);
                continue;
            }

            user.phone = newPhone;
            await user.save();
            console.log(`Migrated ${user.name}: ${oldPhone} -> ${newPhone}`);
        }

        console.log('Migration complete.');
        process.exit(0);
    } catch (err) {
        console.error('Migration failed:', err);
        process.exit(1);
    }
}

migrate();
