const mongoose = require('mongoose');
const Ritual = require('./models/Ritual');
require('dotenv').config();

async function checkRituals() {
    try {
        await mongoose.connect(process.env.MONGODB_URI);
        const count = await Ritual.countDocuments({});
        const activeCount = await Ritual.countDocuments({ isActive: true });
        const rituals = await Ritual.find({});
        console.log(`Total Rituals: ${count}`);
        console.log(`Active Rituals: ${activeCount}`);
        console.log('Ritual details:', JSON.stringify(rituals, null, 2));
        process.exit(0);
    } catch (e) {
        console.error(e);
        process.exit(1);
    }
}

checkRituals();
