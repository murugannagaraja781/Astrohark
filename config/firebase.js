const admin = require('firebase-admin');
const path = require('path');
const fs = require('fs');
const { GoogleAuth } = require('google-auth-library');

const FCM_PROJECT_ID = 'astrohark-d487c';
let callApp = null;
let fcmAuth = null;

const serviceAccountPath = path.join(__dirname, '../firebase-service-account.json');

// Initialize Firebase Admin SDK
try {
    if (fs.existsSync(serviceAccountPath)) {
        const firebaseServiceAccount = require(serviceAccountPath);
        callApp = admin.initializeApp({
            credential: admin.credential.cert(firebaseServiceAccount)
        }, 'callApp');
        console.log('✓ Call App: Firebase Admin SDK initialized');

        // Initialize FCM v1 Auth
        fcmAuth = new GoogleAuth({
            keyFile: serviceAccountPath,
            scopes: ['https://www.googleapis.com/auth/firebase.messaging']
        });
        console.log('[FCM v1] Initialized with service account');
    } else {
        console.warn('✗ Firebase Service account file not found');
    }
} catch (error) {
    console.error('✗ Firebase Initialization Error:', error.message);
}

module.exports = { admin, callApp, fcmAuth, FCM_PROJECT_ID };
