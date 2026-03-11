const express = require('express');
const router = express.Router();
const astrologerController = require('../controllers/astrologer.controller');

router.post('/register', astrologerController.register);
router.post('/online', astrologerController.toggleOnline);
router.post('/service-toggle', astrologerController.toggleService);

module.exports = router;
