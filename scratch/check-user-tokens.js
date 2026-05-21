require('dotenv').config();
const mongoose = require('mongoose');

async function run() {
  try {
    console.log('Connecting to database...');
    await mongoose.connect(process.env.MONGODB_URI);
    console.log('Connected.');

    const db = mongoose.connection.db;
    const usersCollection = db.collection('users');

    const userIds = [
      'ASTRO_1777630111531512',
      '16db3ff3-e9b8-4148-b299-93b3bf7325b8',
      'c2e2530e-0531-4511-944b-a5c5f3506f4e'
    ];

    for (const userId of userIds) {
      const user = await usersCollection.findOne({ userId });
      if (user) {
        console.log(`\n--- User found: ${userId} ---`);
        console.log(`Name: ${user.name}`);
        console.log(`Role: ${user.role}`);
        console.log(`fcmToken: ${user.fcmToken ? (user.fcmToken.substring(0, 30) + '...') : 'None/Empty'}`);
        console.log(`fcmToken Length: ${user.fcmToken ? user.fcmToken.length : 0}`);
        console.log(`isOnline: ${user.isOnline}`);
      } else {
        console.log(`\n--- User NOT found: ${userId} ---`);
      }
    }

  } catch (error) {
    console.error('Error running check script:', error);
  } finally {
    await mongoose.disconnect();
    console.log('\nDisconnected.');
    process.exit(0);
  }
}

run();
