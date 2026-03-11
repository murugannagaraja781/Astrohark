const express = require('express');
const router = express.Router();
const paymentController = require('../controllers/payment.controller');

router.post('/token', paymentController.createToken);
router.get('/verify-token', paymentController.verifyToken);
router.post('/validate-coupon', paymentController.validateCoupon);
router.post('/create', paymentController.createPayment);
router.post('/callback', paymentController.callback);
router.get('/callback', paymentController.callback);
router.get('/history/:userId', paymentController.getHistory);

module.exports = router;
