const mongoose = require('mongoose');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../.env') });
const User = require('../models/User');

async function findDuplicates() {
    try {
        await mongoose.connect(process.env.MONGODB_URI);
        const users = await User.find({});
        const map = new Map();

        users.forEach(u => {
            const last10 = u.phone.slice(-10);
            if (!map.has(last10)) map.set(last10, []);
            map.get(last10).push({ id: u.userId, phone: u.phone, name: u.name, role: u.role });
        });

        console.log('--- Duplicate Phone Numbers (Last 10 Digits) ---');
        for (const [num, list] of map.entries()) {
            if (list.length > 1) {
                console.log(`Phone: ...${num}`);
                list.forEach(u => console.log(`  - ${u.phone} | ${u.name} | Role: ${u.role} | ID: ${u.id}`));
            }
        }
        console.log('-----------------------------------------------');
        process.exit(0);
    } catch (err) {
        process.exit(1);
    }
}
findDuplicates();
