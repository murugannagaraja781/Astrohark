const mongoose = require('mongoose');
const Session = require('../models/Session');
const User = require('../models/User');

/**
 * Astrologer Performance Service
 * Tracks work depth, earnings, and engagement metrics
 */

const getAstrologerPerformance = async (astrologerId, days = 30) => {
    try {
        const startTimeLimit = Date.now() - (days * 24 * 60 * 60 * 1000);

        // Core Session Metrics
        const sessionStats = await Session.aggregate([
            { 
                $match: { 
                    astrologerId: astrologerId, 
                    status: 'ended', 
                    startTime: { $gte: startTimeLimit } 
                } 
            },
            {
                $group: {
                    _id: null,
                    totalMinutes: { $sum: { $divide: ["$duration", 60] } }, // assuming duration is in seconds
                    avgMinutes: { $avg: { $divide: ["$duration", 60] } },
                    totalEarned: { $sum: "$totalEarned" },
                    totalCharged: { $sum: "$totalCharged" },
                    sessionCount: { $sum: 1 },
                    unlimitedCount: { $sum: { $cond: [{ $eq: ["$type", "unlimited"] }, 1, 0] } },
                    slabCount: { $sum: { $cond: [{ $ne: ["$type", "unlimited"] }, 1, 0] } }
                }
            }
        ]);

        // Retention Rate (Repeat Clients)
        const retentionStats = await Session.aggregate([
            { 
                $match: { 
                    astrologerId: astrologerId, 
                    status: 'ended',
                    startTime: { $gte: startTimeLimit }
                } 
            },
            { $group: { _id: "$clientId", sessionCount: { $sum: 1 } } },
            { 
                $group: { 
                    _id: null, 
                    totalUniqueClients: { $sum: 1 },
                    repeatClients: { $sum: { $cond: [{ $gt: ["$sessionCount", 1] }, 1, 0] } }
                } 
            }
        ]);

        // Visibility / Response Rate
        const visibilityStats = await Session.aggregate([
            { 
                $match: { 
                    astrologerId: astrologerId, 
                    startTime: { $gte: startTimeLimit } 
                } 
            },
            { $group: { _id: "$status", count: { $sum: 1 } } }
        ]);

        const visibilityMap = {};
        let totalRequests = 0;
        visibilityStats.forEach(s => {
            visibilityMap[s._id] = s.count;
            totalRequests += s.count;
        });

        // Calculate final percentages
        const overview = sessionStats[0] || {
            totalMinutes: 0,
            avgMinutes: 0,
            totalEarned: 0,
            totalCharged: 0,
            sessionCount: 0,
            unlimitedCount: 0,
            slabCount: 0
        };

        const retention = retentionStats[0] || { totalUniqueClients: 0, repeatClients: 0 };
        const retentionRate = retention.totalUniqueClients > 0 
            ? ((retention.repeatClients / retention.totalUniqueClients) * 100).toFixed(1) 
            : 0;

        const responseRate = totalRequests > 0 
            ? (((visibilityMap['ended'] || 0) / totalRequests) * 100).toFixed(1) 
            : 0;

        return {
            ok: true,
            astrologerId,
            periodDays: days,
            metrics: {
                workDepth: {
                    avgSessionDuration: overview.avgMinutes.toFixed(1),
                    totalMinutes: overview.totalMinutes.toFixed(0),
                    retentionRate: retentionRate + "%",
                    responseRate: responseRate + "%",
                    sessionCount: overview.sessionCount
                },
                earnings: {
                    totalPayout: overview.totalEarned.toFixed(2),
                    totalRevenue: overview.totalCharged.toFixed(2),
                    adminShare: (overview.totalCharged - overview.totalEarned).toFixed(2),
                    slabEarnings: (overview.totalEarned * (overview.slabCount / (overview.sessionCount || 1))).toFixed(2), // Rough estimate
                    unlimitedEarnings: (overview.totalEarned * (overview.unlimitedCount / (overview.sessionCount || 1))).toFixed(2)
                },
                callBreakdown: visibilityMap
            }
        };

    } catch (error) {
        console.error("[PerformanceService] Error:", error);
        return { ok: false, error: error.message };
    }
};

const getAllAstrologersPerformance = async (days = 30) => {
    try {
        const startTimeLimit = Date.now() - (days * 24 * 60 * 60 * 1000);
        
        // Find all astrologers
        const astros = await User.find({ role: 'astrologer' }, 'userId name image');
        const astroIds = astros.map(a => a.userId);

        const results = await Session.aggregate([
            { 
                $match: { 
                    astrologerId: { $in: astroIds },
                    status: 'ended', 
                    startTime: { $gte: startTimeLimit } 
                } 
            },
            {
                $group: {
                    _id: "$astrologerId",
                    totalMinutes: { $sum: { $divide: ["$duration", 60] } },
                    totalEarned: { $sum: "$totalEarned" },
                    sessionCount: { $sum: 1 },
                    uniqueClients: { $addToSet: "$clientId" }
                }
            },
            { $sort: { totalEarned: -1 } }
        ]);

        const performanceData = results.map(r => {
            const user = astros.find(a => a.userId === r._id);
            return {
                userId: r._id,
                name: user ? user.name : 'Unknown',
                image: user ? user.image : '',
                totalMinutes: r.totalMinutes.toFixed(1),
                totalEarned: r.totalEarned.toFixed(2),
                sessionCount: r.sessionCount,
                clientCount: r.uniqueClients.length
            };
        });

        return { ok: true, list: performanceData };

    } catch (error) {
        console.error("[PerformanceService] All Astros Error:", error);
        return { ok: false, error: error.message };
    }
};

module.exports = {
    getAstrologerPerformance,
    getAllAstrologersPerformance
};
