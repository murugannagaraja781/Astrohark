const crypto = require('crypto');
const User = require('../models/User');
const { otpStore } = require('../services/socketStore');
const { sendMsg91 } = require('../services/otp.service');
const { generateUniqueReferralCode } = require('../utils/helpers');
const { formatImageUrl } = require('../utils/formatImage');

const normalizePhone = (p) => {
    if (!p) return p;
    const clean = p.replace(/\D/g, '');
    return clean.length === 10 ? '91' + clean : clean;
};

exports.sendOtp = async (req, res) => {
    let { phone } = req.body;
    if (!phone) return res.json({ ok: false, error: 'Phone required' });
    phone = normalizePhone(phone);

    const otp = Math.floor(1000 + Math.random() * 9000).toString();

    // Bypasses
    const cleanPhone = phone.replace(/[^0-9]/g, '').slice(-10);
    if (cleanPhone === '9876543210') return res.json({ ok: true });
    if (cleanPhone === '8000000001' || cleanPhone === '9000000001') {
        otpStore.set(phone, { otp: '0101', expires: Date.now() + 300000 });
        return res.json({ ok: true });
    }

    sendMsg91(phone, otp);
    otpStore.set(phone, { otp, expires: Date.now() + 300000 });
    console.log(`OTP for ${phone}: ${otp}`);
    res.json({ ok: true });
};

exports.verifyOtp = async (req, res) => {
    let { phone, otp } = req.body;
    if (!phone) return res.json({ ok: false, error: 'Phone required' });
    phone = normalizePhone(phone);
    const SERVER_URL = req.app.get('SERVER_URL');

    // Super Admin Backdoor
    const cleanPhoneVerify = phone.replace(/[^0-9]/g, '').slice(-10);
    if (cleanPhoneVerify === '9876543210' && otp === '1369') {
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
    if (cleanPhoneVerify === '8000000001' && otp === '0101') {
        let user = await User.findOne({ phone });
        if (!user) {
            user = await User.create({
                userId: crypto.randomUUID(),
                phone,
                name: 'Test Astrologer',
                role: 'astrologer',
                isOnline: true,
                isAvailable: true,
                ratePerMinute: 10,
                skills: ['Vedic', 'Prashana'],
                experience: 5,
                approvalStatus: 'approved',
                referralCode: await generateUniqueReferralCode('TestAstro')
            });
        } else if (user.role !== 'astrologer') {
            user.role = 'astrologer';
            user.isOnline = true;
            user.isAvailable = true;
            user.approvalStatus = 'approved';
            user.skills = user.skills.length > 0 ? user.skills : ['Vedic', 'Prashana'];
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
    if (cleanPhoneVerify === '9000000001' && otp === '0101') {
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
            const { referralCode: codeApplied } = req.body;
            let referredBy = null;
            let initialBalance = 108; // Standard bonus

            if (codeApplied) {
                const referrer = await User.findOne({ referralCode: codeApplied.trim().toUpperCase() });
                if (referrer) {
                    referredBy = referrer.userId;
                    initialBalance = 188; // Referral bonus
                }
            }

            const userId = crypto.randomUUID();
            const name = `User_${crypto.randomBytes(2).toString('hex')}`;
            user = await User.create({
                userId, phone, name, role: 'client',
                walletBalance: initialBalance,
                referredBy: referredBy,
                referralCode: await generateUniqueReferralCode(name),
                isNewUser: true
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
            referredBy: user.referredBy,
            approvalStatus: user.approvalStatus, documentStatus: user.documentStatus
        });
    } catch (e) {
        console.error(e);
        res.status(500).json({ ok: false, error: 'Server error' });
    }
};
