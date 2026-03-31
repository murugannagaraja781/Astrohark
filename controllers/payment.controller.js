const crypto = require('crypto');
const fetch = (...args) => import('node-fetch').then(({ default: fetch }) => fetch(...args));
const User = require('../models/User');
const Payment = require('../models/Payment');
const { paymentTokens } = require('../services/socketStore');
const phonepeConfig = require('../config/phonepe');
const { formatImageUrl } = require('../utils/formatImage');

exports.createToken = async (req, res) => {
    try {
        const { userId, amount, couponCode } = req.body;
        if (!userId || !amount) return res.json({ ok: false, error: 'Missing userId or amount' });
        if (amount < 1) return res.json({ ok: false, error: 'Minimum amount is ₹1' });

        const user = await User.findOne({ userId });
        if (!user) return res.json({ ok: false, error: 'User not found' });

        const baseAmount = parseFloat(amount);
        const gstAmount = baseAmount * 0.18;
        const totalAmount = baseAmount + gstAmount;
        const token = crypto.randomBytes(32).toString('hex');

        paymentTokens.set(token, {
            userId, baseAmount, gstAmount, amount: totalAmount,
            couponCode: couponCode || "", createdAt: Date.now(),
            used: false, userName: user.name, userPhone: user.phone
        });

        console.log(`Payment Token Created: ${token.substring(0, 8)}... for ${user.name} amount ₹${amount}`);
        res.json({ ok: true, token });
    } catch (e) {
        console.error(e);
        res.json({ ok: false, error: 'Failed' });
    }
};

exports.verifyToken = async (req, res) => {
    const { token } = req.query;
    if (!token) return res.json({ valid: false, error: 'Token required' });

    const tokenData = paymentTokens.get(token);
    if (!tokenData) return res.json({ valid: false, error: 'Invalid token' });

    const expiryTime = 10 * 60 * 1000;
    if (Date.now() - tokenData.createdAt > expiryTime) {
        paymentTokens.delete(token);
        return res.json({ valid: false, error: 'Token expired' });
    }

    if (tokenData.used) return res.json({ valid: false, error: 'Token already used' });

    res.json({
        valid: true, amount: tokenData.amount, baseAmount: tokenData.baseAmount,
        gstAmount: tokenData.gstAmount, userName: tokenData.userName,
        expiresIn: Math.floor((expiryTime - (Date.now() - tokenData.createdAt)) / 1000)
    });
};

exports.validateCoupon = async (req, res) => {
    const { couponCode, amount } = req.body;
    if (!couponCode || !amount) return res.json({ ok: false, error: 'Missing code or amount' });

    const code = couponCode.toUpperCase().trim();
    const baseAmount = parseFloat(amount);

    if (code === 'WELCOME50') {
        return res.json({
            ok: true, bonus: baseAmount * 0.50,
            message: 'WELCOME50 Applied! 50% Bonus added to Super Wallet.'
        });
    }
    return res.json({ ok: false, error: 'Invalid coupon code' });
};

exports.createPayment = async (req, res) => {
    try {
        let { userId, amount, isApp, token, isSuperWallet, offerPercentage, couponCode } = req.body;
        let baseAmount = 0, gstAmount = 0, couponBonus = 0;

        if (token) {
            const tokenData = paymentTokens.get(token);
            if (!tokenData || (Date.now() - tokenData.createdAt > 600000) || tokenData.used) {
                return res.json({ ok: false, error: 'Invalid token' });
            }
            tokenData.used = true;
            userId = tokenData.userId;
            amount = tokenData.amount;
            baseAmount = tokenData.baseAmount || amount;
            gstAmount = tokenData.gstAmount || 0;
        } else {
            baseAmount = parseFloat(amount);
            gstAmount = baseAmount * 0.18;
            amount = baseAmount + gstAmount;
        }

        if (!amount || !userId) return res.json({ ok: false, error: 'Missing data' });

        const userObj = await User.findOne({ userId });
        const userMobile = ((userObj && userObj.phone) ? userObj.phone : "9999999999").replace(/[^0-9]/g, '').slice(-10);
        const merchantTransactionId = "MT" + Date.now() + Math.round(Math.random() * 1000);

        if (couponCode === 'WELCOME50') couponBonus = baseAmount * 0.50;

        await Payment.create({
            transactionId: merchantTransactionId, merchantTransactionId,
            userId, amount, baseAmount, gstAmount, status: 'pending',
            withGst: true, isApp: !!isApp, isSuperWallet: !!isSuperWallet || !!couponBonus,
            offerPercentage: parseFloat(offerPercentage || 0),
            couponCode: couponCode || null, couponBonus
        });

        const cleanUserId = userId.replace(/[^a-zA-Z0-9]/g, '') || "User";
        const BASE_URL = process.env.BASE_URL || 'https://astrohark.com';

        const payload = {
            merchantId: phonepeConfig.MERCHANT_ID,
            merchantTransactionId,
            merchantUserId: cleanUserId,
            amount: Math.round(amount * 100),
            redirectUrl: `${BASE_URL}/api/payment/callback`,
            redirectMode: isApp ? "GET" : "POST",
            callbackUrl: `${BASE_URL}/api/payment/callback${isApp ? '?isApp=true&txnId=' + merchantTransactionId : ''}`,
            mobileNumber: userMobile,
            paymentInstrument: { type: "PAY_PAGE" }
        };

        if (isApp) payload.redirectUrl = "astrohark://payment-success";

        const base64Payload = Buffer.from(JSON.stringify(payload)).toString('base64');
        const stringToSign = base64Payload + "/pg/v1/pay" + phonepeConfig.SALT_KEY;
        const sha256 = crypto.createHash('sha256').update(stringToSign).digest('hex');
        const checksum = sha256 + "###" + phonepeConfig.SALT_INDEX;

        const response = await fetch(`${phonepeConfig.HOST_URL}/pg/v1/pay`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-VERIFY': checksum,
                'accept': 'application/json'
            },
            body: JSON.stringify({ request: base64Payload })
        }).then(r => r.json());

        if (response.success) {
            res.json({
                ok: true, merchantTransactionId,
                paymentUrl: response.data.instrumentResponse?.redirectInfo?.url,
                intentUrl: response.data.instrumentResponse?.intentUrl
            });
        } else {
            res.json({ ok: false, error: response.message || 'PhonePe error' });
        }
    } catch (e) {
        console.error(e);
        res.json({ ok: false, error: 'Internal error' });
    }
};

exports.callback = async (req, res) => {
    try {
        let decoded = {};
        if (req.body.response) {
            decoded = JSON.parse(Buffer.from(req.body.response, 'base64').toString('utf-8'));
        } else {
            decoded = req.body.code ? req.body : req.query;
        }

        const merchantTransactionId = decoded.data?.merchantTransactionId || decoded.merchantTransactionId || req.query.txnId;
        const code = decoded.code;

        const payment = await Payment.findOne({ merchantTransactionId });
        if (!payment) return res.redirect('/wallet?status=failure&reason=not_found');

        const isSuccess = code === 'PAYMENT_SUCCESS' || code === 'SUCCESS';
        const redirectIsApp = payment.isApp || req.query.isApp === 'true';

        if (isSuccess && payment.status !== 'success') {
            payment.status = 'success';
            payment.providerRefId = decoded.data?.providerReferenceId || decoded.providerReferenceId;
            await payment.save();

            const user = await User.findOne({ userId: payment.userId });
            if (user) {
                user.walletBalance = (user.walletBalance || 0) + payment.baseAmount;
                if (payment.couponBonus > 0) {
                    user.superWalletBalance = (user.superWalletBalance || 0) + payment.couponBonus;
                }
                await user.save();
            }
        } else if (!isSuccess) {
            payment.status = 'failed';
            await payment.save();
        }

        if (redirectIsApp) {
            const scheme = isSuccess ? 'astrohark://payment-success' : 'astrohark://payment-failed';
            return res.send(`<html><script>window.location.href="${scheme}?status=${status}";</script></html>`);
        }

        res.redirect(`/wallet?status=${isSuccess ? 'success' : 'failure'}`);
    } catch (e) {
        console.error(e);
        res.redirect('/wallet?status=failure');
    }
};

exports.getHistory = async (req, res) => {
    try {
        const { userId } = req.params;
        const payments = await Payment.find({ userId }).sort({ createdAt: -1 }).limit(50);
        res.json({ ok: true, payments });
    } catch (e) {
        res.json({ ok: false });
    }
};
