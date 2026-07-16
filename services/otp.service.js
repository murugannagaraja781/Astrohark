const https = require('https');

/**
 * Sends OTP using PING4SMS API.
 * @param {string} phoneNumber Recipient's phone number.
 * @param {string} otp The OTP code.
 */
function sendSMS(phoneNumber, otp) {
    const apiKey = process.env.PING4SMS_API_KEY || '22fe6258e5c2a5caa90b7bea6a55798c';
    const sender = process.env.PING4SMS_SENDER_ID || 'ASTRO7';
    const route = process.env.PING4SMS_ROUTE || '2';
    const templateId = process.env.PING4SMS_TEMPLATE_ID || '1407178289966726304';

    // Format phone number. Clean all non-digits.
    const cleanPhone = phoneNumber.replace(/\D/g, '');
    
    // Ensure 91 prefix is present for Indian mobile numbers
    const mobile = (cleanPhone.length === 10) ? `91${cleanPhone}` : cleanPhone;
    
    // Construct the message matching the registered DLT template exactly.
    const message = `${otp} is your OTP for verification on Astrohark app. Valid for 10 mins. Please do not share this OTP with anyone`;
    
    const params = new URLSearchParams({
        key: apiKey,
        route: route,
        sender: sender,
        number: mobile,
        sms: message,
        templateid: templateId
    });

    const url = `https://site.ping4sms.com/api/smsapi?${params.toString()}`;

    https.get(url, (res) => {
        let data = '';
        res.on('data', (chunk) => data += chunk);
        res.on('end', () => console.log('PING4SMS Result:', data));
    }).on('error', (e) => console.error('PING4SMS Error:', e));
}

module.exports = {
    sendSMS,
    sendMsg91: sendSMS // Keep for compatibility
};
