require('dotenv').config();
const mongoose = require('mongoose');
const fetch = (...args) => import('node-fetch').then(({ default: fetch }) => fetch(...args));
const { GoogleAuth } = require('google-auth-library');
const path = require('path');
const fs = require('fs');

const FCM_PROJECT_ID = 'astrohark-476dc';
const serviceAccountPath = path.join(__dirname, '../firebase-service-account.json');

async function testToken(fcmToken, fcmAuth) {
  try {
    const accessToken = await fcmAuth.getAccessToken();
    const messagePayload = {
      token: fcmToken,
      notification: {
        title: "Test Validation",
        body: "This is a dry-run test message"
      },
      android: {
        priority: 'high'
      }
    };

    const payload = {
      message: messagePayload
    };

    const response = await fetch(
      `https://fcm.googleapis.com/v1/projects/${FCM_PROJECT_ID}/messages:send`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${accessToken.token || accessToken}`
        },
        body: JSON.stringify(payload)
      }
    );

    const result = await response.json();
    return {
      status: response.status,
      ok: response.ok,
      result
    };
  } catch (error) {
    return {
      error: error.message
    };
  }
}

async function run() {
  try {
    console.log('Connecting to database...');
    await mongoose.connect(process.env.MONGODB_URI);
    console.log('Connected.');

    if (!fs.existsSync(serviceAccountPath)) {
      console.error('Service account not found!');
      process.exit(1);
    }

    const fcmAuth = new GoogleAuth({
      keyFile: serviceAccountPath,
      scopes: ['https://www.googleapis.com/auth/firebase.messaging']
    });

    const db = mongoose.connection.db;
    const usersCollection = db.collection('users');

    const userIds = [
      'ASTRO_1777630111531512',
      '16db3ff3-e9b8-4148-b299-93b3bf7325b8',
      'c2e2530e-0531-4511-944b-a5c5f3506f4e'
    ];

    for (const userId of userIds) {
      const user = await usersCollection.findOne({ userId });
      if (user && user.fcmToken) {
        console.log(`\nTesting token for user: ${userId} (${user.name})`);
        console.log(`Token: ${user.fcmToken}`);
        const res = await testToken(user.fcmToken, fcmAuth);
        console.log(`HTTP Status: ${res.status}`);
        console.log(`OK: ${res.ok}`);
        console.log(`Response:`, JSON.stringify(res.result, null, 2));
      } else {
        console.log(`\nUser ${userId} not found or has no FCM token.`);
      }
    }

  } catch (err) {
    console.error(err);
  } finally {
    await mongoose.disconnect();
    console.log('\nDisconnected.');
    process.exit(0);
  }
}

run();
