require('dotenv').config();
const mongoose = require('mongoose');
const Session = require('../models/Session');
const User = require('../models/User');

async function run() {
  try {
    await mongoose.connect(process.env.MONGODB_URI);
    console.log("Connected to MongoDB");

    const page = 1;
    const limit = 5;
    const search = '';
    const type = 'all';

    const pipeline = [
      { $match: { status: 'ended' } }
    ];

    pipeline.push({
      $addFields: {
        searchClientId: { $ifNull: ['$clientId', '$fromUserId'] },
        searchAstroId: { $ifNull: ['$astrologerId', '$toUserId'] }
      }
    });

    pipeline.push({
      $lookup: {
        from: 'users',
        localField: 'searchClientId',
        foreignField: 'userId',
        as: 'clientDoc'
      }
    });

    pipeline.push({
      $lookup: {
        from: 'users',
        localField: 'searchAstroId',
        foreignField: 'userId',
        as: 'astroDoc'
      }
    });

    pipeline.push({
      $project: {
        sessionId: 1,
        clientId: 1,
        astrologerId: 1,
        fromUserId: 1,
        toUserId: 1,
        type: 1,
        startTime: 1,
        endTime: 1,
        duration: 1,
        totalEarned: 1,
        totalCharged: 1,
        actualBillingStart: 1,
        sessionEndAt: 1,
        clientName: { $ifNull: [ { $arrayElemAt: ['$clientDoc.name', 0] }, 'Unknown Client' ] },
        astrologerName: { $ifNull: [ { $arrayElemAt: ['$astroDoc.name', 0] }, 'Unknown Astrologer' ] }
      }
    });

    const totalCountPipeline = [...pipeline, { $count: 'total' }];
    
    const dataPipeline = [
      ...pipeline,
      { $sort: { startTime: -1, sessionEndAt: -1 } },
      { $skip: (page - 1) * limit },
      { $limit: limit }
    ];

    const top3AstroPipeline = [
      { $match: { status: 'ended' } },
      {
        $group: {
          _id: { $ifNull: ['$astrologerId', '$toUserId'] },
          sessions: { $sum: 1 },
          durationSum: { $sum: { $ifNull: ['$duration', 0] } }
        }
      },
      { $sort: { sessions: -1 } },
      { $limit: 3 },
      {
        $lookup: {
          from: 'users',
          localField: '_id',
          foreignField: 'userId',
          as: 'userDoc'
        }
      },
      {
        $project: {
          astrologerId: '$_id',
          name: { $ifNull: [ { $arrayElemAt: ['$userDoc.name', 0] }, 'Unknown Astrologer' ] },
          sessions: 1,
          duration: '$durationSum'
        }
      }
    ];

    const [countResult, dataResult, topAstrologers] = await Promise.all([
      Session.aggregate(totalCountPipeline),
      Session.aggregate(dataPipeline),
      Session.aggregate(top3AstroPipeline)
    ]);

    console.log("Count result:", countResult);
    console.log("Data result count:", dataResult.length);
    console.log("Top astrologers:", topAstrologers);

    // Test sharp
    console.log("Checking sharp import...");
    const sharp = require('sharp');
    console.log("Sharp version:", sharp.versions);

    console.log("Validation complete successfully!");
    process.exit(0);
  } catch (err) {
    console.error("Error in validation script:", err);
    process.exit(1);
  }
}

run();
