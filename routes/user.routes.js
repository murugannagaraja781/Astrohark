const express = require('express');
const router = express.Router();
const userController = require('../controllers/user.controller');

router.get('/:userId', userController.getProfile);
router.get('/:userId/birthdata', userController.getBirthData);
router.get('/chat/history/:sessionId', userController.getChatHistory);
router.post('/apply-referral', userController.applyReferral);

module.exports = router;
