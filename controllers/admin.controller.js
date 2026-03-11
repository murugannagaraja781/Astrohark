const AcademyVideo = require('../models/AcademyVideo');
const Banner = require('../models/Banner');
const AccountDeletionRequest = require('../models/AccountDeletionRequest');
const User = require('../models/User');

exports.getVideos = async (req, res) => {
    const videos = await AcademyVideo.find().sort({ createdAt: -1 });
    res.json({ ok: true, videos });
};

exports.addVideo = async (req, res) => {
    const video = await AcademyVideo.create(req.body);
    res.json({ ok: true, video });
};

exports.getBanners = async (req, res) => {
    const banners = await Banner.find().sort({ order: 1 });
    res.json({ ok: true, banners });
};

exports.addBanner = async (req, res) => {
    const banner = await Banner.create(req.body);
    res.json({ ok: true, banner });
};

exports.getDeletionRequests = async (req, res) => {
    const requests = await AccountDeletionRequest.find().sort({ requestedAt: -1 });
    res.json({ ok: true, requests });
};

exports.processDeletion = async (req, res) => {
    const { requestId, action, notes, adminId } = req.body;
    const request = await AccountDeletionRequest.findOne({ requestId });
    if (!request) return res.json({ ok: false, error: 'Request not found' });

    request.status = action; // approved/rejected
    request.notes = notes;
    request.processedAt = new Date();
    request.processedBy = adminId;
    await request.save();

    if (action === 'approved') {
        // Logic to actually delete or anonymize user
        if (request.userId) {
            await User.updateOne({ userId: request.userId }, { isBanned: true, name: 'Deleted User' });
        }
    }
    res.json({ ok: true });
};
