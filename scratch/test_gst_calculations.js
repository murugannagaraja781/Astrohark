const mongoose = require('mongoose');
const dotenv = require('dotenv');
const path = require('path');

// Load environment variables
dotenv.config({ path: path.join(__dirname, '../.env') });

// Mock Razorpay SDK to prevent actual API calls during test
const Razorpay = require('razorpay');
Razorpay.prototype.orders = {
    create: async (opts) => {
        return {
            id: 'order_test_' + Math.random().toString(36).substring(2, 9),
            amount: opts.amount,
            currency: opts.currency
        };
    }
};

const User = require('../models/User');
const Payment = require('../models/Payment');
const paymentController = require('../controllers/payment.controller');

async function runTests() {
    console.log('Connecting to MongoDB...');
    await mongoose.connect(process.env.MONGODB_URI);
    console.log('Connected to MongoDB successfully.');

    // Ensure a test user exists
    let testUser = await User.findOne({ userId: 'test-gst-user' });
    if (!testUser) {
        testUser = await User.create({
            userId: 'test-gst-user',
            phone: '919999999999',
            name: 'Test GST User',
            role: 'client',
            walletBalance: 0
        });
    }

    const results = [];

    // Helper to run controller and capture response
    const mockRes = () => {
        const resObj = {
            jsonVal: null,
            json(val) {
                this.jsonVal = val;
                return this;
            }
        };
        return resObj;
    };

    // Test Case 1: Web Flow (Base ₹100) -> Should charge ₹118
    console.log('\n--- Running Test Case 1: Web Flow (Base ₹100) ---');
    {
        const req = {
            body: {
                userId: 'test-gst-user',
                amount: 100,
                isApp: false
            }
        };
        const res = mockRes();
        await paymentController.createPayment(req, res);
        const data = res.jsonVal;
        console.log('Response:', data);

        const paymentDoc = await Payment.findOne({ transactionId: data.orderId });
        const success = paymentDoc && 
                        paymentDoc.baseAmount === 100 && 
                        paymentDoc.gstAmount === 18 && 
                        paymentDoc.amount === 118;
        results.push({ name: 'Web Flow (Base ₹100)', success, details: `Base: ${paymentDoc?.baseAmount}, GST: ${paymentDoc?.gstAmount}, Total: ${paymentDoc?.amount}` });
    }

    // Test Case 2: Web Flow (Base ₹1000) -> Should charge ₹1180
    console.log('\n--- Running Test Case 2: Web Flow (Base ₹1000) ---');
    {
        const req = {
            body: {
                userId: 'test-gst-user',
                amount: 1000,
                isApp: false
            }
        };
        const res = mockRes();
        await paymentController.createPayment(req, res);
        const data = res.jsonVal;
        console.log('Response:', data);

        const paymentDoc = await Payment.findOne({ transactionId: data.orderId });
        const success = paymentDoc && 
                        paymentDoc.baseAmount === 1000 && 
                        paymentDoc.gstAmount === 180 && 
                        paymentDoc.amount === 1180;
        results.push({ name: 'Web Flow (Base ₹1000)', success, details: `Base: ${paymentDoc?.baseAmount}, GST: ${paymentDoc?.gstAmount}, Total: ${paymentDoc?.amount}` });
    }

    // Test Case 3: App Flow (Total ₹118) -> Should charge ₹118 (Base ₹100)
    console.log('\n--- Running Test Case 3: App Flow (Total ₹118) ---');
    {
        const req = {
            body: {
                userId: 'test-gst-user',
                amount: 118,
                isApp: true
            }
        };
        const res = mockRes();
        await paymentController.createPayment(req, res);
        const data = res.jsonVal;
        console.log('Response:', data);

        const paymentDoc = await Payment.findOne({ transactionId: data.orderId });
        const success = paymentDoc && 
                        paymentDoc.baseAmount === 100 && 
                        paymentDoc.gstAmount === 18 && 
                        paymentDoc.amount === 118;
        results.push({ name: 'App Flow (Total ₹118)', success, details: `Base: ${paymentDoc?.baseAmount}, GST: ${paymentDoc?.gstAmount}, Total: ${paymentDoc?.amount}` });
    }

    // Test Case 4: App Flow (Total ₹1180) -> Should charge ₹1180 (Base ₹1000)
    console.log('\n--- Running Test Case 4: App Flow (Total ₹1180) ---');
    {
        const req = {
            body: {
                userId: 'test-gst-user',
                amount: 1180,
                isApp: true
            }
        };
        const res = mockRes();
        await paymentController.createPayment(req, res);
        const data = res.jsonVal;
        console.log('Response:', data);

        const paymentDoc = await Payment.findOne({ transactionId: data.orderId });
        const success = paymentDoc && 
                        paymentDoc.baseAmount === 1000 && 
                        paymentDoc.gstAmount === 180 && 
                        paymentDoc.amount === 1180;
        results.push({ name: 'App Flow (Total ₹1180)', success, details: `Base: ${paymentDoc?.baseAmount}, GST: ${paymentDoc?.gstAmount}, Total: ${paymentDoc?.amount}` });
    }

    // Test Case 5: Token Web Flow (Base ₹10000) -> Should charge ₹11800 (Base ₹10000)
    console.log('\n--- Running Test Case 5: Token Web Flow (Base ₹10000) ---');
    {
        // 1. Create Token
        const reqToken = {
            body: {
                userId: 'test-gst-user',
                amount: 10000
            }
        };
        const resToken = mockRes();
        await paymentController.createToken(reqToken, resToken);
        const tokenData = resToken.jsonVal;
        console.log('Token created:', tokenData);

        // 2. Create Payment
        const reqPay = {
            body: {
                token: tokenData.token
            }
        };
        const resPay = mockRes();
        await paymentController.createPayment(reqPay, resPay);
        const data = resPay.jsonVal;
        console.log('Payment created:', data);

        const paymentDoc = await Payment.findOne({ transactionId: data.orderId });
        const success = paymentDoc && 
                        paymentDoc.baseAmount === 10000 && 
                        paymentDoc.gstAmount === 1800 && 
                        paymentDoc.amount === 11800;
        results.push({ name: 'Token Web Flow (Base ₹10000)', success, details: `Base: ${paymentDoc?.baseAmount}, GST: ${paymentDoc?.gstAmount}, Total: ${paymentDoc?.amount}` });

        // Test Case 6: Retry Flow -> Should reuse existing order
        console.log('\n--- Running Test Case 6: Retry Flow ---');
        const reqRetry = {
            body: {
                transactionId: data.orderId
            }
        };
        const resRetry = mockRes();
        await paymentController.createPayment(reqRetry, resRetry);
        const retryData = resRetry.jsonVal;
        console.log('Retry Response:', retryData);

        const retrySuccess = retryData.ok && retryData.orderId === data.orderId && retryData.amount === 1180000; // in paise
        results.push({ name: 'Retry Flow', success: retrySuccess, details: `Original Order ID: ${data.orderId}, Retry Order ID: ${retryData.orderId}, Paise: ${retryData.amount}` });
    }

    console.log('\n================ TEST RESULTS ================');
    results.forEach(r => {
        console.log(`${r.success ? '✅' : '❌'} ${r.name} - ${r.details}`);
    });
    console.log('==============================================');

    // Cleanup test user & payments
    await Payment.deleteMany({ userId: 'test-gst-user' });
    await User.deleteOne({ userId: 'test-gst-user' });
    await mongoose.disconnect();
}

runTests().catch(err => {
    console.error('Test run failed:', err);
    process.exit(1);
});
