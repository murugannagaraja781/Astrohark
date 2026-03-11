const User = require('../models/User');
const { formatImageUrl } = require('../utils/formatImage');

async function getFormattedAstrologers(SERVER_URL) {
    try {
        const astros = await User.find({ role: 'astrologer', approvalStatus: 'approved' })
            .select('userId name phone skills price isOnline isChatOnline isAudioOnline isVideoOnline experience isVerified image walletBalance totalEarnings isBusy languages orderCount isDocumentVerified')
            .lean();

        return astros.map(a => ({
            userId: a.userId,
            name: a.name,
            skills: a.skills || [],
            price: a.price || 15,
            isOnline: a.isOnline || false,
            isChatOnline: a.isChatOnline || false,
            isAudioOnline: a.isAudioOnline || false,
            isVideoOnline: a.isVideoOnline || false,
            experience: a.experience || 0,
            isVerified: a.isVerified || false,
            isBusy: a.isBusy || false,
            image: formatImageUrl(a.image, a.name, SERVER_URL),
            languages: a.languages || ['Tamil', 'English'],
            orderCount: a.orderCount || 0,
            isDocumentVerified: a.isDocumentVerified || false
        }));
    } catch (e) {
        console.error('Error fetching formatted astros:', e);
        return [];
    }
}

async function broadcastAstroUpdate(io, SERVER_URL) {
    try {
        const formattedAstros = await getFormattedAstrologers(SERVER_URL);
        io.emit('astrologer-update', formattedAstros);
        console.log(`Broadcasting update for ${formattedAstros.length} astrologers.`);
    } catch (e) {
        console.error('Broadcast Error:', e);
    }
}

module.exports = { getFormattedAstrologers, broadcastAstroUpdate };
