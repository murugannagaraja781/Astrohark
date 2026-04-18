const express = require('express');
const router = express.Router();
const adminController = require('../controllers/admin.controller');

router.get('/academy/videos', adminController.getVideos);
router.post('/academy/videos', adminController.addVideo);
router.get('/banners', adminController.getBanners);
router.post('/banners', adminController.addBanner);
router.get('/deletion-requests', adminController.getDeletionRequests);
router.post('/process-deletion', adminController.processDeletion);
router.post('/update-balance', adminController.updateBalance);
router.get('/astrologers/pending', adminController.getPendingAstrologers);
router.post('/astrologers/approve', adminController.approveAstrologer);
router.get('/astrologer-performance/:astrologerId', adminController.getAstrologerPerformance);


module.exports = router;
