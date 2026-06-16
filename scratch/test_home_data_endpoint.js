const mongoose = require('mongoose');
const Banner = require('../models/Banner');

async function testQuery() {
    const MONGO_URI = 'mongodb+srv://astroharksolution_db_user:iS0RRTi1B1L905b3@cluster0.84baoye.mongodb.net/?appName=Cluster0';
    await mongoose.connect(MONGO_URI);

    try {
        const bannersRaw = await Banner.find({
            isActive: true,
            $or: [{ expiryDate: { $gt: new Date() } }, { expiryDate: null }]
        }).sort({ order: 1 });

        console.log("Matching active banners count:", bannersRaw.length);
        console.log("Active banners:", JSON.stringify(bannersRaw, null, 2));
    } catch (e) {
        console.error("Error running query:", e);
    } finally {
        await mongoose.connection.close();
    }
}

testQuery().catch(console.error);
