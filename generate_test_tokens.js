require('dotenv').config();
const mongoose = require('mongoose');
const jwt = require('jsonwebtoken');

// Assuming crypto and generating a referral code like the rest of the code does.
const crypto = require('crypto');

async function ensureTestUsers() {
    await mongoose.connect(process.env.MONGODB_URI);
    const db = mongoose.connection.db;
    const users = db.collection('users');

    let astro = await users.findOne({ phone: '8000000001' });
    if (!astro) {
        let base = 'TestAstro';
        let referralCode = base + Math.floor(1000 + Math.random() * 9000);
        astro = {
            userId: crypto.randomUUID(),
            phone: '8000000001',
            name: 'Test Astrologer',
            role: 'astrologer',
            isAvailable: true,
            isOnline: true,
            ratePerMinute: 10,
            referralCode: referralCode,
            approvalStatus: 'approved'
        };
        await users.insertOne(astro);
        console.log("Created 8000000001 Astrologer");
    } else if (astro.role !== 'astrologer' || astro.approvalStatus !== 'approved') {
        await users.updateOne({ phone: '8000000001' }, { $set: { role: 'astrologer', approvalStatus: 'approved', isOnline: true, isAvailable: true } });
        console.log("Updated 8000000001 Astrologer");
    }

    let client = await users.findOne({ phone: '9000000001' });
    if (!client) {
         let base = 'TestClient';
         let referralCode = base + Math.floor(1000 + Math.random() * 9000);
         client = {
             userId: crypto.randomUUID(),
             phone: '9000000001',
             name: 'Test Client',
             role: 'client',
             walletBalance: 1000,
             referralCode: referralCode
         };
         await users.insertOne(client);
         console.log("Created 9000000001 Client");
    }

    process.exit(0);
}

ensureTestUsers();
