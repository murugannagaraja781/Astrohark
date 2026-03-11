const express = require('express');
const router = express.Router();
const commonController = require('../controllers/common.controller');

router.post('/city-autocomplete', commonController.cityAutocomplete);
router.post('/city-timezone', commonController.cityTimezone);

module.exports = router;
