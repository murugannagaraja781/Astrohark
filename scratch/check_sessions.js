const mongoose = require('mongoose');
require('dotenv').config();
const Session = require('../models/Session');

async function check() {
  await mongoose.connect(process.env.MONGODB_URI);
  console.log('Connected to MongoDB');
  const sessions = await Session.find().sort({ startTime: -1 }).limit(10).lean();
  console.log(`Found ${sessions.length} recent sessions:`);
  sessions.forEach(s => {
    console.log(JSON.stringify(s, null, 2));
  });
  await mongoose.disconnect();
}
check().catch(console.error);
