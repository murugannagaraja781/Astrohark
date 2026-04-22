const express = require('express');
const router = express.Router();
const commonController = require('../controllers/common.controller');

router.post('/city-autocomplete', commonController.cityAutocomplete);
router.post('/city-timezone', commonController.cityTimezone);
router.get('/config/app', commonController.getAppConfig);
router.get('/academy/videos', require('../controllers/admin.controller').getVideos);

module.exports = router;
