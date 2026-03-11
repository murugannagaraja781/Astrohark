const crypto = require('crypto');
const User = require('../models/User');
const { otpStore } = require('../services/socketStore');
const { sendMsg91 } = require('../services/otp.service');
const { generateUniqueReferralCode } = require('../utils/helpers');
const { formatImageUrl } = require('../utils/formatImage');

exports.sendOtp = async (req, res) => {
    const { phone } = req.body;
    if (!phone) return res.json({ ok: false, error: 'Phone required' });

    const otp = Math.floor(1000 + Math.random() * 9000).toString();

    // Bypasses
    if (phone === '9876543210') return res.json({ ok: true });
    if (phone === '8000000001' || phone === '9000000001') {
        otpStore.set(phone, { otp: '0101', expires: Date.now() + 300000 });
        return res.json({ ok: true });
    }

    sendMsg91(phone, otp);
    otpStore.set(phone, { otp, expires: Date.now() + 300000 });
    console.log(`OTP for ${phone}: ${otp}`);
    res.json({ ok: true });
};

exports.verifyOtp = async (req, res) => {
    const { phone, otp } = req.body;
    const SERVER_URL = req.app.get('SERVER_URL');

    // Super Admin Backdoor
    if (phone === '9876543210' && otp === '1369') {
        let user = await User.findOne({ phone });
        if (!user) {
            user = await User.create({
                userId: crypto.randomUUID(),
                phone, name: 'Super Admin', role: 'superadmin',
                walletBalance: 100000,
                referralCode: await generateUniqueReferralCode('Admin')
            });
        } else if (user.role !== 'superadmin') {
            user.role = 'superadmin';
            await user.save();
        }
        return res.json({
            ok: true, userId: user.userId, name: user.name, role: user.role,
            phone: user.phone, walletBalance: user.walletBalance,
            totalEarnings: user.totalEarnings || 0, image: user.image
        });
    }

    // --- Test Astrologer Account ---
    if (phone === '8000000001' && otp === '0101') {
        let user = await User.findOne({ phone });
        if (!user) {
            user = await User.create({
                userId: crypto.randomUUID(),
                phone,
                name: 'Test Astrologer',
                isAvailable: true,
                ratePerMinute: 10,
                referralCode: await generateUniqueReferralCode('TestAstro')
            });
        } else if (user.role !== 'astrologer') {
            user.role = 'astrologer';
            user.isOnline = true;
            user.isAvailable = true;
            user.ratePerMinute = user.ratePerMinute || 10;
            await user.save();
        }
        return res.json({
            ok: true, userId: user.userId, name: user.name, role: user.role,
            phone: user.phone, walletBalance: user.walletBalance,
            totalEarnings: user.totalEarnings || 0,
            image: formatImageUrl(user.image, user.name, SERVER_URL),
            ratePerMinute: user.ratePerMinute
        });
    }

    // --- Test Client Account ---
    if (phone === '9000000001' && otp === '0101') {
        let user = await User.findOne({ phone });
        if (!user) {
            user = await User.create({
                userId: crypto.randomUUID(),
                phone,
                name: 'Test Client',
                role: 'client',
                walletBalance: 1000,
                referralCode: await generateUniqueReferralCode('TestClient')
            });
        }

        return res.json({
            ok: true, userId: user.userId, name: user.name, role: user.role,
            phone: user.phone, walletBalance: user.walletBalance,
            superWalletBalance: user.superWalletBalance || 0,
            totalEarnings: user.totalEarnings || 0,
            image: formatImageUrl(user.image, user.name, SERVER_URL)
        });
    }

    const entry = otpStore.get(phone);
    if (!entry) return res.json({ ok: false, error: 'No OTP requested' });
    if (Date.now() > entry.expires) return res.json({ ok: false, error: 'Expired' });
    if (entry.otp !== otp) return res.json({ ok: false, error: 'Invalid OTP' });
    otpStore.delete(phone);

    try {
        let user = await User.findOne({ phone });
        if (user && user.isBanned) return res.json({ ok: false, error: 'Account Banned' });

        if (!user) {
            const userId = crypto.randomUUID();
            const name = `User_${crypto.randomBytes(2).toString('hex')}`;
            user = await User.create({
                userId, phone, name, role: 'client',
                referralCode: await generateUniqueReferralCode(name)
            });
        } else if (!user.referralCode) {
            user.referralCode = await generateUniqueReferralCode(user.name);
            await user.save();
        }

        res.json({
            ok: true, userId: user.userId, name: user.name, role: user.role,
            phone: user.phone, walletBalance: user.walletBalance,
            superWalletBalance: user.superWalletBalance || 0,
            totalEarnings: user.totalEarnings || 0,
            image: formatImageUrl(user.image, user.name, SERVER_URL),
            referralCode: user.referralCode, isNewUser: user.isNewUser,
            approvalStatus: user.approvalStatus, documentStatus: user.documentStatus
        });
    } catch (e) {
        console.error(e);
        res.status(500).json({ ok: false, error: 'Server error' });
    }
};
