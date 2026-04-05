const User = require('../models/User');
const ChatMessage = require('../models/ChatMessage');
const { formatImageUrl } = require('../utils/formatImage');

exports.getProfile = async (req, res) => {
    try {
        const user = await User.findOne({ userId: req.params.userId });
        if (!user) return res.status(404).json({ ok: false, error: 'User not found' });
        
        const SERVER_URL = req.app.get('SERVER_URL');
        res.json({ 
            ok: true, 
            ...user._doc, 
            image: formatImageUrl(user.image, user.name, SERVER_URL)
        });
    } catch (e) {
        res.status(500).json({ ok: false });
    }
};

exports.getChatHistory = async (req, res) => {
    try {
        const messages = await ChatMessage.find({ sessionId: req.params.sessionId }).sort({ timestamp: 1 });
        res.json({ ok: true, messages });
    } catch (e) {
        res.status(500).json({ ok: false });
    }
};
