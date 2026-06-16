const mongoose = require('mongoose');
const Banner = require('../models/Banner');

async function addBanner() {
    const MONGO_URI = 'mongodb+srv://astroharksolution_db_user:iS0RRTi1B1L905b3@cluster0.84baoye.mongodb.net/?appName=Cluster0';
    await mongoose.connect(MONGO_URI);
    
    // Check if there are any banners
    const count = await Banner.countDocuments();
    console.log("Current banners count:", count);
    
    // Let's create a test banner
    const newBanner = await Banner.create({
        imageUrl: "https://images.unsplash.com/photo-1532983330958-4b32bb9bb078?q=80&w=1200",
        title: "Test Live Banner",
        subtitle: "Created via diagnostic script",
        ctaText: "Check Now",
        order: 1,
        isActive: true,
        offerPercentage: 10.0,
        expiryDate: null
    });
    
    console.log("Created banner:", newBanner);
    
    const countAfter = await Banner.countDocuments();
    console.log("New banners count:", countAfter);

    await mongoose.connection.close();
}

addBanner().catch(console.error);
