const AcademyVideo = require('../models/AcademyVideo');
const Banner = require('../models/Banner');
const AccountDeletionRequest = require('../models/AccountDeletionRequest');
const User = require('../models/User');
const performanceService = require('../services/performance.service');

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
exports.updateBalance = async (req, res) => {
    try {
        const { userId, amount, action } = req.body; // action: 'add' or 'subtract'
        if (!userId || amount === undefined) return res.json({ ok: false, error: 'User ID and amount required' });

        const user = await User.findOne({ userId });
        if (!user) return res.json({ ok: false, error: 'User not found' });

        if (action === 'subtract') {
            user.walletBalance = (user.walletBalance || 0) - parseFloat(amount);
        } else {
            user.walletBalance = (user.walletBalance || 0) + parseFloat(amount);
        }

        await user.save();
        res.json({ ok: true, balance: user.walletBalance });
    } catch (error) {
        res.json({ ok: false, error: error.message });
    }
};

exports.getPendingAstrologers = async (req, res) => {
    try {
        const list = await User.find({ role: 'astrologer', approvalStatus: 'pending' }).sort({ createdAt: -1 });
        res.json({ ok: true, list });
    } catch (e) {
        res.json({ ok: false, error: e.message });
    }
};

exports.approveAstrologer = async (req, res) => {
    try {
        const { userId, status } = req.body; // status: 'approved' or 'rejected'
        const user = await User.findOneAndUpdate({ userId }, { approvalStatus: status }, { new: true });
        if (!user) return res.json({ ok: false, error: 'User not found' });
        res.json({ ok: true, status: user.approvalStatus });
    } catch (e) {
        res.json({ ok: false, error: e.message });
    }
};

exports.getAstrologerPerformance = async (req, res) => {
    try {
        const { astrologerId } = req.params;
        const { days } = req.query;
        const stats = await performanceService.getAstrologerPerformance(astrologerId, parseInt(days) || 30);
        res.json(stats);
    } catch (e) {
        res.json({ ok: false, error: e.message });
    }
};
