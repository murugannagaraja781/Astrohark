const fetch = (...args) => import('node-fetch').then(({ default: fetch }) => fetch(...args));
const { fcmAuth, FCM_PROJECT_ID } = require('../config/firebase');
const User = require('../models/User');

// Cache to prevent log flooding for tokens already being cleaned
const cleaningTokens = new Set();

async function sendFcmV1Push(fcmToken, data, notification, userId = null) {
    if (!fcmToken) return { success: false, error: 'No token provided' };
    
    if (!fcmAuth) {
        console.warn('[FCM v1] Not initialized - skipping push');
        return { success: false, error: 'FCM not initialized' };
    }

    // Skip if we are already cleaning this user's token to avoid log spam
    if (userId && cleaningTokens.has(userId)) {
        return { success: false, error: 'CLEANING_IN_PROGRESS' };
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
            const errorMsg = result.error?.message || JSON.stringify(result);
            console.error('[FCM v1] Push failed:', errorMsg);
            
            // Check for invalid token errors
            const isInvalidToken = (
                response.status === 404 || 
                errorMsg.includes('Requested entity was not found') || 
                errorMsg.includes('UNREGISTERED') ||
                errorMsg.includes('INVALID_ARGUMENT')
            );

            if (isInvalidToken && userId) {
                if (!cleaningTokens.has(userId)) {
                    cleaningTokens.add(userId);
                    console.warn(`[FCM v1] Token for user ${userId} is invalid, cleaning up...`);
                    try {
                        await User.updateOne({ userId }, { $unset: { fcmToken: "" } });
                        // Remove from set after a short delay to allow DB propagation
                        setTimeout(() => cleaningTokens.delete(userId), 5000);
                    } catch (dbErr) {
                        console.error('[FCM v1] Failed to clear invalid token from DB:', dbErr.message);
                        cleaningTokens.delete(userId);
                    }
                }
                return { success: false, error: 'INVALID_TOKEN', originalError: errorMsg };
            }
            
            return { success: false, error: errorMsg };
        }
    } catch (err) {
        console.error('[FCM v1] Send error:', err.message);
        return { success: false, error: err.message };
    }
}

module.exports = { sendFcmV1Push };

