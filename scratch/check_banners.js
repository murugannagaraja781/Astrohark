const mongoose = require('mongoose');

async function diagnostic() {
    const MONGO_URI = 'mongodb+srv://astroharksolution_db_user:iS0RRTi1B1L905b3@cluster0.84baoye.mongodb.net/?appName=Cluster0';
    await mongoose.connect(MONGO_URI);
    
    const db = mongoose.connection.db;
    console.log("Connected to DB:", db.databaseName);
    
    console.log("Listing all collections:");
    const collections = await db.listCollections().toArray();
    console.log(collections.map(c => c.name));
    
    // Check user count to see if we are in the correct database
    try {
        const User = mongoose.model('User', new mongoose.Schema({}, { strict: false }));
        const userCount = await User.countDocuments();
        console.log("Total users in database:", userCount);
    } catch (e) {
        console.error("User count error:", e.message);
    }
    
    // Check banner count
    try {
        const Banner = mongoose.model('Banner', new mongoose.Schema({}, { strict: false }));
        const bannerCount = await Banner.countDocuments();
        console.log("Total banners in database:", bannerCount);
        
        const allBanners = await Banner.find().lean();
        console.log("All banners raw:");
        console.log(allBanners);
    } catch (e) {
        console.error("Banner fetch error:", e.message);
    }

    await mongoose.connection.close();
}

diagnostic().catch(console.error);
