const crypto = require('crypto');
const Razorpay = require('razorpay');
const User = require('../models/User');
const Payment = require('../models/Payment');
const { paymentTokens } = require('../services/socketStore');
const razorpayConfig = require('../config/razorpay');

const razorpay = new Razorpay({
    key_id: razorpayConfig.KEY_ID,
    key_secret: razorpayConfig.KEY_SECRET,
});

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
            couponCode = tokenData.couponCode || couponCode;
        } else {
            baseAmount = parseFloat(amount);
            gstAmount = baseAmount * 0.18;
            amount = baseAmount + gstAmount;
        }

        if (!amount || !userId) return res.json({ ok: false, error: 'Missing data' });

        if (couponCode === 'WELCOME50') couponBonus = baseAmount * 0.50;

        let keyId = razorpayConfig.KEY_ID;
        let keySecret = razorpayConfig.KEY_SECRET;

        console.log(`[Razorpay Debug] Attempting order creation with KeyID: ${keyId?.substring(0, 10)}... (Secret length: ${keySecret?.length})`);

        if (!keyId || !keySecret) {
            console.error("Razorpay Error: KEY_ID or KEY_SECRET is missing from environment variables!");
            return res.json({ ok: false, error: 'Payment gateway configuration error' });
        }

        const order = await razorpay.orders.create({
            amount: Math.round(amount * 100), // Razorpay expects paisa
            currency: "INR",
            receipt: "rcpt_" + Date.now(),
        });

        await Payment.create({
            transactionId: order.id,
            merchantTransactionId: order.id,
            userId, amount, baseAmount, gstAmount, status: 'pending',
            withGst: true, isApp: !!isApp, isSuperWallet: !!isSuperWallet || !!couponBonus,
            offerPercentage: parseFloat(offerPercentage || 0),
            couponCode: couponCode || null, couponBonus
        });

        res.json({
            ok: true,
            orderId: order.id,
            amount: order.amount,
            key: keyId
        });
    } catch (e) {
        let keyId = razorpayConfig.KEY_ID;
        console.error("Razorpay Order Error Details:", {
            errorDescription: e.error ? e.error.description : (e.description || 'Unknown'),
            errorCode: e.error ? e.error.code : (e.code || 'Unknown'),
            statusCode: e.statusCode,
            usingKey: keyId ? keyId.substring(0, 10) + "..." : 'None'
        });
        res.json({ ok: false, error: 'Could not create payment order. ' + (e.error ? e.error.description : 'Please try again.') });
    }
};

exports.callback = async (req, res) => {
    try {
        const { razorpay_order_id, razorpay_payment_id, razorpay_signature } = req.body;

        const hmac = crypto.createHmac('sha256', razorpayConfig.KEY_SECRET);
        hmac.update(razorpay_order_id + "|" + razorpay_payment_id);
        const generated_signature = hmac.digest('hex');

        if (generated_signature !== razorpay_signature) {
            console.error("Razorpay Signature mismatch!");
            return res.json({ ok: false, error: 'Invalid signature' });
        }

        const payment = await Payment.findOne({ transactionId: razorpay_order_id });
        if (!payment) return res.json({ ok: false, error: 'Payment record not found' });

        if (payment.status !== 'success') {
            payment.status = 'success';
            payment.providerRefId = razorpay_payment_id;
            await payment.save();

            const user = await User.findOne({ userId: payment.userId });
            if (user) {
                user.walletBalance = (user.walletBalance || 0) + payment.baseAmount;
                if (payment.couponBonus > 0) {
                    user.superWalletBalance = (user.superWalletBalance || 0) + payment.couponBonus;
                }
                
                // Rule 4: Referrer Reward (The "Hook")
                // Only if it's the user's first successful recharge
                if (user.referredBy) {
                    const successCount = await Payment.countDocuments({ 
                        userId: user.userId, 
                        status: 'success',
                        reason: 'recharge' 
                    });
                    
                    if (successCount === 1) { // This is the first one (just set to success)
                        const referrer = await User.findOne({ userId: user.referredBy });
                        if (referrer) {
                            referrer.walletBalance = (referrer.walletBalance || 0) + 81;
                            referrer.totalEarnings = (referrer.totalEarnings || 0) + 81;
                            referrer.referralCount = (referrer.referralCount || 0) + 1;
                            await referrer.save();

                            // Rule 5: Tracking record
                            await Payment.create({
                                transactionId: `REF_${crypto.randomBytes(8).toString('hex')}`,
                                userId: referrer.userId,
                                amount: 81,
                                baseAmount: 81,
                                gstAmount: 0,
                                status: 'success',
                                reason: 'referral'
                            });
                            console.log(`[Referral Reward] Credited ₹81 to Referrer: ${referrer.name} for User: ${user.name}`);
                        }
                    }
                }

                await user.save();
                console.log(`[Razorpay] Wallet Credited: ${user.name} +₹${payment.baseAmount}`);
            }
        }

        res.json({ ok: true, status: 'success' });
    } catch (e) {
        console.error("Razorpay Callback Error:", e);
        res.json({ ok: false, error: 'Verification failed' });
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
