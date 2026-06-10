// services/email.service.js
const nodemailer = require('nodemailer');

const getTransporter = () => {
    return nodemailer.createTransport({
        host: process.env.SMTP_HOST || 'smtp.gmail.com',
        port: parseInt(process.env.SMTP_PORT) || 587,
        secure: process.env.SMTP_SECURE === 'true' || process.env.SMTP_SECURE === true, // true for 465, false for other ports
        auth: {
            user: process.env.SMTP_USER || 'info@astrohark.com',
            pass: process.env.SMTP_PASS || 'your_email_password'
        }
    });
};

exports.sendFeedbackEmail = async (feedbackData) => {
    let destinationEmail = process.env.EMAIL_TO || 'info@astrohark.com';
    try {
        const User = require('../models/User');
        const superadmin = await User.findOne({ role: 'superadmin' });
        if (superadmin && superadmin.email) {
            destinationEmail = superadmin.email;
        }
    } catch (err) {
        console.error('Failed to retrieve superadmin email from database, falling back:', err);
    }

    const mailOptions = {
        from: process.env.EMAIL_FROM || '"Astrohark Feedback" <info@astrohark.com>',
        to: destinationEmail,
        subject: `New Astrohark Feedback from ${feedbackData.userName}`,
        text: `
You have received a new feedback comment on Astrohark.

User ID: ${feedbackData.userId}
User Name: ${feedbackData.userName}
Rating: ${feedbackData.rating} / 5
Session Type: ${feedbackData.sessionType || 'N/A'}
Astrologer: ${feedbackData.astrologerName ? `${feedbackData.astrologerName} (${feedbackData.astrologerId})` : 'N/A'}

Comment:
${feedbackData.comment}

Submitted At: ${new Date()}
        `,
        html: `
            <h3>New Astrohark Feedback</h3>
            <p><strong>User ID:</strong> ${feedbackData.userId}</p>
            <p><strong>User Name:</strong> ${feedbackData.userName}</p>
            <p><strong>Rating:</strong> ${feedbackData.rating} / 5</p>
            <p><strong>Session Type:</strong> ${feedbackData.sessionType || 'N/A'}</p>
            <p><strong>Astrologer:</strong> ${feedbackData.astrologerName ? `${feedbackData.astrologerName} (${feedbackData.astrologerId})` : 'N/A'}</p>
            <hr/>
            <p><strong>Comment:</strong></p>
            <blockquote style="background: #f9f9f9; padding: 10px; border-left: 3px solid #ccc;">
                ${feedbackData.comment}
            </blockquote>
        `
    };

    try {
        const transporter = getTransporter();
        const info = await transporter.sendMail(mailOptions);
        console.log('Feedback email sent successfully:', info.messageId);
        return { success: true, messageId: info.messageId };
    } catch (error) {
        console.error('Error sending feedback email:', error);
        return { success: false, error: error.message };
    }
};
