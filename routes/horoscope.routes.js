const express = require('express');
const router = express.Router();
const horoscopeController = require('../controllers/horoscope.controller');

router.post('/charts/birth-chart', horoscopeController.getBirthChart);
router.post('/match/porutham', horoscopeController.getMatchPorutham);

module.exports = router;
