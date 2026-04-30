const AcademyVideo = require('../models/AcademyVideo');
const Banner = require('../models/Banner');
const AccountDeletionRequest = require('../models/AccountDeletionRequest');
const User = require('../models/User');
const performanceService = require('../services/performance.service');
const fs = require('fs');
const path = require('path');

exports.getVideos = async (req, res) => {
    const videos = await AcademyVideo.find().sort({ createdAt: -1 });
    res.json({ ok: true, videos });
};

exports.addVideo = async (req, res) => {
    try {
        const { title, youtubeUrl, thumbnail, category } = req.body;
        const video = await AcademyVideo.create({ title, youtubeUrl, thumbnail, category });
        res.json({ ok: true, video });
    } catch (e) {
        res.json({ ok: false, error: e.message });
    }
};

exports.deleteVideo = async (req, res) => {
    try {
        await AcademyVideo.findByIdAndDelete(req.params.id);
        res.json({ ok: true });
    } catch (e) {
        res.json({ ok: false, error: e.message });
    }
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
        // Hard delete the user to release phone number/userId for re-registration
        if (request.userId) {
            await User.deleteOne({ userId: request.userId });
            console.log(`[Admin] Hard deleted user ${request.userId} as per approved deletion request.`);
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

exports.getAllAstrologersPerformance = async (req, res) => {
    try {
        const { days } = req.query;
        const stats = await performanceService.getAllAstrologersPerformance(parseInt(days) || 30);
        res.json(stats);
    } catch (e) {
        res.json({ ok: false, error: e.message });
    }
};

exports.getConfig = async (req, res) => {
    try {
        const envPath = path.join(__dirname, '../.env');
        const content = fs.readFileSync(envPath, 'utf8');
        const lines = content.split('\n');
        const SENSITIVE_KEYS = [
            'TURN_USERNAME', 'TURN_SERVER', 'TURN_PASSWORD', 'TURN_REALM', 
            'MONGO_URI', 'JWT_SECRET', 'FCM_SERVER_KEY'
        ];

        lines.forEach(line => {
            const trimmed = line.trim();
            if (trimmed && !trimmed.startsWith('#')) {
                const [key, ...valueParts] = trimmed.split('=');
                if (key && !SENSITIVE_KEYS.includes(key)) {
                    config[key] = valueParts.join('=');
                }
            }
        });
        res.json({ ok: true, config });
    } catch (e) {
        res.json({ ok: false, error: e.message });
    }
};

exports.updateConfig = async (req, res) => {
    try {
        const { config } = req.body;
        if (!config) return res.json({ ok: false, error: 'Config object required' });

        const envPath = path.join(__dirname, '../.env');
        let content = fs.readFileSync(envPath, 'utf8');
        const lines = content.split('\n');

        const updatedLines = lines.map(line => {
            const trimmed = line.trim();
            if (trimmed && !trimmed.startsWith('#')) {
                const [key] = trimmed.split('=');
                if (key && config[key] !== undefined) {
                    process.env[key] = config[key];
                    return `${key}=${config[key]}`;
                }
            }
            return line;
        });

        // Add new keys if not present
        const existingKeys = lines.map(line => {
            const trimmed = line.trim();
            if (trimmed && !trimmed.startsWith('#')) {
                return trimmed.split('=')[0];
            }
            return null;
        }).filter(Boolean);

        Object.keys(config).forEach(key => {
            process.env[key] = config[key]; // Update current process env as well
            if (!existingKeys.includes(key)) {
                updatedLines.push(`${key}=${config[key]}`);
            }
        });

        fs.writeFileSync(envPath, updatedLines.join('\n'), 'utf8');
        res.json({ ok: true });
    } catch (e) {
        res.json({ ok: false, error: e.message });
    }
};
