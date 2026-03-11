const fetch = (...args) => import('node-fetch').then(({ default: fetch }) => fetch(...args));
const { fcmAuth, FCM_PROJECT_ID } = require('../config/firebase');

async function sendFcmV1Push(fcmToken, data, notification) {
    if (!fcmAuth) {
        console.warn('[FCM v1] Not initialized - skipping push');
        return { success: false, error: 'FCM not initialized' };
    }

    try {
        const accessToken = await fcmAuth.getAccessToken();

        const messagePayload = {
            token: fcmToken,
            data: {
                ...data,
                title: notification ? notification.title : '',
                body: notification ? notification.body : ''
            },
            android: {
                priority: 'high',
                ttl: '0s'
            }
        };

        const message = { message: messagePayload };

        const response = await fetch(
            `https://fcm.googleapis.com/v1/projects/${FCM_PROJECT_ID}/messages:send`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${accessToken.token || accessToken}`
                },
                body: JSON.stringify(message)
            }
        );

        const result = await response.json();

        if (response.ok) {
            console.log('[FCM v1] Push sent successfully:', result.name);
            return { success: true, messageId: result.name };
        } else {
            console.error('[FCM v1] Push failed:', result.error?.message || JSON.stringify(result));
            return { success: false, error: result.error?.message };
        }
    } catch (err) {
        console.error('[FCM v1] Send error:', err.message);
        return { success: false, error: err.message };
    }
}

module.exports = { sendFcmV1Push };
