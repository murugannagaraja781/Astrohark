// server.js
require('dotenv').config(); // Load environment variables from .env file
// Force update timestamp: 2026-03-11 (OTP Fix)
const https = require('https');
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const path = require('path');
const crypto = require('crypto');
const mongoose = require('mongoose');
const multer = require('multer');

const { DateTime } = require('luxon');
const { fetchDailyHoroscope } = require("./utils/rasiEng/horoscopeData");

const User = require('./models/User');
const CallRequest = require('./models/CallRequest');
const Session = require('./models/Session');
const PairMonth = require('./models/PairMonth');
const BillingLedger = require('./models/BillingLedger');
const Withdrawal = require('./models/Withdrawal');
const Payment = require('./models/Payment');
const ChatMessage = require('./models/ChatMessage');
const AcademyVideo = require('./models/AcademyVideo');
const Banner = require('./models/Banner');
const AccountDeletionRequest = require('./models/AccountDeletionRequest');
const GlobalSettings = require('./models/GlobalSettings');

const {
  userSockets,
  socketToUser,
  userActiveSession,
  activeSessions,
  pendingMessages,
  otpStore,
  offlineTimeouts,
  savedAstroStatus,
  sessionDisconnectTimeouts,
  SESSION_GRACE_PERIOD
} = require('./services/socketStore');

// Razorpay Config (Separated into config/razorpay.js and handled via routes)


// Polyfill for fetch (Node.js 18+ has it built-in)
if (!global.fetch) {
  global.fetch = require('node-fetch');
}

// FCM v1 API and Firebase Admin consolidated in push.service.js and config/firebase.js
const { sendFcmV1Push } = require('./services/push.service');
const callHandler = require('./socket/callHandler');
const billingService = require('./services/billing.service');
const { admin, callApp, fcmAuth } = require('./config/firebase'); 

// Mobile Token Store (Legacy if not used)
let mobileTokenStore = new Map();


// FCM Initialization handled by imported service

const app = express();
app.set('trust proxy', 1); // Standard for one-hop reverse proxy (like Nginx)
const server = http.createServer(app);
const io = new Server(server);
app.set('io', io);
billingService.setIo(io);

// WebRTC ICE/TURN Config
const DEFAULT_ICE_SERVERS = [
  { urls: 'stun:free.expressturn.com:3478' },
  {
    urls: 'turn:free.expressturn.com:3478?transport=udp',
    username: '000000002089544731',
    credential: 'HIzMMgt7G9eioH07AnygPJHRWGM='
  },
  {
    urls: 'turn:free.expressturn.com:3478?transport=tcp',
    username: '000000002089544731',
    credential: 'HIzMMgt7G9eioH07AnygPJHRWGM='
  }
];

app.get('/api/webrtc-config', (req, res) => {
  try {
    const iceServers = process.env.ICE_SERVERS ? JSON.parse(process.env.ICE_SERVERS) : DEFAULT_ICE_SERVERS;
    res.json({ ok: true, iceServers });
  } catch (e) {
    console.error('Error parsing ICE_SERVERS:', e);
    res.json({ ok: true, iceServers: DEFAULT_ICE_SERVERS });
  }
});
const cors = require("cors");
const compression = require('compression');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');

// Security & Optimization
app.use(helmet({
  contentSecurityPolicy: false,
}));
app.use(compression());
app.use(cors({ origin: "*" }));

// Rate Limiting
const globalLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 1000, // limit each IP to 1000 requests per windowMs
  message: 'Too many requests from this IP, please try again after 15 minutes',
  validate: false
});
app.use(globalLimiter);

// Specific limiter for payment/auth
const apiLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 100,
  message: 'Too many API requests, please try again later',
  validate: false
});
app.use('/api/', apiLimiter);

// Global to store server URL for absolute image paths
let SERVER_URL = process.env.SERVER_URL || '';

// Temporary Test Route (will remove after testing)
app.get('/api/test-link', async (req, res) => {
  try {
     const user = await User.findOne({ userId: { $exists: true } });
     if (!user) return res.send("No users in database to test with.");
     
     const response = await fetch(`${SERVER_URL || 'http://localhost:3000'}/api/payment/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: user.userId, amount: 100 })
     });
     const data = await response.json();
     if (data.token) {
        res.send(`Test Link: ${SERVER_URL || 'http://localhost:3000'}/payment.html?token=${data.token}`);
     } else {
        res.json(data);
     }
  } catch (e) { res.status(500).send(e.message); }
});

// Middleware to capture host for absolute image paths
app.use((req, res, next) => {
  if (!SERVER_URL) {
    const host = req.get('x-forwarded-host') || req.get('host');
    const protocol = req.get('x-forwarded-proto') || req.protocol;
    if (host) {
      SERVER_URL = `${protocol}://${host}`;
      console.log(`[Config] Automatically detected SERVER_URL: ${SERVER_URL}`);
    }
  }
  app.set('SERVER_URL', SERVER_URL);
  next();
});

// Diagnostic route to check server URL
app.get('/api/check-server-url', (req, res) => {
  res.json({
    ok: true,
    serverUrl: SERVER_URL,
    headers: {
      host: req.get('host'),
      forwardedHost: req.get('x-forwarded-host'),
      forwardedProto: req.get('x-forwarded-proto'),
      protocol: req.protocol
    }
  });
});

function formatImageUrl(imgPath, name) {
  if (!imgPath) {
    return `https://ui-avatars.com/api/?name=${encodeURIComponent(name || 'User')}&background=random`;
  }
  if (imgPath.startsWith('http')) return imgPath;
  if (SERVER_URL) {
    // Ensure imgPath starts with / for joining
    const path = imgPath.startsWith('/') ? imgPath : `/${imgPath}`;
    return `${SERVER_URL}${path}`;
  }
  return imgPath;
}

const { getFormattedAstrologers, broadcastAstroUpdate: serviceBroadcastAstroUpdate } = require('./services/astrologer.service');

async function broadcastAstroUpdate() {
  await serviceBroadcastAstroUpdate(io, SERVER_URL);
}

app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(express.static('public'));  // Serve static files

// Policy Page Routes
app.get('/privacy-policy', (req, res) => res.sendFile(path.join(__dirname, 'public', 'privacy-policy.html')));
app.get('/terms-condition', (req, res) => res.sendFile(path.join(__dirname, 'public', 'terms-condition.html')));
app.get('/refund-cancellation-policy', (req, res) => res.sendFile(path.join(__dirname, 'public', 'refund-cancellation-policy.html')));
app.get('/return-policy', (req, res) => res.sendFile(path.join(__dirname, 'public', 'return-policy.html')));
app.get('/shipping-policy', (req, res) => res.sendFile(path.join(__dirname, 'public', 'shipping-policy.html')));

// Fallback Wallet Route for App Users who get redirected to /wallet
app.get('/wallet', (req, res) => {
  const status = req.query.status || 'unknown';
  const reason = req.query.reason || '';

  // Construct Deep Link
  const scheme = status === 'success' ? 'astrohark://payment-success' : 'astrohark://payment-failed';
  const deepLink = `${scheme}?status=${status}&reason=${reason}`;
  const intentUrl = `intent://payment-${status === 'success' ? 'success' : 'failed'}?status=${status}#Intent;scheme=astrohark;package=com.astrohark.app;end`;

  res.send(`
    <html>
      <head>
        <title>Payment Status</title>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          body { font-family: sans-serif; padding: 20px; text-align: center; }
          .btn { background: #059669; color: white; padding: 15px 30px; border-radius: 8px; text-decoration: none; display: inline-block; margin-top: 20px; font-weight: bold;}
        </style>
      </head>
      <body>
        <h3>Payment ${status === 'success' ? 'Successful' : 'Completed'}</h3>
        <p>Redirecting you back to the app...</p>
        <a href="${deepLink}" class="btn">Return to Home</a>
        <script>
          // Auto Redirect
          setTimeout(() => { window.location.href = "${intentUrl}"; }, 500);
          setTimeout(() => { window.location.href = "${deepLink}"; }, 1500);
        </script>
      </body>
    </html>
  `);
});


// Routes
const rasiEngRouter = require("./routes/rasiEng");
const rasipalanRouter = require("./routes/rasipalan");
const freeHoroscopeRouter = require("./routes/freeHoroscope");

// Newly Extracted Routers
const authRoutes = require('./routes/auth.routes');
const commonRoutes = require('./routes/common.routes');
const astrologerRoutes = require('./routes/astrologer.routes');
const adminRoutes = require('./routes/admin.routes');
const horoscopeRoutes = require('./routes/horoscope.routes');
const userRoutes = require('./routes/user.routes');
const paymentRoutes = require('./routes/payment.routes');
const pageRoutes = require('./routes/page.routes');

app.use("/api/rasi-eng", rasiEngRouter);
app.use("/api/rasipalan", rasipalanRouter);
app.use("/api/horoscope/rasi-palan", rasipalanRouter); // Android App specific path
app.use("/api/horoscope", freeHoroscopeRouter); // Free horoscope chart generation

// Register Extracted Routes
app.use('/', pageRoutes);
app.use('/api', authRoutes);
app.use('/api', commonRoutes);
app.use('/api/astrologer', astrologerRoutes);
app.use('/api/admin', adminRoutes);
app.use('/api', horoscopeRoutes);
app.use('/api/user', userRoutes);
app.use('/api/chat', userRoutes);
app.use('/api/payment', paymentRoutes);



// FCM Test Endpoint - Verify Firebase is working
app.get('/api/test-fcm', async (req, res) => {
  try {
    if (!fcmAuth) {
      return res.json({
        ok: false,
        status: 'NOT_INITIALIZED',
        error: 'FCM Auth not initialized'
      });
    }

    // Try to get access token to verify credentials work
    const token = await fcmAuth.getAccessToken();

    if (token) {
      return res.json({
        ok: true,
        status: 'WORKING',
        message: 'Firebase Admin SDK is properly configured and can get access tokens'
      });
    } else {
      return res.json({
        ok: false,
        status: 'TOKEN_FAILED',
        error: 'Could not get access token'
      });
    }
  } catch (err) {
    return res.json({
      ok: false,
      status: 'ERROR',
      error: err.message
    });
  }
});

// WebRTC Configuration API - Provides TURN/STUN details to Mobile App/Web
function getWebRTCConfig() {
  return {
    ok: true,
    stunServer: process.env.STUN_SERVER || 'stun:free.expressturn.com:3478',
    turnServer: process.env.TURN_SERVER || 'free.expressturn.com',
    turnPort: process.env.TURN_PORT || '3478',
    turnUsername: process.env.TURN_USERNAME || '000000002089544731',
    turnPassword: process.env.TURN_PASSWORD || 'HIzMMgt7G9eioH07AnygPJHRWGM=',
    iceServers: [
      { urls: 'stun:stun.l.google.com:19302' },
      { urls: 'stun:stun1.l.google.com:19302' },
      { urls: 'stun:stun2.l.google.com:19302' },
      { urls: 'stun:stun3.l.google.com:19302' },
      { urls: 'stun:stun4.l.google.com:19302' },
      { urls: process.env.STUN_SERVER || 'stun:stun.l.google.com:19302' },
      {
        urls: `turn:${process.env.TURN_SERVER || 'turn.astrohark.com'}:${process.env.TURN_PORT || '3478'}?transport=udp`,
        username: process.env.TURN_USERNAME || 'webrtcuser',
        credential: process.env.TURN_PASSWORD || 'strongpassword123'
      },
      {
        urls: `turn:${process.env.TURN_SERVER || 'turn.astrohark.com'}:${process.env.TURN_PORT || '3478'}?transport=tcp`,
        username: process.env.TURN_USERNAME || 'webrtcuser',
        credential: process.env.TURN_PASSWORD || 'strongpassword123'
      }
    ]
  };
}

app.get('/api/config/webrtc', (req, res) => {
  try {
    res.json(getWebRTCConfig());
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

app.get('/api/webrtc-config', (req, res) => {
  try {
    res.json(getWebRTCConfig());
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// ===== File upload setup =====
const uploadDir = path.join(__dirname, 'uploads');
const upload = multer({ dest: uploadDir });

app.use('/uploads', express.static(uploadDir));


app.post('/upload', upload.single('file'), (req, res) => {
  // ... (keeping upload logic if valid) ...
  return res.json({ ok: true, url: req.file ? '/uploads/' + req.file.filename : '' });
});

app.post('/api/user/profile-pic', upload.single('image'), async (req, res) => {
  try {
    // Robust check for userId in body or query
    const userId = req.body.userId || req.query.userId;
    console.log(`[Upload] Profile pic request for: ${userId}, file: ${req.file?.filename}`);
    
    if (!userId || !req.file) {
      console.warn('[Upload] Failed: Missing userId or image');
      return res.status(400).json({ ok: false, error: 'Missing userId or image' });
    }

    const imageUrl = 'uploads/' + req.file.filename;
    // Use returnDocument: 'after' to fix deprecation warning
    const user = await User.findOneAndUpdate(
      { userId },
      { $set: { image: imageUrl } },
      { returnDocument: 'after' }
    );

    if (!user) {
      console.warn(`[Upload] Failed: User ${userId} not found`);
      return res.status(404).json({ ok: false, error: 'User not found' });
    }

    // Broadcast if astrologer to update client list
    if (user.role === 'astrologer') {
      const io = req.app.get('io');
      const SERVER_URL = req.app.get('SERVER_URL');
      const { broadcastAstroUpdate } = require('./services/astrologer.service');
      broadcastAstroUpdate(io, SERVER_URL);
    }

    console.log(`[Upload] Success: ${userId} updated with ${imageUrl}`);
    res.json({ ok: true, image: imageUrl });
  } catch (err) {
    console.error('Profile pic upload error:', err);
    res.status(500).json({ ok: false, error: err.message });
  }
});

const MONGO_URI = process.env.MONGODB_URI || 'mongodb://localhost:27017/astrohark';

// Helper function to check if MongoDB is connected
const isMongoConnected = () => {
  return mongoose.connection.readyState === 1;
};

// Helper function for safe database operations
const safeDbOperation = async (operation, fallbackValue = null) => {
  if (!isMongoConnected()) {
    console.warn('⚠️  MongoDB not connected, skipping database operation');
    return fallbackValue;
  }
  try {
    return await operation();
  } catch (err) {
    console.error('Database operation error:', err.message);
    return fallbackValue;
  }
};

// MongoDB Connection with retry logic
const connectDB = async (retries = 5) => {
  try {
    await mongoose.connect(MONGO_URI, {
      serverSelectionTimeoutMS: 10000,
      socketTimeoutMS: 45000,
      maxPoolSize: 10,
      minPoolSize: 2
    });
    console.log('✅ MongoDB Connected to:', MONGO_URI.split('@').pop().split('?')[0]);
    if (process.env.NODE_ENV !== 'test') {
      seedDatabase();
    }
  } catch (err) {
    console.error('❌ MongoDB Connection Error:', err.message);

    if (err.message.includes('IP that isn\'t whitelisted') || err.message.includes('IP whitelist')) {
      console.error('👉 ACTION NEEDED: Login to MongoDB Atlas and whitelist your server IP');
      console.error('   Go to: Network Access → Add IP Address → Allow Access from Anywhere (0.0.0.0/0)');
    }

    if (retries > 0) {
      console.log(`🔄 Retrying MongoDB connection... (${retries} attempts left)`);
      setTimeout(() => connectDB(retries - 1), 5000);
    } else {
      console.error('❌ MongoDB connection failed after all retries');
      console.error('⚠️  Server will continue without database (some features may not work)');
    }
  }
};

// Handle MongoDB connection events
mongoose.connection.on('connected', () => {
  console.log('📡 Mongoose connected to MongoDB');
});

mongoose.connection.on('error', (err) => {
  console.error('❌ Mongoose connection error:', err.message);
});

mongoose.connection.on('disconnected', () => {
  console.log('📴 Mongoose disconnected from MongoDB');
});

// Graceful shutdown
const gracefulShutdown = async (signal) => {
  console.log(`📡 Received ${signal}. Shutting down gracefully...`);
  try {
    if (mongoose.connection.readyState !== 0) { // 0 = disconnected
      await mongoose.connection.close();
      console.log('✅ MongoDB connection closed through app termination');
    }
    process.exit(0);
  } catch (err) {
    if (err.name === 'MongoClientClosedError') {
      console.log('ℹ️ MongoDB connection was already closed.');
      process.exit(0);
    }
    console.error('❌ Error closing MongoDB connection:', err);
    process.exit(1);
  }
};

process.on('SIGINT', () => gracefulShutdown('SIGINT'));
process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));

// Start connection
connectDB();

// Schemas





// Phase 15: Withdrawal Schema






// Account Deletion Request Schema


// Memory cache for performance
let SLAB_RATES = {
  1: 0.30,
  2: 0.35,
  3: 0.40,
  4: 0.50
};

async function loadSettings() {
  try {
    const slabSetting = await GlobalSettings.findOne({ key: 'slab_rates' });
    if (slabSetting) {
      SLAB_RATES = slabSetting.value;
      console.log('[Settings] Slab Rates loaded:', SLAB_RATES);
    } else {
      // Initialize if not exists
      await GlobalSettings.create({ key: 'slab_rates', value: SLAB_RATES });
    }
  } catch (e) {
    console.error('[Settings] Failed to load settings:', e);
  }
}
loadSettings();


// ===== Seed Data =====
async function seedDatabase() {
  const count = await User.countDocuments();
  if (count > 0) return; // Already seeded

  console.log('--- Seeding Database ---');

  const create = async (name, phone, role) => {
    const userId = crypto.randomUUID();
    await User.create({
      userId, name, phone, role,
      skills: role === 'astrologer' ? ['Vedic', 'Prashana'] : [],
      price: 20,
      walletBalance: 369,
      experience: role === 'astrologer' ? 5 : 0,
      approvalStatus: role === 'astrologer' ? 'approved' : 'pending'
    });
  };

  await create('Test Astrologer', '8000000001', 'astrologer');
  await create('Test Client', '9000000001', 'client');
  await create('Super Admin', '9876543210', 'superadmin');

  console.log('--- Database Seeded ---');
}
// seedDatabase(); // Moved to DB connection success

// Stores are now imported from ./services/socketStore.js

const OFFLINE_GRACE_PERIOD = 5 * 60 * 1000; // 5 minutes

// --- Static Files & Root Route ---
app.use(express.static(path.join(__dirname, 'public')));
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// Store OTPs in memory { phone: { otp, expires } }
// const otpStore = new Map(); // This was already declared above, moving it here for context with the new code.

// ===== Daily Horoscope Logic =====
let dailyHoroscope = { date: '', content: '' };

function generateTamilHoroscope() {
  const now = new Date();
  const dateStr = now.toDateString();

  if (dailyHoroscope.date === dateStr) return dailyHoroscope.content;

  // Tamil Templates (Grammatically Correct Parts)
  // Spoken Tamil Daily Predictions (One Sentence Rule)
  const predictions = [
    "இன்னிக்கு வேலைல கொஞ்சம் கவனமா இருங்க, சின்ன தப்பு கூட பெருசா ஆகலாம்.",
    "பண வரவு நல்லா இருக்கும், ஆனா செலவும் அதுக்கு ஏத்த மாதிரி வரும்.",
    "குடும்பத்துல சின்ன சின்ன சண்டை வரலாம், நீங்க கொஞ்சம் விட்டுக்கொடுங்க.",
    "உடம்புல சின்ன சோர்வு இருக்கும், சரியான நேரத்துக்கு சாப்பிடுங்க.",
    "புதுசா எதுவும் முயற்சி பண்ண வேண்டாம், இருக்கறத சரியா பாத்துக்கோங்க.",
    "நண்பர்கள் மூலமா நல்ல செய்தி வரும், சந்தோஷமா இருப்பீங்க.",
    "இன்னிக்கு உங்களுக்கு யோகமான நாள், நினைச்சது நடக்கும்.",
    "வெளியிடங்களுக்கு போகும்போது வண்டியை மெதுவா ஓட்டுங்க.",
    "வேலை தேடுறவங்களுக்கு இன்னிக்கு நல்ல பதில் கிடைக்கும்.",
    "யார் கிட்டயும் கடன் வாங்க வேண்டாம், கொடுக்கவும் வேண்டாம்.",
    "கோபத்தை குறைச்சுகிட்டா இன்னிக்கு எல்லாமே நல்லபடியா நடக்கும்.",
    "பிள்ளைகள் விஷயத்துல கொஞ்சம் அக்கறை காட்டுங்க.",
    "தொழில்ல எதிர்பார்த்த லாபம் கிடைக்கும், புது ஆர்டர் வரும்.",
    "வாய் வார்த்தைல கவனம் தேவை, தேவையில்லாம பேச வேண்டாம்.",
    "இன்னிக்கு நாள் முழுக்க சுறுசுறுப்பா இருப்பீங்க."
  ];

  // Pick one based on date (Deterministic per day)
  const dayIndex = now.getDate() % predictions.length;
  dailyHoroscope = {
    date: dateStr,
    content: predictions[dayIndex]
  };

  return dailyHoroscope.content;
}

// Init on start
generateTamilHoroscope();

// --- Endpoints ---



// Astrologer List API (Used by Mobile App)
app.get('/api/astrology/astrologers', async (req, res) => {
  try {
    const formatted = await getFormattedAstrologers(SERVER_URL);
    res.json({ ok: true, astrologers: formatted });
  } catch (err) {
    console.error('Error fetching astrologers:', err);
    res.status(500).json({ ok: false, error: err.message });
  }
});

// --- Get Astrologer Session History ---
app.get('/api/astrology/history/:userId', async (req, res) => {
  try {
    const { userId } = req.params;
    // Find sessions where this user was either the client or the astrologer
    const sessions = await Session.find({
      $or: [
        { astrologerId: userId },
        { clientId: userId },
        { fromUserId: userId },
        { toUserId: userId }
      ],
      status: 'ended'
    })
      .sort({ actualBillingStart: -1, startTime: -1 })
      .limit(50)

      .lean();

    const populatedSessions = await Promise.all(sessions.map(async (s) => {
      const cId = s.clientId || s.fromUserId;
      const aId = s.astrologerId || s.toUserId;
      const [client, astro] = await Promise.all([
        User.findOne({ userId: cId }).select('name').lean(),
        User.findOne({ userId: aId }).select('name').lean()
      ]);
      return {
        ...s,
        clientName: client ? client.name : 'Unknown Client',
        astrologerName: astro ? astro.name : 'Unknown Astrologer'
      };
    }));


    res.json({ ok: true, sessions: populatedSessions });
  } catch (err) {
    console.error('History API error:', err);
    res.status(500).json({ ok: false, error: err.message });
  }
});

// --- Register Device (FCM Token) ---
app.post('/register', async (req, res) => {
  try {
    const { userId, fcmToken } = req.body;
    if (!userId || !fcmToken) {
      return res.status(400).json({ success: false, error: 'Missing fields' });
    }

    const user = await User.findOne({ userId });
    if (user) {
      user.fcmToken = fcmToken;
      await user.save();
      console.log(`[FCM] Device registered for ${user.name} (${userId})`);
      res.json({ success: true, message: 'Device registered' });
    } else {
      res.status(404).json({ success: false, error: 'User not found' });
    }
  } catch (error) {
    console.error('Registration Error:', error);
    res.status(500).json({ success: false, error: error.message });
  }
});

// Academy Admin APIs



// Daily Horoscope API
app.get('/api/daily-horoscope', async (req, res) => {
  try {
    const today = DateTime.now().setZone('Asia/Kolkata').toFormat('yyyy-MM-dd');
    const data = await fetchDailyHoroscope(today);
    if (data && data.length > 0) {
      // Pick the first rasi (Mesham) as a generic forecast for the home screen
      res.json({ ok: true, content: data[0].forecast_ta });
    } else {
      const content = generateTamilHoroscope();
      res.json({ ok: true, content });
    }
  } catch (err) {
    console.error('Error in /api/daily-horoscope:', err);
    const content = generateTamilHoroscope();
    res.json({ ok: true, content });
  }
});

// Academy Videos API
app.get('/api/academy/videos', async (req, res) => {
  try {
    let videos = await AcademyVideo.find().sort({ createdAt: -1 });
    if (videos.length === 0) {
      // Return some dummy videos if none exist
      videos = [
        { title: "Introduction to Astrology", youtubeUrl: "https://www.youtube.com/watch?v=kYI9W5yisCc", category: "Basics" },
        { title: "Planetary Positions", youtubeUrl: "https://www.youtube.com/watch?v=FjI1XwHhK_4", category: "Intermediate" },
        { title: "Daily Prediction Guide", youtubeUrl: "https://www.youtube.com/watch?v=BvRE0mD6uA0", category: "General" }
      ];
    }
    res.json({ ok: true, videos });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// --- Banner APIs (Admin & App) ---

// Get Active Banners (Public)
app.get('/api/home/banners', async (req, res) => {
  try {
    const banners = await Banner.find({
      isActive: true,
      $or: [
        { expiryDate: { $gt: new Date() } },
        { expiryDate: null }
      ]
    }).sort({ order: 1 });
    // Fallback if no banners in DB
    if (banners.length === 0) {
      return res.json({
        ok: true,
        data: [
          { id: '1', _id: '1', imageUrl: "https://images.unsplash.com/photo-1532983330958-4b32bb9bb078?q=80&w=1200", title: "Premium Consultation", subtitle: "50% Off Today", ctaText: "Book Now" },
          { id: '2', _id: '2', imageUrl: "https://images.unsplash.com/photo-1516589174184-c68d8e01d300?q=80&w=1200", title: "Find Your Soulmate", subtitle: "Vedic Compatibility", ctaText: "Check Match" },
          { id: '3', _id: '3', imageUrl: "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?q=80&w=1200", title: "Career Guidance", subtitle: "Success Ahead", ctaText: "View Path" }
        ]
      });
    }
    const formattedBanners = banners.map(b => ({
      ...b.toObject ? b.toObject() : b,
      imageUrl: formatImageUrl(b.imageUrl, 'Banner')
    }));
    res.json({ ok: true, data: formattedBanners });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// Admin: Get All Banners

// Admin: Create Banner

// Admin: Update Banner

// Admin: Delete Banner

// 12 Rasi Horoscope API
app.get('/api/horoscope/rasi', (req, res) => {
  const raliList = [
    { id: 1, name: "Mesham", name_tamil: "மேஷம்", icon: "aries", prediction: "இன்று நீங்கள் எதிலும் நிதானத்துடன் செயல்பட வேண்டும். குடும்பத்தில் மகிழ்ச்சி நிலவும்." },
    { id: 2, name: "Rishabam", name_tamil: "ரிஷபம்", icon: "taurus", prediction: "தொழில் வியாபாரத்தில் நல்ல லாபம் கிடைக்கும். உறவினர்கள் வருகை இருக்கும்." },
    { id: 3, name: "Mithunam", name_tamil: "மிதுனம்", icon: "gemini", prediction: "எதிர்பார்த்த உதவிகள் தக்க சமயத்தில் கிடைக்கும். சுப காரிய முயற்சிகள் கைகூடும்." },
    { id: 4, name: "Kadagam", name_tamil: "கடகம்", icon: "cancer", prediction: "உடல் ஆரோக்கியத்தில் கவனம் தேவை. பயணங்களில் எச்சரிக்கை அவசியம்." },
    { id: 5, name: "Simmam", name_tamil: "சிம்மம்", icon: "leo", prediction: "நண்பர்கள் மூலம் ஆதாயம் உண்டாகும். நினைத்த காரியம் நிறைவேறும்." },
    { id: 6, name: "Kanni", name_tamil: "கன்னி", icon: "virgo", prediction: "வேலை சுமை அதிகரிக்கலாம். சக ஊழியர்களிடம் அனுசரித்து செல்வது நல்லது." },
    { id: 7, name: "Thulaam", name_tamil: "துலாம்", icon: "libra", prediction: "பண வரவு தாராளமாக இருக்கும். புதிய பொருட்கள் வாங்குவீர்கள்." },
    { id: 8, name: "Viruchigam", name_tamil: "விருச்சிகம்", icon: "scorpio", prediction: "வாழ்க்கை துணையின் ஆதரவு கிடைக்கும். ஆன்மீக நாட்டம் அதிகரிக்கும்." },
    { id: 9, name: "Dhanusu", name_tamil: "தனுசு", icon: "sagittarius", prediction: "பிள்ளைகள் வழியில் நல்ல செய்தி வரும். சமூகத்தில் மதிப்பு உயரும்." },
    { id: 10, name: "Magaram", name_tamil: "மகரம்", icon: "capricorn", prediction: "வீண் செலவுகள் ஏற்படும். ஆடம்பர செலவுகளை குறைப்பது நல்லது." },
    { id: 11, name: "Kumbam", name_tamil: "கும்பம்", icon: "aquarius", prediction: "திறமைக்கு ஏற்ற அங்கீகாரம் கிடைக்கும். மேலதிகாரிகளின் பாராட்டு கிடைக்கும்." },
    { id: 12, name: "Meenam", name_tamil: "மீனம்", icon: "pisces", prediction: "உடல் சோர்வு நீங்கி புத்துணர்ச்சி பெறுவீர்கள். கணவன் மனைவி அன்யோன்யம் கூடும்." }
  ];
  res.json({ ok: true, data: raliList });
});

// ==========================================
// USER INTAKE APIs (Required by Android App)
// ==========================================

// Get user intake details
app.get('/api/user/:userId/intake', async (req, res) => {
  try {
    const { userId } = req.params;
    const user = await User.findOne({ userId });

    if (!user) {
      return res.status(404).json({ success: false, error: 'User not found' });
    }

    res.json({
      success: true,
      data: user.intakeDetails || null
    });
  } catch (err) {
    console.error('Get intake error:', err);
    res.status(500).json({ success: false, error: err.message });
  }
});

// Save user intake details
app.post('/api/user/intake', async (req, res) => {
  try {
    const { userId, ...intakeData } = req.body;

    if (!userId) {
      return res.status(400).json({ success: false, error: 'userId required' });
    }

    const user = await User.findOneAndUpdate(
      { userId },
      { $set: { intakeDetails: intakeData } },
      { new: true }
    );

    if (!user) {
      return res.status(404).json({ success: false, error: 'User not found' });
    }

    res.json({ success: true, data: user.intakeDetails });
  } catch (err) {
    console.error('Save intake error:', err);
    res.status(500).json({ success: false, error: err.message });
  }
});

// ==========================================
// CHAT HISTORY API (Required by Android App)
// ==========================================

// ==========================================
// LEGACY CHART APIs (Redirect to rasi-eng)
// ==========================================

// Birth chart - proxy to rasi-eng/charts/full

// Match porutham

// OTP Send (Mock)

// OTP Verify (DB Lookup)

// --- Referral Apply Endpoint ---
app.post('/api/referral/apply', async (req, res) => {
  try {
    const { userId, referralCode } = req.body;
    const user = await User.findOne({ userId });

    if (!user) return res.json({ ok: false, error: 'User not found' });
    if (!user.isNewUser) return res.json({ ok: false, error: 'Referral can only be applied by new users' });

    // Find the referrer
    const referrer = await User.findOne({ referralCode: referralCode.toUpperCase() });
    if (!referrer) return res.json({ ok: false, error: 'Invalid referral code' });
    if (referrer.userId === userId) return res.json({ ok: false, error: 'Cannot refer yourself' });

    // Reward Referrer (User A)
    const referrerBonus = 20;
    referrer.walletBalance += referrerBonus;
    referrer.referralCount += 1;
    await referrer.save();

    // Reward New User (User B)
    const newUserBonus = 10;
    user.walletBalance += newUserBonus;
    user.referredBy = referrer.userId;
    user.isNewUser = false; // Mark as processed
    await user.save();

    // Log in Ledger (Referrer)
    await BillingLedger.create({
      billingId: crypto.randomUUID(),
      sessionId: 'REFERRAL_REWARD',
      minuteIndex: 0,
      chargedToClient: 0,
      creditedToAstrologer: referrerBonus,
      reason: 'referral',
      createdAt: new Date()
    });

    res.json({
      ok: true,
      bonusAmount: newUserBonus,
      newBalance: user.walletBalance,
      message: 'Referral applied successfully!'
    });

  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});

// Skip referral popup
app.post('/api/referral/skip', async (req, res) => {
  try {
    const { userId } = req.body;
    await User.updateOne({ userId }, { isNewUser: false });
    res.json({ ok: true });
  } catch (err) {
    res.status(500).json({ ok: false, error: err.message });
  }
});


// ===== ACCOUNT DELETION REQUEST API =====
app.post('/api/delete-account-request', async (req, res) => {
  try {
    const { user_identifier, reason } = req.body;

    if (!user_identifier) {
      return res.json({ ok: false, error: 'Email or phone number is required' });
    }

    // Check if user exists in database
    let user = null;
    let userId = null;

    // Try to find by phone
    if (/^\d+$/.test(user_identifier)) {
      user = await User.findOne({ phone: user_identifier });
    } else {
      // Try to find by email (if email field exists in your schema)
      user = await User.findOne({ email: user_identifier });
    }

    if (user) {
      userId = user.userId;
    }

    // Check if there's already a pending request
    const existingRequest = await AccountDeletionRequest.findOne({
      userIdentifier: user_identifier,
      status: 'pending'
    });

    if (existingRequest) {
      return res.json({
        ok: false,
        error: 'A deletion request for this account is already pending'
      });
    }

    // Create deletion request
    const requestId = crypto.randomUUID();
    const deletionRequest = await AccountDeletionRequest.create({
      requestId,
      userIdentifier: user_identifier,
      userId: userId,
      reason: reason || 'No reason provided',
      status: 'pending',
      requestedAt: new Date()
    });

    console.log(`[Account Deletion] Request created: ${requestId} for ${user_identifier}`);

    res.json({
      ok: true,
      message: 'Account deletion request submitted successfully',
      requestId: requestId
    });

  } catch (error) {
    console.error('[Account Deletion] Error:', error);
    res.status(500).json({ ok: false, error: 'Failed to submit deletion request' });
  }
});

// ===== ADMIN: GET ACCOUNT DELETION REQUESTS =====

// ===== ADMIN: PROCESS ACCOUNT DELETION REQUEST =====

// ===== NATIVE CALL ACCEPT API =====
// Called from Android when notification Accept/Reject is clicked
// This allows accepting calls WITHOUT WebView being loaded
app.post('/api/native/accept-call', async (req, res) => {
  try {
    const { sessionId, userId, accept, callType } = req.body;

    console.log(`[Native API] Accept Call - Session: ${sessionId}, User: ${userId}, Accept: ${accept}`);

    if (!sessionId || !userId) {
      return res.json({ ok: false, error: 'Missing sessionId or userId' });
    }

    // Find the session
    let session = activeSessions.get(sessionId);
    let fromUserId = null;
    let sessionType = callType || 'audio';

    if (session) {
      // Session found in memory
      fromUserId = session.users.find(u => u !== userId);
      sessionType = session.type || callType || 'audio';
    } else {
      // Try DB
      const dbSession = await Session.findOne({ sessionId });
      if (dbSession) {
        fromUserId = dbSession.fromUserId;
        sessionType = dbSession.type || callType || 'audio';
      }
    }

    if (!fromUserId) {
      console.log(`[Native API] Session not found: ${sessionId}`);
      return res.json({ ok: false, error: 'Session not found or expired' });
    }

    const callerSocketId = userSockets.get(fromUserId);

    if (accept) {
      // Accept the call - notify caller via socket
      if (callerSocketId) {
        io.to(callerSocketId).emit('session-answered', {
          sessionId,
          fromUserId: userId,
          type: sessionType,
          accept: true
        });
        console.log(`[Native API] ✅ Call ACCEPTED - Notified caller: ${fromUserId}`);
      } else {
        console.log(`[Native API] Caller not connected: ${fromUserId}`);
      }

      return res.json({
        ok: true,
        fromUserId,
        callType: sessionType,
        message: 'Call accepted successfully'
      });

    } else {
      // Reject the call
      if (callerSocketId) {
        io.to(callerSocketId).emit('session-answered', {
          sessionId,
          fromUserId: userId,
          accept: false
        });
        console.log(`[Native API] ❌ Call REJECTED - Notified caller: ${fromUserId}`);
      }

      // End the session
      endSessionRecord(sessionId);

      return res.json({ ok: true, message: 'Call rejected' });
    }

  } catch (err) {
    console.error('[Native API] Error:', err);
    res.status(500).json({ ok: false, error: 'Server error' });
  }
});

function startSessionRecord(sessionId, type, u1, u2) {
  activeSessions.set(sessionId, {
    type,
    users: [u1, u2],
    startedAt: Date.now(),
  });
  userActiveSession.set(u1, sessionId);
  userActiveSession.set(u2, sessionId);

  // Mark astrologer as busy
  User.updateMany({ userId: { $in: [u1, u2] }, role: 'astrologer' }, { isBusy: true })
    .then(() => broadcastAstroUpdate())
    .catch(e => console.error('Error marking busy:', e));
}


function getOtherUserIdFromSession(sessionId, userId) {
  const s = activeSessions.get(sessionId);
  if (!s) return null;
  const [u1, u2] = s.users;
  return u1 === userId ? u2 : u2 === userId ? u1 : null;
}

// Helper: End Session & Calculate Wallet
async function endSessionRecord(sessionId) {
  const s = activeSessions.get(sessionId);
  if (!s) return;

  const endTime = Date.now();
  // Phase 1/2: Use tracked billable seconds if available
  const billableSeconds = s.elapsedBillableSeconds || 0;

  // Update Session in DB
  await Session.updateOne({ sessionId }, {
    endTime,
    duration: billableSeconds * 1000,
    totalEarned: s.totalEarned || 0,
    totalCharged: s.totalDeducted || 0,
    status: 'ended'
  });


  // Update PairMonth Cumulative Seconds (Phase 4)
  if (s.pairMonthId) {
    await PairMonth.updateOne(
      { _id: s.pairMonthId },
      { $inc: { slabLockedAt: billableSeconds } }
    );
  }

  // Phase 3: Early Exit Handling (< 60s)
  if (billableSeconds > 0 && billableSeconds < 60) {
    console.log(`Session ${sessionId}: Early exit at ${billableSeconds}s. Charging full first minute.`);
    await processBillingCharge(sessionId, billableSeconds, 1, 'early_exit');
  }

  // Phase 5: Round-Up Billing (Partial Minute at End)
  else if (billableSeconds > 60) {
    const lastBilled = s.lastBilledMinute || 1;
    const totalMinutes = Math.ceil(billableSeconds / 60);

    if (totalMinutes > lastBilled) {
      console.log(`Session ${sessionId}: Finalizing billing for partial minutes ${lastBilled + 1} to ${totalMinutes}`);

      for (let i = lastBilled + 1; i <= totalMinutes; i++) {
        // User Rule: If it's the last minute of the session AND it has extra seconds, it's a fraction (100% Admin)
        const isFraction = (i === totalMinutes && (billableSeconds % 60) !== 0);
        const billingType = isFraction ? 'fraction' : 'slab';

        await processBillingCharge(sessionId, 60, i, billingType);
      }

    }
  }

  // Cleanup active session finally
  activeSessions.delete(sessionId);
  if (s.users) {
    s.users.forEach((u) => {
      if (userActiveSession.get(u) === sessionId) {
        userActiveSession.delete(u);
      }
      // NEW: Clear any pending session disconnect timeouts for these users
      if (sessionDisconnectTimeouts.has(u)) {
        clearTimeout(sessionDisconnectTimeouts.get(u));
        sessionDisconnectTimeouts.delete(u);
      }
    });
  }

  // Notify with Summary
  const payload = {
    reason: 'ended',
    summary: {
      deducted: s.totalDeducted || 0,
      earned: s.totalEarned || 0,
      duration: billableSeconds
    }
  };

  if (s.clientId) io.to(s.clientId).emit('session-ended', payload);
  if (s.astrologerId) io.to(s.astrologerId).emit('session-ended', payload);

  // Mark astrologer as NOT busy (Wait for DB update before broadcast)
  User.updateMany({ userId: { $in: s.users }, role: 'astrologer' }, { isBusy: false })
    .then(() => broadcastAstroUpdate())
    .catch(e => console.error('Error clearing busy:', e));
}

// --- Phase 3: Billing Helper ---
// SLAB_RATES is now a let defined above with GlobalSettings loading logic

async function processBillingCharge(sessionId, durationSeconds, minuteIndex, type) {
  try {
    const session = await Session.findOne({ sessionId });
    if (!session) return;

    // Fetch Astrologer Price
    const astro = await User.findOne({ userId: session.astrologerId });
    if (!astro) return;

    const client = await User.findOne({ userId: session.clientId });
    if (!client) return;

    // Phase: Pricing Logic
    // Priority: Astro DB Price > Hardcoded fallback
    let pricePerMin = 10;
    if (astro.price && astro.price > 0) {
      pricePerMin = parseInt(astro.price);
    } else {
      // Fallback defaults
      if (session.type === 'audio') pricePerMin = 15;
      if (session.type === 'video') pricePerMin = 20;
    }

    console.log(`[Billing] Session ${sessionId} | Type: ${session.type} | Price: ${pricePerMin}/min | Minute: ${minuteIndex}`);

    let amountToCharge = 0;
    let adminShare = 0;
    let astroShare = 0;
    let reason = '';

    // Logic: First 60 Seconds (Admin Only)
    if (type === 'first_60_full') {
      amountToCharge = pricePerMin;
      adminShare = amountToCharge;
      astroShare = 0;
      reason = 'first_60';
    } else if (type === 'early_exit') {
      // User requested: Even if early exit (e.g. 30s), charge full minute (pricePerMin)
      amountToCharge = pricePerMin;
      adminShare = amountToCharge; // 100% to Admin
      astroShare = 0;
      reason = 'first_60_min_charge';

    } else if (type === 'slab') {
      // Standard Minute Billing
      const activeSess = activeSessions.get(sessionId);
      const currentSlab = activeSess?.currentSlab || 3;
      const rate = SLAB_RATES[currentSlab] || 0.30;

      amountToCharge = pricePerMin;
      astroShare = amountToCharge * rate;
      adminShare = amountToCharge - astroShare;
      reason = `slab_${currentSlab}`;

      console.log(`[Billing] Slab: ${currentSlab} | Rate: ${rate} | AstroShare: ${astroShare}`);
    } else if (type === 'fraction') {
      // User rule: Extra seconds (fractional minute) at the end
      // 100% of this charge goes to Admin, 0 to Astrologer.
      amountToCharge = pricePerMin;
      adminShare = amountToCharge;
      astroShare = 0;
      reason = 'fraction_roundup';
    } else {

      return;
    }

    // Deduct from Client (70/30 Rule)
    const totalToDeduct = amountToCharge;
    if (client.walletBalance >= totalToDeduct) {
      let mainDeduct = totalToDeduct * 0.7;
      let superDeduct = totalToDeduct * 0.3;

      // Rule: If super wallet has balance, use it for the 30%
      if (client.superWalletBalance > 0) {
        if (client.superWalletBalance >= superDeduct) {
          client.superWalletBalance -= superDeduct;
        } else {
          // Take what's available and shift rest to main
          const availableSuper = client.superWalletBalance;
          client.superWalletBalance = 0;
          mainDeduct += (superDeduct - availableSuper);
        }
      } else {
        // No super wallet balance, take 100% from main
        mainDeduct = totalToDeduct;
      }

      client.walletBalance -= mainDeduct;
      await client.save();

      // Credit Astrologer (if > 0)
      if (astroShare > 0) {
        astro.walletBalance += astroShare;
        astro.totalEarnings = (astro.totalEarnings || 0) + astroShare; // Phase 16
        await astro.save();
      }

      // Admin Share is just recorded in Ledger, or we could credit a SuperAdmin wallet.
      // Task says: "Deduct from client, credit 0 to astro, rest to Admin"

      // Create Ledger Entry
      await BillingLedger.create({
        billingId: crypto.randomUUID(),
        sessionId,
        minuteIndex,
        chargedToClient: amountToCharge,
        creditedToAstrologer: astroShare,
        adminAmount: adminShare,
        reason
      });

      // Track Session Totals
      const activeSess = activeSessions.get(sessionId);
      if (activeSess) {
        activeSess.totalDeducted = (activeSess.totalDeducted || 0) + amountToCharge;
        activeSess.totalEarned = (activeSess.totalEarned || 0) + astroShare;
      }

      console.log(`Billing: ${reason} | Charge: ${amountToCharge} | Admin: ${adminShare} | Astro: ${astroShare}`);

      // Notify Wallets
      const s1 = userSockets.get(client.userId);
      if (s1) io.to(s1).emit('wallet-update', { balance: client.walletBalance });

      const s2 = userSockets.get(astro.userId);
      if (s2) io.to(s2).emit('wallet-update', {
        balance: astro.walletBalance,
        totalEarnings: astro.totalEarnings || 0
      });

    } else {
      console.log(`Billing Failed: Insufficient funds for ${client.name}`);
      // Handle forced termination
      forceEndSession(sessionId, 'insufficient_funds');
    }

  } catch (e) {
    console.error('Billing Error:', e);
  }
}

function forceEndSession(sessionId, reason) {
  const session = activeSessions.get(sessionId);
  if (!session) return;

  console.log(`Force Ending Session ${sessionId} due to: ${reason}`);

  // Notify Users (With Summary)
  const clientSocketId = userSockets.get(session.clientId);
  const astroSocketId = userSockets.get(session.astrologerId);

  const payload = {
    reason,
    summary: {
      deducted: session.totalDeducted || 0,
      earned: session.totalEarned || 0,
      duration: session.elapsedBillableSeconds || 0
    }
  };

  if (clientSocketId) io.to(clientSocketId).emit('session-ended', payload);
  if (astroSocketId) io.to(astroSocketId).emit('session-ended', payload);

  // Cleanup Server State
  endSessionRecord(sessionId);
}

// ===== City Autocomplete API =====

// ===== Get City Timezone =====

// ===== Astrologer Registration REST (for Android App) =====

// ===== Socket.IO =====
io.on('connection', (socket) => {
  console.log('Socket connected:', socket.id);

  // Mount specialized handlers
  callHandler(io, socket, SERVER_URL, broadcastAstroUpdate);

  // --- Explicit End Session (Stability Guard for Chat/Calls) ---
  socket.on('end-session', async (data) => {
    try {
      const { sessionId } = data || {};
      if (sessionId) {
        console.log(`[Socket] end-session received for ${sessionId}`);
        billingService.endSessionRecord(sessionId, broadcastAstroUpdate);
      }
    } catch (err) {
      console.error('end-session error', err);
    }
  });

  // --- Register user ---
  // --- Register New Astrologer ---
  socket.on('submit-astro-registration', async (data, cb) => {
    try {
      const {
        realName,
        displayName,
        gender,
        dob,
        tob,
        pob,
        cellNumber1,
        cellNumber2,
        whatsAppNumber,
        address,
        aadharNumber,
        panNumber,
        astrologyExperience,
        profession,
        bankDetails,
        upiId,
        upiNumber
      } = data;

      // Basic Validation
      if (!cellNumber1 || !realName) {
        return cb({ ok: false, error: 'Mandatory fields missing' });
      }

      // Check if phone already exists
      const existing = await User.findOne({ phone: cellNumber1 });
      if (existing) {
        return cb({ ok: false, error: 'Phone number already registered' });
      }

      const userId = 'ASTRO_' + Date.now() + Math.floor(Math.random() * 1000);
      const newUser = new User({
        userId,
        phone: cellNumber1,
        name: displayName || realName,
        realName,
        gender,
        birthDetails: { dob, tob, pob, lat: 0, lon: 0 }, // Using lat/0 lon/0 as placeholder
        cellNumber2,
        whatsAppNumber,
        address,
        aadharNumber,
        panNumber,
        astrologyExperience,
        profession,
        bankDetails,
        upiId,
        upiNumber,
        role: 'astrologer',
        approvalStatus: 'pending', // Explicit pending status
        isVerified: false,
        documentStatus: 'processing',
        walletBalance: 0,
        image: 'images/default-user.png' // Configurable later
      });

      await newUser.save();
      console.log('New Astrologer Registration:', newUser.name, newUser.userId);

      // Notify Super Admin if online
      io.to('superadmin').emit('admin-notification', { text: `New Astrologer Request: ${newUser.name}` });

      cb({ ok: true });
    } catch (e) {
      console.error('Registration Error:', e);
      cb({ ok: false, error: 'Server Error' });
    }
  });

  // --- Register user ---
  socket.on('register', (data, cb) => {
    try {
      const { name, phone, existingUserId } = data || {};
      const userId = data.userId || socketToUser.get(socket.id);

      const query = phone ? { phone } : (userId ? { userId } : null);

      if (!query) {
        if (typeof cb === 'function') cb({ ok: false, error: 'No identifier provided' });
        return;
      }

      User.findOne(query).then(user => {
        if (!user) {
          if (typeof cb === 'function') cb({ ok: false, error: 'User not found' });
          return;
        }

        const userId = user.userId;
        userSockets.set(userId, socket.id);
        socketToUser.set(socket.id, userId);

        if (typeof cb === 'function') cb({
          ok: true,
          userId: user.userId,
          role: user.role,
          name: user.name,
          walletBalance: user.walletBalance,
          superWalletBalance: user.superWalletBalance || 0,
          totalEarnings: user.totalEarnings || 0
        });
        console.log(`User registered: ${user.name} (${user.role})`);

        // Cancel pending SESSION timeout (For ALL users - Client or Astrologer)
        if (sessionDisconnectTimeouts.has(userId)) {
          clearTimeout(sessionDisconnectTimeouts.get(userId));
          sessionDisconnectTimeouts.delete(userId);
          console.log(`[Session] Cancelled disconnect timeout for ${user.name} (reconnected in time!)`);

          // Re-join the user to the socket room?
          // If we depend on socket.id for targeting, we update userSockets above so it should be fine.
          // However, if we used rooms for sessions, we'd need to re-join.
          // Current logic uses userSockets.get(userId) to target, so updating the map is sufficient.
        }

        // If astro, broadcast status
        if (user.role === 'astrologer') {
          // Cancel pending offline timeout (if any - though we will remove the timeout logic)
          if (offlineTimeouts.has(userId)) {
            clearTimeout(offlineTimeouts.get(userId));
            offlineTimeouts.delete(userId);
          }

          // Re-sync online status if isAvailable is true
          if (user.isAvailable) {
            user.isOnline = true;
            user.save().then(() => broadcastAstroUpdate());
          } else {
            broadcastAstroUpdate();
          }
        }
        // If superadmin, join room
        if (user.role === 'superadmin') {
          socket.join('superadmin');
        }

        // NEW: All users join a room with their userId for reliable messaging
        socket.join(userId);
        console.log(`[Socket] ${user.name} joined room: ${userId}`);
      });
    } catch (err) {
      console.error('register error', err);
      if (typeof cb === 'function') cb({ ok: false, error: 'Internal error' });
    }
  });

  // --- Logout (Explicit) ---
  socket.on('logout', async (cb) => {
    try {
      const userId = socketToUser.get(socket.id);
      if (!userId) return cb && cb({ ok: false, error: 'Not logged in' });

      console.log(`[Presence] User ${userId} requested explicit logout.`);
      
      const user = await User.findOne({ userId });
      if (user && user.role === 'astrologer') {
        user.isOnline = false;
        user.isAvailable = false;
        user.isChatOnline = false;
        user.isAudioOnline = false;
        user.isVideoOnline = false;
        await user.save();
        broadcastAstroUpdate();
      }

      // Clear from maps
      userSockets.delete(userId);
      socketToUser.delete(socket.id);
      
      if (typeof cb === 'function') cb({ ok: true });
    } catch (err) {
      console.error('logout error', err);
      if (typeof cb === 'function') cb({ ok: false, error: 'Internal error' });
    }
  });

  // --- Rejoin Session (for reconnecting after background/edit) ---
  socket.on('rejoin-session', (data) => {
    try {
      const { sessionId } = data || {};
      const userId = socketToUser.get(socket.id);

      if (sessionId && userId) {
        socket.join(sessionId);
        console.log(`[Socket] User ${userId} rejoined session: ${sessionId}`);

        // Notify the other party that user has reconnected
        socket.to(sessionId).emit('peer-reconnected', { userId });
      }
    } catch (err) {
      console.error('rejoin-session error', err);
    }
  });


  // --- Get Astrologers List ---
  socket.on('get-astrologers', async (cb) => {
    const formatted = await getFormattedAstrologers(SERVER_URL);
    if (typeof cb === 'function') cb({ astrologers: formatted });
  });

  // --- Toggle Status (Astrologer Only) ---
  socket.on('toggle-status', async (data) => {
    const userId = data.userId || socketToUser.get(socket.id);
    if (!userId) return;

    try {
      const update = {};
      if (data.type === 'chat') update.isChatOnline = !!data.online;
      if (data.type === 'audio') update.isAudioOnline = !!data.online;
      if (data.type === 'video') update.isVideoOnline = !!data.online;

      // We first get the user to calculate global isOnline
      let user = await User.findOne({ userId });
      if (!user || user.role !== 'astrologer') return;
      if (user.approvalStatus !== 'approved') return;

      Object.assign(user, update);
      user.isOnline = user.isChatOnline || user.isAudioOnline || user.isVideoOnline;
      user.isAvailable = user.isOnline; // Sync isAvailable with manual toggle
      user.lastSeen = new Date();
      await user.save();
      broadcastAstroUpdate();
      console.log(`[Presence] ${user.name} toggled ${data.type}: ${data.online}`);
    } catch (e) { console.error(e); }
  });

  // --- Update Service Status (Individual Toggles from Android) ---
  socket.on('update-service-status', async (data) => {
    const userId = data.userId || socketToUser.get(socket.id);
    if (!userId) return;

    try {
      const update = {};
      const isEnabled = !!data.isEnabled;

      if (data.service === 'chat') update.isChatOnline = isEnabled;
      if (data.service === 'call') update.isAudioOnline = isEnabled; // 'call' maps to 'audio'
      if (data.service === 'video') update.isVideoOnline = isEnabled;

      let user = await User.findOne({ userId });
      if (user) {
        Object.assign(user, update);
        // Manual Toggle Rule: isAvailable is the master status
        user.isOnline = user.isAvailable;
        user.lastSeen = new Date();
        await user.save();

        broadcastAstroUpdate();
        console.log(`[Service Status] ${user.name} updated ${data.service}: ${isEnabled}`);
      }
    } catch (e) { console.error('update-service-status error:', e); }
  });

  // --- Mobile App Specific Status Update ---
  socket.on('update-status', async (data) => {
    const userId = data.userId || socketToUser.get(socket.id);
    if (!userId) return;

    try {
      const isOnline = !!data.isOnline;
      // Mobile toggle sets ALL statuses
      let user = await User.findOne({ userId });
      if (user) {
        user.isChatOnline = isOnline;
        user.isAudioOnline = isOnline;
        user.isVideoOnline = isOnline;
        user.isOnline = isOnline;
        user.isAvailable = isOnline;
        user.lastSeen = new Date();
        await user.save();
        broadcastAstroUpdate();
        console.log(`[Presence Mobile] ${user.name} updated status: ${isOnline}`);
      }
    } catch (e) { console.error(e); }
  });

  // --- App Lifecycle: Background ---
  socket.on('app-background', async () => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return;

    try {
      const user = await User.findOne({ userId });
      if (user && user.role === 'astrologer') {
        user.lastSeen = new Date();
        // DON'T mark offline - just update lastSeen
        await user.save();
        console.log(`[Presence] ${user.name} went to background (lastSeen updated)`);
      }
    } catch (e) { console.error('[Presence] app-background error:', e); }
  });

  // --- App Lifecycle: Foreground ---
  socket.on('app-foreground', async () => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return;

    try {
      const user = await User.findOne({ userId });
      if (user && user.role === 'astrologer') {
        user.lastSeen = new Date();

        // Restore status from saved state if available
        const saved = savedAstroStatus.get(userId);
        if (saved) {
          user.isChatOnline = saved.chat;
          user.isAudioOnline = saved.audio;
          user.isVideoOnline = saved.video;
          user.isOnline = saved.chat || saved.audio || saved.video;
          user.isAvailable = user.isOnline;
          savedAstroStatus.delete(userId);
          console.log(`[Presence] ${user.name} returned to foreground - status restored`);
        } else {
          console.log(`[Presence] ${user.name} returned to foreground`);
        }

        await user.save();
        broadcastAstroUpdate();
      }
    } catch (e) { console.error('[Presence] app-foreground error:', e); }
  });

  // --- Update Profile ---
  socket.on('update-profile', async (data, cb) => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return cb({ ok: false, error: 'Not logged in' });

    try {
      const user = await User.findOne({ userId });
      if (user) {
        if (data.price) user.price = parseInt(data.price);
        if (data.experience) user.experience = parseInt(data.experience);
        if (data.image) user.image = data.image; // URL
        if (data.birthDetails) {
          user.birthDetails = { ...user.birthDetails, ...data.birthDetails };
        }

        await user.save();

        if (user.role === 'astrologer') broadcastAstroUpdate();
        cb({ ok: true, user });
      } else {
        cb({ ok: false, error: 'User not found' });
      }
    } catch (e) {
      console.error('Update Profile Error', e);
      cb({ ok: false, error: 'Internal Error' });
    }
  });

  // --- Session request (chat / audio / video) ---


  // --- Chat message (text / audio / file) ---
  socket.on('chat-message', async (data) => {
    try {
      const { toUserId, sessionId, content, timestamp, messageId } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId || !content || !messageId) return;

      socket.emit('message-status', {
        messageId,
        status: 'sent',
      });

      // Save to DB (Async)
      ChatMessage.create({
        messageId,
        sessionId,
        fromUserId,
        toUserId,
        text: content.text,
        timestamp: timestamp || Date.now()
      }).catch(e => console.error('ChatSave Error', e));

      // Emit to Room (userId) - works even after reconnect
      io.to(toUserId).emit('chat-message', {
        fromUserId,
        content,
        sessionId: sessionId || null,
        timestamp: timestamp || Date.now(),
        messageId,
      });

      // ALWAYS send FCM push for background delivery
      // App may be in background but socket still connected
      // FCM ensures message is delivered even if app is killed
      sendChatMessagePush(toUserId, fromUserId, content.text || 'New message', sessionId, messageId);
    } catch (err) {
      console.error('chat-message error', err);
    }
  });

  // --- Helper: Send Chat Message Push (for background messages) ---
  async function sendChatMessagePush(toUserId, fromUserId, messageText, sessionId, messageId) {
    try {
      const toUser = await User.findOne({ userId: toUserId });
      const fromUser = await User.findOne({ userId: fromUserId });

      if (toUser && toUser.fcmToken) {
        const payload = {
          type: 'CHAT_MESSAGE',
          sessionId: sessionId || '',
          callerName: fromUser?.name || 'Astrologer',
          callerId: fromUserId,
          text: (messageText || 'New message').substring(0, 200),
          messageId: messageId || Date.now().toString(),
          timestamp: Date.now().toString()
        };

        // Data-only message for background handling
        await sendFcmV1Push(toUser.fcmToken, payload, null, toUserId);
        console.log(`Chat push sent to ${toUserId} from ${fromUserId}`);
      }
    } catch (e) {
      console.error('Chat Message Push Error:', e);
    }
  }


  // --- Helper: Send Chat Push ---
  async function sendChatPush(toUserId, fromUserId, messageText, sessionId) {
    try {
      const toUser = await User.findOne({ userId: toUserId });
      const fromUser = await User.findOne({ userId: fromUserId });

      if (toUser && toUser.fcmToken) {
        const payload = {
          type: 'INCOMING_CALL',
          callType: 'chat',
          sessionId: sessionId || `chat_${Date.now()}`,
          callerName: fromUser?.name || 'Client',
          callerId: fromUserId,
          body: messageText.substring(0, 100),
          timestamp: Date.now().toString()
        };

        const notification = {
          title: `Message from ${fromUser?.name}`,
          body: messageText.substring(0, 100)
        };

        await sendFcmV1Push(toUser.fcmToken, payload, notification, toUserId);
      }
    } catch (e) { console.error('Chat Push Error:', e); }
  }


  // --- message-status (from Android) - handles both delivered and read ---
  socket.on('message-status', (data) => {
    try {
      const { toUserId, messageId, status } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId || !messageId || !status) return;

      console.log(`[MessageStatus] ${status} from ${fromUserId} to ${toUserId} msgId=${messageId}`);

      // Emit to sender (toUserId is the original sender)
      io.to(toUserId).emit('message-status', {
        messageId,
        status, // 'delivered' or 'read'
      });
    } catch (err) { console.error('message-status error', err); }
  });

  // --- Receiver: delivered ack (legacy) ---
  socket.on('message-delivered', (data) => {
    try {
      const { toUserId, messageId } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId || !messageId) return;

      // Emit to userId room (not socketId) - works after reconnect
      io.to(toUserId).emit('message-status', {
        messageId,
        status: 'delivered',
      });
    } catch (err) { console.error(err); }
  });

  // --- Receiver: read ack ---
  socket.on('message-read', (data) => {
    try {
      const { toUserId, messageId } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId || !messageId) return;

      // Emit to userId room (not socketId) - works after reconnect
      io.to(toUserId).emit('message-status', {
        messageId,
        status: 'read',
      });
    } catch (err) { console.error(err); }
  });

  // --- Typing indicator ---
  socket.on('typing', (data) => {
    try {
      const { toUserId, isTyping } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId) return;

      const targetSocketId = userSockets.get(toUserId);
      if (!targetSocketId) return;

      io.to(targetSocketId).emit('typing', {
        fromUserId,
        isTyping: !!isTyping,
      });
    } catch (err) { console.error('typing error', err); }
  });



  // --- Phase 2: Session Timer Engine ---
  if (global.tickInterval) clearInterval(global.tickInterval);
  global.tickInterval = setInterval(tickSessions, 1000);

  // Phase 4 Helper
  function getSlabBySeconds(seconds) {
    if (seconds <= 300) return 1;
    if (seconds <= 600) return 2;
    if (seconds <= 900) return 3;
    if (seconds <= 1200) return 4;
    return 4; // Max slab 4+
  }

  function tickSessions() {
    const now = Date.now();
    if (Math.floor(now / 1000) % 10 === 0) {
      console.log(`[Ticker] Active: ${activeSessions.size}`);
      for (const [sid, s] of activeSessions) {
        console.log(`  - ${sid}: Billable=${s.elapsedBillableSeconds}, Start=${s.actualBillingStart}, TotalDed=${s.totalDeducted}`);
      }
    }
    for (const [sessionId, session] of activeSessions) {
      // 0. GHOST SESSION CLEANUP: If session hasn't started billing for > 5 mins, clean it up
      const SESSION_TIMEOUT = 5 * 60 * 1000; // 5 minutes
      if (!session.actualBillingStart && (now - session.startedAt > SESSION_TIMEOUT)) {
        console.log(`[Ticker] Cleaning up ghost session ${sessionId} (timeout)`);
        activeSessions.delete(sessionId);
        if (session.users) {
          session.users.forEach(u => {
            if (userActiveSession.get(u) === sessionId) userActiveSession.delete(u);
          });
        }
        continue;
      }

      // 1. Check if Billing Started
      if (!session.actualBillingStart || now < session.actualBillingStart) continue;

      // 2. Check Connections (BOTH must be connected)
      const clientSocketId = userSockets.get(session.clientId);
      const astroSocketId = userSockets.get(session.astrologerId);

      const isClientConnected = !!clientSocketId;
      const isAstroConnected = !!astroSocketId;

      if (isClientConnected && isAstroConnected) {
        session.elapsedBillableSeconds++;

        // DEBUG LOGGING - Throttle to every 10s or 1 minute in production?
        // For now keep it but it adds up in logs.
        if (session.elapsedBillableSeconds % 30 === 0) {
          console.log(`[${sessionId}] Tick: ${session.elapsedBillableSeconds}s, TotalDed: ${session.totalDeducted}`);
        }

        // Phase 3: First Minute Check (at 60s exactly)
        if (session.elapsedBillableSeconds === 60) {
          console.log(`Session ${sessionId}: First 60s completed.`);
          processBillingCharge(sessionId, 60, 1, 'first_60_full');
        }

        // Phase 4: Check Slab Upgrade
        if (session.pairMonthId) {
          const totalSeconds = (session.initialPairSeconds || 0) + session.elapsedBillableSeconds;
          const calculatedSlab = getSlabBySeconds(totalSeconds);
          const effectiveSlab = Math.max(calculatedSlab, session.currentSlab || 0);

          if (effectiveSlab > session.currentSlab) {
            console.log(`Session ${sessionId}: Slab Upgraded ${session.currentSlab} -> ${effectiveSlab}`);
            session.currentSlab = effectiveSlab;
            PairMonth.updateOne({ _id: session.pairMonthId }, { currentSlab: effectiveSlab }).exec();
          }
        }

        // Phase 5: Post-First-Minute Billing
        if (session.elapsedBillableSeconds > 60) {
          const eligibleSeconds = session.elapsedBillableSeconds - 60;
          const eligibleMinutes = Math.floor(eligibleSeconds / 60);
          const totalShouldBeBilled = 1 + eligibleMinutes;

          if (totalShouldBeBilled > session.lastBilledMinute) {
            console.log(`Session ${sessionId}: Minute ${totalShouldBeBilled} reached.`);
            processBillingCharge(sessionId, 60, totalShouldBeBilled, 'slab');
            session.lastBilledMinute = totalShouldBeBilled;
          }
        }
      }
    }
  }


  // --- Client Birth Chart Data ---
  socket.on('client-birth-chart', (data, cb) => {
    try {
      const { toUserId, birthData } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId) {
        if (typeof cb === 'function') return cb({ ok: false, error: 'Invalid data' });
        return;
      }

      // Send birth chart data to astrologer
      io.to(toUserId).emit('client-birth-chart', {
        fromUserId,
        birthData
      });

      if (typeof cb === 'function') cb({ ok: true });
      console.log(`Birth chart sent from ${fromUserId} to ${toUserId}`);
    } catch (err) {
      console.error('client-birth-chart error', err);
      if (typeof cb === 'function') cb({ ok: false, error: err.message });
    }
  });

  socket.on('client-birth-chart-manual', (data, cb) => {
    try {
      const { toUserId, birthData } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !toUserId) {
        if (typeof cb === 'function') return cb({ ok: false, error: 'Invalid data' });
        return;
      }

      // Send birth chart data to astrologer
      const sId = userSockets.get(toUserId);
      if (sId) {
        io.to(sId).emit('client-birth-chart', {
          fromUserId,
          birthData
        });
      }

      if (typeof cb === 'function') cb({ ok: true });
      console.log(`Birth chart sent manually from ${fromUserId} to ${toUserId}`);
    } catch (err) {
      console.error('client-birth-chart-manual error', err);
      if (typeof cb === 'function') cb({ ok: false, error: err.message });
    }
  });

  // --- Session end (manual) ---
  socket.on('session-ended', (data) => {
    try {
      const { sessionId, toUserId, type, durationMs } = data || {};
      const fromUserId = socketToUser.get(socket.id);
      if (!fromUserId || !sessionId || !toUserId) return;

      endSessionRecord(sessionId);

      const targetSocketId = userSockets.get(toUserId);
      if (targetSocketId) {
        io.to(targetSocketId).emit('session-ended', {
          sessionId,
          fromUserId,
          type,
          durationMs,
        });
      }

      console.log(
        `Session ended (manual): sessionId=${sessionId}, type=${type}, from=${fromUserId}, to=${toUserId}, duration=${durationMs} ms`
      );
    } catch (err) {
      console.error('session-ended error', err);
    }
  });

  // --- ADMIN API ---
  const checkAdmin = async (sid) => {
    const uid = socketToUser.get(sid);
    if (!uid) return false;
    const u = await User.findOne({ userId: uid });
    return u && u.role === 'superadmin';
  };

  // --- Admin: Get All Users ---
  socket.on('get-all-users', async (cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      const users = await User.find({}).sort({ role: 1, name: 1 }); // Sort by role then name
      cb({ ok: true, users });
    } catch (e) { cb({ ok: false }); }
  });

  // --- Admin: Edit User (Name Only) ---
  socket.on('admin-edit-user', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false, error: 'Unauthorized' });
    try {
      const { targetUserId, updates } = data || {};
      if (!targetUserId || !updates || !updates.name) return cb({ ok: false, error: 'Invalid Data' });

      const u = await User.findOne({ userId: targetUserId });
      if (!u) return cb({ ok: false, error: 'User not found' });

      u.name = updates.name;
      await u.save();

      console.log(`Admin edited user ${u.userId}: Name -> ${u.name}`);

      if (u.role === 'astrologer') broadcastAstroUpdate();

      cb({ ok: true });
    } catch (e) {
      console.error(e);
      cb({ ok: false, error: 'Internal Error' });
    }
  });

  // --- Admin: Update User Details (Unified) ---
  socket.on('admin-update-user-details', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false, error: 'Unauthorized' });
    try {
      const { userId, updates } = data;
      const user = await User.findOne({ userId });
      if (!user) return cb({ ok: false, error: 'User not found' });

      // Update allowed fields
      if (updates.name) user.name = updates.name;
      if (updates.realName) user.realName = updates.realName;
      if (updates.email) user.email = updates.email;
      if (updates.image) user.image = updates.image;
      if (updates.price !== undefined) user.price = parseInt(updates.price);
      if (updates.experience !== undefined) user.experience = parseInt(updates.experience);
      if (updates.astrologyExperience !== undefined) user.astrologyExperience = updates.astrologyExperience;
      if (updates.profession) user.profession = updates.profession;
      if (updates.skills) user.skills = updates.skills;
      if (updates.aadharNumber) user.aadharNumber = updates.aadharNumber;
      if (updates.panNumber) user.panNumber = updates.panNumber;
      if (updates.bankDetails) user.bankDetails = updates.bankDetails;
      if (updates.upiId) user.upiId = updates.upiId;
      if (updates.upiNumber) user.upiNumber = updates.upiNumber;
      
      if (typeof updates.isVerified === 'boolean') user.isVerified = updates.isVerified;
      if (updates.documentStatus) {
        user.documentStatus = updates.documentStatus;
        user.isDocumentVerified = (updates.documentStatus === 'verified');
      }

      await user.save();
      console.log(`Admin updated user ${user.name}:`, updates);

      if (user.role === 'astrologer') {
        console.log('Broadcasting update for astrologer:', user.name);
        await broadcastAstroUpdate();
      }

      // Notify the specific user if online
      const sId = userSockets.get(user.userId);
      if (sId) {
        const formattedUser = user.toObject ? user.toObject() : user;
        formattedUser.image = formatImageUrl(formattedUser.image, formattedUser.name);
        io.to(sId).emit('my-profile-updated', formattedUser);
      }

      cb({ ok: true, user });
    } catch (e) {
      console.error(e);
      cb({ ok: false, error: 'Update Failed' });
    }
  });

  socket.on('admin-update-role', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      const updates = { role: data.role };
      if (data.role === 'astrologer') {
        updates.walletBalance = 0;
        updates.approvalStatus = 'approved'; // Automatically approve admin assigned roles
      }
      await User.updateOne({ userId: data.userId }, updates);

      // Notify user of role/wallet change if online
      const sId = userSockets.get(data.userId);
      if (sId) {
        if (data.role === 'astrologer') io.to(sId).emit('wallet-update', { balance: 0 });
        io.to(sId).emit('app-notification', { text: `Your role has been updated to ${data.role}!` });
      }

      cb({ ok: true });
    } catch (e) { cb({ ok: false }); }
  });

  socket.on('admin-delete-user', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false, error: 'Unauthorized' });
    try {
      const { userId } = data;
      await User.deleteOne({ userId });
      cb({ ok: true });
    } catch (e) {
      console.error(e);
      cb({ ok: false, error: 'Deletion Failed' });
    }
  });

  socket.on('admin-add-wallet', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      const u = await User.findOne({ userId: data.userId });
      u.walletBalance += parseInt(data.amount);
      await u.save();

      // Notify user
      const s = userSockets.get(data.userId);
      if (s) io.to(s).emit('wallet-update', { balance: u.walletBalance });

      cb({ ok: true });
    } catch (e) { cb({ ok: false }); }
  });

  socket.on('admin-toggle-ban', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      await User.updateOne({ userId: data.userId }, { isBanned: data.isBanned });
      cb({ ok: true });
      // If banned, disconnect socket?
      if (data.isBanned) {
        const s = userSockets.get(data.userId);
        if (s) io.to(s).emit('force-logout'); // Need to handle client side
      }
    } catch (e) { cb({ ok: false }); }
  });

  socket.on('admin-get-pending-requests', async (cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      const pending = await User.find({ approvalStatus: 'pending', role: 'astrologer' });
      cb({ ok: true, requests: pending });
    } catch (e) {
      console.error(e);
      cb({ ok: false });
    }
  });

  socket.on('admin-approve-astrologer', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      const { userId, action } = data; // action: 'approve' | 'reject'
      const user = await User.findOne({ userId });
      if (!user) return cb({ ok: false, error: 'User not found' });

      if (action === 'approve') {
        user.approvalStatus = 'approved';
        user.isVerified = true;
        user.documentStatus = 'verified';
        await user.save();

        // Notify user via WhatsApp (Manual step or automated script if API exists)
        // For now, if they try to login, they will see dashboard
      } else if (action === 'reject') {
        user.approvalStatus = 'rejected';
        await user.save();
      }

      console.log(`Admin ${action}ed astrologer: ${user.name}`);
      cb({ ok: true });
    } catch (e) {
      console.error(e);
      cb({ ok: false });
    }
  });

  // Phase 10: Ledger Stats
  socket.on('admin-get-ledger-stats', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      // Get billing stats
      const billingStats = await BillingLedger.aggregate([
        {
          $group: {
            _id: null,
            totalRevenue: { $sum: '$chargedToClient' },
            totalAstroPayout: { $sum: '$creditedToAstrologer' },
            totalAdminRevenue: { $sum: '$adminAmount' },
            totalMinutes: { $sum: 1 }
          }
        }
      ]);

      // Get user counts
      const totalUsers = await User.countDocuments();
      const activeSessionCount = activeSessions.size;

      // Get full ledger for breakdown
      const fullLedger = await BillingLedger.find({}).sort({ createdAt: -1 }).limit(100);

      const billing = billingStats[0] || {};

      // Map to expected format
      const stats = {
        totalRevenue: billing.totalRevenue || 0,
        adminProfit: billing.totalAdminRevenue || 0,
        astroPayout: billing.totalAstroPayout || 0,
        totalDuration: (billing.totalMinutes || 0) * 60, // Convert minutes to seconds
        totalUsers: totalUsers,
        activeSessions: activeSessionCount
      };

      cb({ ok: true, stats, fullLedger });
    } catch (e) {
      console.error(e);
      cb({ ok: false });
    }
  });

  // --- Save FCM Token (for push notifications) ---
  socket.on('save-fcm-token', async ({ fcmToken }) => {
    const userId = socketToUser.get(socket.id);
    if (!userId || !fcmToken) return;

    try {
      await User.updateOne({ userId }, { fcmToken });
      console.log(`[FCM] Token saved for user: ${userId.substring(0, 8)}...`);
    } catch (e) {
      console.error('[FCM] Error saving token:', e);
    }
  });

  // --- Get Wallet (Manual Refresh) ---
  socket.on('get-wallet', async (data) => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return;
    try {
      const u = await User.findOne({ userId });
      if (u) {
        socket.emit('wallet-update', {
          balance: u.walletBalance,
          totalEarnings: u.totalEarnings || 0
        });
      }
    } catch (e) { }
  });

  // --- Withdrawal Logic ---
  socket.on('request-withdrawal', async (data, cb) => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return;
    try {
      const amount = parseInt(data.amount);
      if (!amount || amount < 100) return cb({ ok: false, error: 'Minimum limit 100' });

      // Attempt atomic deduction to prevent race conditions
      const u = await User.findOneAndUpdate(
        { userId, walletBalance: { $gte: amount } },
        { $inc: { walletBalance: -amount } },
        { new: true }
      );

      if (!u) {
        // Either user not found OR insufficient balance
        return cb({ ok: false, error: 'Insufficient Balance' });
      }

      let w;
      try {
        w = await Withdrawal.create({
          astroId: userId,
          amount,
          status: 'pending',
          requestedAt: Date.now()
        });
      } catch (dbErr) {
        // Rollback if DB creation fails
        console.error("DB Error creating withdrawal, rolling back wallet:", dbErr);
        // Refund seamlessly
        await User.updateOne({ userId }, { $inc: { walletBalance: amount } });
        return cb({ ok: false, error: 'Database Error - Try Again' });
      }

      // Emit wallet update to self
      io.to(socket.id).emit('wallet-update', { balance: u.walletBalance });

      // Notify Super Admins
      io.to('superadmin').emit('admin-notification', {
        type: 'withdrawal_request',
        text: `💰 New Withdrawal Request: ${u.name} requested ₹${amount}`,
        data: { withdrawalId: w._id, astroName: u.name, amount }
      });

      cb({ ok: true, balance: u.walletBalance });
    } catch (e) {
      console.error(e);
      cb({ ok: false, error: 'Error' });
    }
  });

  socket.on('approve-withdrawal', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      const { withdrawalId } = data;
      const w = await Withdrawal.findById(withdrawalId);
      if (!w || w.status !== 'pending') return cb({ ok: false, error: 'Invalid Request' });

      const u = await User.findOne({ userId: w.astroId });
      if (!u) return cb({ ok: false, error: 'User not found' });

      // Balance already deducted at request time

      // Update Request
      w.status = 'approved';
      w.processedAt = Date.now();
      await w.save();

      // Notify Astro via Socket
      const sId = userSockets.get(u.userId);
      if (sId) {
        io.to(sId).emit('app-notification', { text: `✅ Your withdrawal of ₹${w.amount} is approved! 2 working days logic applied.` });
      }

      // Notify Astro via FCM (Push Notification)
      if (u.fcmToken) {
        const fcmData = {
          type: "withdrawal_approved",
          withdrawalId: w._id.toString(),
          amount: w.amount.toString()
        };
        const fcmNotification = {
          title: "Withdrawal Approved! 💰",
          body: `Your withdrawal of ₹${w.amount} has been approved. The amount will be credited to your bank account within 2 working days.`
        };
        sendFcmV1Push(u.fcmToken, fcmData, fcmNotification, u.userId).catch(e => console.error("FCM Error:", e));
      }

      cb({ ok: true, balance: u.walletBalance });
    } catch (e) {
      console.error(e);
      cb({ ok: false, error: 'Error' });
    }
  });

  socket.on('reject-withdrawal', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return cb({ ok: false });
    try {
      const { withdrawalId } = data;
      const w = await Withdrawal.findById(withdrawalId);
      if (!w || w.status !== 'pending') return cb({ ok: false, error: 'Invalid Request' });

      const u = await User.findOne({ userId: w.astroId });
      if (u) {
        // REFUND
        u.walletBalance += w.amount;
        await u.save();

        const sId = userSockets.get(u.userId);
        if (sId) {
          io.to(sId).emit('wallet-update', { balance: u.walletBalance });
          io.to(sId).emit('app-notification', { text: `❌ Your withdrawal of ₹${w.amount} was rejected. Money refunded.` });
        }
      }

      w.status = 'rejected';
      w.processedAt = Date.now();
      await w.save();

      cb({ ok: true });
    } catch (e) {
      console.error(e);
      cb({ ok: false });
    }
  });

  socket.on('get-withdrawals', async (cb) => {
    try {
      const list = await Withdrawal.find().sort({ requestedAt: -1 }).limit(50);
      const enriched = [];
      for (const w of list) {
        const u = await User.findOne({ userId: w.astroId });
        enriched.push({
          ...w.toObject(),
          astroName: u ? u.name : 'Unknown',
          bankingDetails: u ? {
            bankName: 'Details Below',
            accountNumber: u.bankDetails || 'N/A', // Mapping free-text bank details here
            accountHolderName: u.realName || u.name,
            ifscCode: '-',
            upiId: `${u.upiId || ''} ${u.upiNumber ? '(' + u.upiNumber + ')' : ''}`
          } : null
        });
      }
      if (typeof cb === 'function') cb({ ok: true, list: enriched });
    } catch (e) {
      console.error(e);
      if (typeof cb === 'function') cb({ ok: false, list: [] });
    }
  });

  socket.on('get-my-withdrawals', async (cb) => {
    const userId = socketToUser.get(socket.id);
    if (!userId) return;
    try {
      const list = await Withdrawal.find({ astroId: userId }).sort({ requestedAt: -1 }).limit(10);
      if (typeof cb === 'function') cb({ ok: true, list });
    } catch (e) {
      if (typeof cb === 'function') cb({ ok: false });
    }
  });

  socket.on('get-payout-status', async (data, cb) => {
    try {
      const userId = socketToUser.get(socket.id);
      if (!userId) return cb({ ok: false });

      const pending = await Withdrawal.find({ astroId: userId, status: 'pending' });
      const totalPending = pending.reduce((sum, w) => sum + (w.amount || 0), 0);

      cb({ ok: true, pendingAmount: totalPending, count: pending.length });
    } catch (e) {
      console.error(e);
      cb({ ok: false, error: 'Error' });
    }
  });

  socket.on('get-slab-rates', async (cb) => {
    if (!await checkAdmin(socket.id)) return;
    cb({ ok: true, rates: SLAB_RATES });
  });

  socket.on('update-slab-rates', async (rates, cb) => {
    if (!await checkAdmin(socket.id)) return;
    try {
      await GlobalSettings.updateOne({ key: 'slab_rates' }, { value: rates }, { upsert: true });
      SLAB_RATES = rates;
      console.log('[Admin] Slab Rates updated:', SLAB_RATES);
      cb({ ok: true });
    } catch (e) {
      cb({ ok: false, error: e.message });
    }
  });

  socket.on('send-bulk-fcm', async (data, cb) => {
    if (!await checkAdmin(socket.id)) return;
    try {
      const { userIds, title, body, allUsers } = data;
      let query = {};

      if (!allUsers) {
        if (!userIds || userIds.length === 0) return cb({ ok: false, error: 'No users selected' });
        query = { userId: { $in: userIds } };
      } else {
        query = { fcmToken: { $exists: true, $ne: '' } };
      }

      const users = await User.find(query, 'userId fcmToken name');
      const validUsers = users.filter(u => u.fcmToken);
      let sentCount = 0;
      let failCount = 0;

      // Batch processing (Chunk size 20)
      const chunkSize = 20;
      for (let i = 0; i < validUsers.length; i += chunkSize) {
        const chunk = validUsers.slice(i, i + chunkSize);
        const promises = chunk.map(u => {
          const fcmData = { type: 'marketing_offer', title, body };
          const fcmNotif = { title, body };
          return sendFcmV1Push(u.fcmToken, fcmData, fcmNotif, u.userId)
            .then(res => res.success ? 1 : 0)
            .catch(() => 0);
        });

        const results = await Promise.all(promises);
        const chunkSuccess = results.reduce((a, b) => a + b, 0);
        sentCount += chunkSuccess;
        failCount += (chunk.length - chunkSuccess);
      }

      cb({ ok: true, sentCount, failCount });
    } catch (e) {
      cb({ ok: false, error: e.message });
    }
  });
  // --- End Withdrawal Logic ---

  // --- Disconnect ---
  socket.on('disconnect', async () => {
    const userId = socketToUser.get(socket.id);
    if (userId) {
      console.log(`Socket disconnected: ${socket.id}, userId=${userId}`);
      socketToUser.delete(socket.id);

      if (userSockets.get(userId) === socket.id) {
        userSockets.delete(userId);
      }

      try {
        // Find user to check role
        const user = await User.findOne({ userId });
        if (user && user.role === 'astrologer') {
          // Astrologer specific logic can go here (e.g. status tracking)
          console.log(`Astrologer ${userId} disconnected. Socket: ${socket.id}`);
        }
      } catch (e) { console.error('Disconnect DB error', e); }

      const sid = userActiveSession.get(userId);
      if (sid) {
        // --- FIX: Don't end session immediately. Give grace period. ---
        console.log(`[Session] User ${userId} disconnected. Starting grace period for Session ${sid}`);

        // Clear existing if any (debounce)
        if (sessionDisconnectTimeouts.has(userId)) {
          clearTimeout(sessionDisconnectTimeouts.get(userId));
        }

        const timeoutId = setTimeout(() => {
          // If this runs, it means user didn't reconnect in time
          console.log(`[Session] Grace period expired for ${userId}. Ending Session ${sid}`);

          sessionDisconnectTimeouts.delete(userId);

          // Double check if session still active (maybe other user ended it?)
          const s = activeSessions.get(sid);
          if (s) {
            // We can optionally update Session end time in DB here
            Session.updateOne({ sessionId: sid }, { endTime: Date.now(), duration: Date.now() - s.startedAt }).catch(() => { });

            const otherUserId = getOtherUserIdFromSession(sid, userId);

            // NOW we end it
            endSessionRecord(sid);

            if (otherUserId) {
              // Notify other user that partner dropped
              io.to(otherUserId).emit('session-ended', {
                sessionId: sid,
                reason: 'partner_disconnected'
              });
            }
          }
        }, SESSION_GRACE_PERIOD);

        sessionDisconnectTimeouts.set(userId, timeoutId);
      }
    } else {
      console.log('Socket disconnected (no user):', socket.id);
    }
  });
});

// ===== Reliable Calling System (DB + FCM) =====

// 1. Astrologer Online Toggle

// 1b. Individual Service Toggle (Chat / Audio / Video)

// 2. Initiate Call (User -> Astrologer)
app.post('/api/call/initiate', async (req, res) => {
  const { callerId, receiverId } = req.body;
  if (!callerId || !receiverId) return res.json({ ok: false, error: 'Missing IDs' });

  try {
    // A. Check Availability (DB Source of Truth)
    const astro = await User.findOne({ userId: receiverId });


    if (!astro || !astro.isAvailable) {
      return res.json({ ok: false, error: 'Astrologer is Offline', code: 'OFFLINE' });
    }

    // B. Create Call Request
    const callId = "CALL_" + Date.now() + "_" + Math.floor(Math.random() * 1000);
    await CallRequest.create({
      callId,
      callerId,
      receiverId,
      status: 'ringing'
    });

    // C. Send FCM Push Notification (WAKE UP APP)
    // Send FCM v1 Push Notification
    if (astro.fcmToken) {
      const fcmData = {
        type: 'incoming_call',
        callId: callId,
        callerId: callerId,
        callerName: 'Client'
      };

      const fcmNotification = {
        title: 'Incoming Call',
        body: 'Tap to answer video call'
      };

      const fcmResult = await sendFcmV1Push(astro.fcmToken, fcmData, fcmNotification, receiverId);
      console.log(`[FCM v1] Sent Push to ${receiverId} | Success: ${fcmResult.success}`);
    } else {
      console.log(`[FCM v1] No Token for ${receiverId}. Call might fail if app is killed.`);
    }

    res.json({ ok: true, callId, status: 'ringing' });

  } catch (e) {
    console.error("Init Call Error:", e);
    res.json({ ok: false, error: 'Server Error' });
  }
});

// 3. Accept Call (Astrologer -> Server)
app.post('/api/call/accept', async (req, res) => {
  const { callId, receiverId } = req.body;
  try {
    const call = await CallRequest.findOne({ callId });
    if (!call) return res.json({ ok: false, error: 'Invalid Call' });

    if (call.status !== 'ringing') {
      return res.json({ ok: false, error: 'Call already handled' });
    }

    call.status = 'accepted';
    await call.save();

    res.json({ ok: true, message: 'Call Connected' });

  } catch (e) {
    console.error("Accept Call Error:", e);
    res.json({ ok: false });
  }
});


// ===== Payment Gateway Logic (PhonePe) =====
// Configuration from environment variables
// Config moved to top of file

// ===== Payment Token Store (In-Memory) =====
// Token → { userId, amount, createdAt, used }
const paymentTokens = new Map();

// Token cleanup - delete expired tokens every 5 minutes
setInterval(() => {
  const now = Date.now();
  const expiryTime = 10 * 60 * 1000; // 10 minutes
  for (const [token, data] of paymentTokens) {
    if (now - data.createdAt > expiryTime) {
      paymentTokens.delete(token);
    }
  }
}, 5 * 60 * 1000);

// Generate Payment Token (Called from WebView with auth session)

// Verify Payment Token (Called from payment.html in browser)

// 1. Initiate Payment (Supports both token-based and legacy userId-based)
// Validate Coupon Code


// 2. Callback (Webhook)

// --- 3. Public Status Pages ---
app.get('/payment-success', (req, res) => {
  const { amount, txnId } = req.query;
  const intentUrl = `intent://payment-success?status=success&txnId=${txnId}#Intent;scheme=astrohark;package=com.astrohark.app;end`;
  const customSchemeUrl = `astrohark://payment-success?status=success&txnId=${txnId}`;

  res.send(`
    <!DOCTYPE html>
    <html>
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Success</title>
        <style>
          body { display:flex; flex-direction:column; align-items:center; justify-content:center; height:100vh; font-family:sans-serif; background:#f0fdf4; margin:0; text-align:center; }
          .card { background:white; padding:40px; border-radius:20px; box-shadow:0 10px 30px rgba(0,0,0,0.1); width:320px; }
          .icon { font-size:60px; color:#22c55e; margin-bottom:20px; }
          .btn { display:block; padding:15px; background:#16a34a; color:white; text-decoration:none; border-radius:10px; font-weight:bold; margin-top:20px; }
        </style>
      </head>
      <body>
        <div class="card">
          <div class="icon">✓</div>
          <h2>Success!</h2>
          <p>₹${amount || '--'}</p>
          <a href="${intentUrl}" class="btn">Return to Home</a>
          <script>
             function openApp() {
               // Try Intent first (Chrome/Android)
               window.location.href = "${intentUrl}";
               // Immediate Deep Link fallback
               setTimeout(() => { window.location.href = "${customSchemeUrl}"; }, 100);
               // Backup force link
               setTimeout(() => { window.location.href = "astrohark://payment-success"; }, 500);
             }
             openApp();
          </script>
        </div>
      </body>
    </html>
  `);
});

app.get('/payment-failed', (req, res) => {
  const intentUrl = `intent://payment-failed?status=failed#Intent;scheme=astrohark;package=com.astrohark.app;end`;
  const customSchemeUrl = `astrohark://payment-failed?status=failed`;
  res.send(`
    <!DOCTYPE html>
    <html>
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Failed</title>
        <style>
          body { display:flex; flex-direction:column; align-items:center; justify-content:center; height:100vh; font-family:sans-serif; background:#fef2f2; margin:0; text-align:center; }
          .card { background:white; padding:40px; border-radius:20px; box-shadow:0 10px 30px rgba(0,0,0,0.1); width:320px; }
          .icon { font-size:60px; color:#ef4444; margin-bottom:20px; }
          .btn { display:block; padding:15px; background:#b91c1c; color:white; text-decoration:none; border-radius:10px; font-weight:bold; margin-top:20px; }
        </style>
      </head>
      <body>
        <div class="card">
          <div class="icon">✗</div>
          <h2>Failed</h2>
          <a href="${intentUrl}" class="btn">Return to Home</a>
          <script>
             function openApp() { window.location.href = "${intentUrl}"; setTimeout(() => { window.location.href = "${customSchemeUrl}"; }, 100); }
             openApp();
          </script>
        </div>
      </body>
    </html>
  `);
});

// 3. Payment History API

// ===== PhonePe SDK Configuration =====
const PHONEPE_MERCHANT_ID = process.env.PHONEPE_MERCHANT_ID;
const PHONEPE_SALT_KEY = process.env.PHONEPE_SALT_KEY;
const PHONEPE_SALT_INDEX = process.env.PHONEPE_SALT_INDEX;
const PHONEPE_HOST_URL = process.env.PHONEPE_HOST_URL || "https://api.phonepe.com/apis/hermes";

// ===== PhonePe SDK API (Native App Payment) =====

// PhonePe SDK Init - For React Native PhonePe SDK
app.post('/api/phonepe/init', async (req, res) => {
  try {
    const { userId, amount } = req.body;
    if (!userId || !amount) {
      return res.status(400).json({ ok: false, error: 'userId and amount required' });
    }

    // Fetch User
    const user = await User.findOne({ userId });
    if (!user) {
      return res.status(404).json({ ok: false, error: 'User not found' });
    }

    const userMobile = (user.phone || "9999999999").replace(/[^0-9]/g, '').slice(-10);
    const merchantTransactionId = "TXN_" + Date.now() + "_" + Math.floor(Math.random() * 1000);
    const cleanUserId = userId.replace(/[^a-zA-Z0-9]/g, '');

    // Create Pending Payment Record
    const baseAmount = Math.floor(amount / 1.18);
    await Payment.create({
      transactionId: merchantTransactionId,
      merchantTransactionId,
      userId,
      amount,
      baseAmount,
      gstAmount: amount - baseAmount,
      status: 'pending'
    });

    // PhonePe Payload
    const payload = {
      merchantId: PHONEPE_MERCHANT_ID,
      merchantTransactionId: merchantTransactionId,
      merchantUserId: cleanUserId,
      amount: amount * 100, // Paise
      redirectUrl: `https://astrohark.com/api/payment/callback?isApp=true`,
      redirectMode: "POST",
      callbackUrl: `https://astrohark.com/api/phonepe/callback`,
      mobileNumber: userMobile,
      paymentInstrument: {
        type: "PAY_PAGE"
      }
    };

    const base64Payload = Buffer.from(JSON.stringify(payload)).toString('base64');
    const stringToSign = base64Payload + "/pg/v1/pay" + PHONEPE_SALT_KEY;
    const sha256 = crypto.createHash('sha256').update(stringToSign).digest('hex');
    const checksum = sha256 + "###" + PHONEPE_SALT_INDEX;

    const response = await fetch(`${PHONEPE_HOST_URL}/pg/v1/pay`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-VERIFY': checksum,
        'accept': 'application/json'
      },
      body: JSON.stringify({ request: base64Payload })
    });

    const data = await response.json();
    console.log('[PhonePe SDK Init]', JSON.stringify(data));

    if (data.success) {
      res.json({
        ok: true,
        transactionId: merchantTransactionId,
        data: data.data
      });
    } else {
      res.json({ ok: false, error: data.message || 'Payment initialization failed' });
    }

  } catch (e) {
    console.error("PhonePe SDK Init Error:", e);
    res.status(500).json({ ok: false, error: 'Internal Server Error' });
  }
});

// NEW: Signature Endpoint for Native Android SDK
app.post('/api/phonepe/sign', async (req, res) => {
  try {
    const { userId, amount } = req.body;
    if (!userId || !amount) {
      return res.status(400).json({ ok: false, error: 'userId and amount required' });
    }

    const user = await User.findOne({ userId });
    const userMobile = user ? (user.phone || "9999999999").replace(/[^0-9]/g, '').slice(-10) : "9999999999";
    const merchantTransactionId = "TXN_" + Date.now() + "_" + Math.floor(Math.random() * 1000);
    const cleanUserId = userId.replace(/[^a-zA-Z0-9]/g, '');

    // Record intent in DB
    const baseAmount = Math.floor(amount / 1.18);
    await Payment.create({
      transactionId: merchantTransactionId,
      merchantTransactionId,
      userId,
      amount,
      baseAmount,
      gstAmount: amount - baseAmount,
      status: 'pending'
    });

    // Native SDK Payload
    const payload = {
      merchantId: PHONEPE_MERCHANT_ID,
      merchantTransactionId: merchantTransactionId,
      merchantUserId: cleanUserId,
      amount: amount * 100,
      callbackUrl: "https://astrohark.com/api/phonepe/callback",
      mobileNumber: userMobile,
      paymentInstrument: {
        type: "PAY_PAGE"
      }
    };

    const base64Payload = Buffer.from(JSON.stringify(payload)).toString('base64');
    const stringToSign = base64Payload + "/pg/v1/pay" + PHONEPE_SALT_KEY;
    const sha256 = crypto.createHash('sha256').update(stringToSign).digest('hex');
    const checksum = sha256 + "###" + PHONEPE_SALT_INDEX;

    res.json({
      ok: true,
      payload: base64Payload,
      checksum: checksum,
      transactionId: merchantTransactionId
    });

  } catch (e) {
    console.error("PhonePe Sign Error:", e);
    res.status(500).json({ ok: false, error: 'Signing failed' });
  }
});

// PhonePe STATUS API - Used by Android App to verify after return
app.get('/api/phonepe/status/:transactionId', async (req, res) => {
  try {
    const { transactionId } = req.params;
    if (!transactionId) return res.status(400).json({ ok: false, error: 'transactionId required' });

    // Header construction: SHA256("/pg/v1/status/{merchantId}/{transactionId}" + saltKey) + "###" + saltIndex
    const endpoint = `/pg/v1/status/${PHONEPE_MERCHANT_ID}/${transactionId}`;
    const stringToSign = endpoint + PHONEPE_SALT_KEY;
    const sha256 = crypto.createHash('sha256').update(stringToSign).digest('hex');
    const checksum = sha256 + "###" + PHONEPE_SALT_INDEX;

    const response = await fetch(`${PHONEPE_HOST_URL}${endpoint}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        'X-VERIFY': checksum,
        'X-MERCHANT-ID': PHONEPE_MERCHANT_ID,
        'accept': 'application/json'
      }
    });

    const data = await response.json();
    console.log(`[PhonePe Status Check] TXN: ${transactionId} ->`, JSON.stringify(data));

    if (data.success && data.code === "PAYMENT_SUCCESS") {
      // Find and update payment record if not success yet
      const payment = await Payment.findOne({ transactionId });
      if (payment && payment.status !== 'success') {
        payment.status = 'success';
        payment.providerRefId = data.data?.providerReferenceId || '';
        await payment.save();

        // Credit Wallet
        const user = await User.findOne({ userId: payment.userId });
        if (user) {
          user.walletBalance = (user.walletBalance || 0) + parseFloat(payment.baseAmount || payment.amount || 0);
          await user.save();
          console.log(`[PhonePe SUCCESS] Wallet credited for ${user.name} +₹${payment.baseAmount || payment.amount}`);
        }
      }
      res.json({ ok: true, status: 'success', data: data.data });
    } else {
      res.json({ ok: false, status: 'failed', message: data.message });
    }
  } catch (e) {
    console.error("PhonePe Status Check Error:", e);
    res.status(500).json({ ok: false, error: 'Verification failed' });
  }
});

/**
 * PhonePe Server Callback Handler
 */
app.post('/api/phonepe/callback', async (req, res) => {
  try {
    const { response } = req.body;
    if (!response) {
       console.error('[PhonePe Callback] No response field in body');
       return res.status(400).send('No response');
    }

    // Decode Base64 Response
    const decodedStr = Buffer.from(response, 'base64').toString('utf8');
    const decoded = JSON.parse(decodedStr);
    console.log('[PhonePe Callback Received]', decoded);

    const transactionId = decoded.data?.merchantTransactionId;
    const success = decoded.success;
    const code = decoded.code;

    if (transactionId) {
       const payment = await Payment.findOne({ transactionId });
       if (!payment) {
          console.error(`[PhonePe Callback] Payment record not found for TXN: ${transactionId}`);
          return res.status(200).send('TXN NOT FOUND'); // Still 200 to acknowledge callback
       }

       if (success && code === "PAYMENT_SUCCESS") {
          if (payment.status !== 'success') {
             payment.status = 'success';
             payment.providerRefId = decoded.data?.providerReferenceId || '';
             await payment.save();

             // Credit Wallet
             const user = await User.findOne({ userId: payment.userId });
             if (user) {
                user.walletBalance = (user.walletBalance || 0) + parseFloat(payment.baseAmount || payment.amount || 0);
                await user.save();
                console.log(`[PhonePe CALLBACK CREDITED] ${user.name} +₹${payment.baseAmount || payment.amount}`);
                
                // Referral Logic hook (same as Razorpay)
                if (user.referredBy) {
                  const successCount = await Payment.countDocuments({ 
                      userId: user.userId, 
                      status: 'success'
                  });
                  if (successCount === 1) {
                      const referrer = await User.findOne({ userId: user.referredBy });
                      if (referrer) {
                          referrer.walletBalance = (referrer.walletBalance || 0) + 81;
                          referrer.totalEarnings = (referrer.totalEarnings || 0) + 81;
                          referrer.referralCount = (referrer.referralCount || 0) + 1;
                          await referrer.save();

                          await Payment.create({
                              transactionId: `REF_${crypto.randomBytes(8).toString('hex')}`,
                              userId: referrer.userId,
                              amount: 81,
                              baseAmount: 81,
                              gstAmount: 0,
                              status: 'success',
                              reason: 'referral'
                          });
                      }
                  }
                }

                // WebSocket Update
                const io = req.app.get('io');
                const { userSockets } = require('./services/socketStore');
                if (userSockets.has(user.userId)) {
                   io.to(userSockets.get(user.userId)).emit('wallet-update', {
                      balance: user.walletBalance,
                      superBalance: user.superWalletBalance
                   });
                }
             }
          }
       } else {
          if (payment.status === 'pending') {
             payment.status = 'failed';
             await payment.save();
          }
       }
    }

    // Acknowledge PhonePe that we received the callback
    res.status(200).send('OK');

  } catch (e) {
    console.error("PhonePe Callback Error:", e);
    res.status(500).send('Error');
  }
});

// Payment flows are now handled via /api/payment routes in routes/payment.routes.js

// ============================================================================
// MOBILE APP SPECIFIC ENDPOINTS (from mobileapp/server/server.js)
// ============================================================================

/**
 * Register user's FCM token
 * POST /register
 */
// [DEPRECATED] - Use the MongoDB /register endpoint at line 524
// app.post('/register', (req, res) => {
//   const { userId, fcmToken } = req.body;
//   if (!userId || typeof userId !== 'string' || !fcmToken || typeof fcmToken !== 'string') {
//     return res.status(400).json({ success: false, error: 'Invalid input' });
//   }
//   mobileTokenStore.set(userId, fcmToken);
//   console.log(`[Mobile] Registered: ${userId} → ${fcmToken.substring(0, 20)}...`);
//   res.json({ success: true, message: `User ${userId} registered successfully` });
// });

/**
 * List all registered users (for debugging)
 * GET /users
 */
app.get('/users', (req, res) => {
  const users = [];
  mobileTokenStore.forEach((token, userId) => {
    users.push({ userId, tokenPreview: `${token.substring(0, 15)}...` });
  });
  res.json({ count: users.length, users });
});

/**
 * Unregister a user
 * DELETE /unregister/:userId
 */
app.delete('/unregister/:userId', (req, res) => {
  const { userId } = req.params;
  if (mobileTokenStore.has(userId)) {
    mobileTokenStore.delete(userId);
    res.json({ success: true, message: `User ${userId} unregistered` });
  } else {
    res.status(404).json({ success: false, error: 'User not found' });
  }
});

/**
 * Initiate a call to a user
 * POST /call
 */
app.post('/call', async (req, res) => {
  const { callerId, calleeId, callerName } = req.body;

  if (!callerId || !calleeId) {
    return res.status(400).json({ success: false, error: 'Missing callerId or calleeId' });
  }

  // Check if Firebase is initialized
  // Check if Firebase is initialized
  if (!callApp) {
    console.error('[Mobile] Call App Firebase NOT initialized. Check firebase-service-account.json');
    return res.status(503).json({
      success: false,
      error: 'Push notification service unavailable (Server Config Error)',
      details: global.callAppInitError || 'Unknown initialization error' // Exposed for debugging
    });
  }

  // UPDATED: Look up from MongoDB (User collection)
  // const fcmToken = mobileTokenStore.get(calleeId);
  const user = await User.findOne({ userId: calleeId });
  const fcmToken = user ? user.fcmToken : null;

  if (!fcmToken) {
    return res.status(404).json({ success: false, error: 'User not online/registered' });
  }

  const callId = `call_${Date.now()}_${Math.random().toString(36).substring(7)}`;

  const message = {
    token: fcmToken,
    data: {
      type: 'INCOMING_CALL',
      callId: callId,
      callerId: callerId,
      callerName: callerName || callerId,
      timestamp: Date.now().toString()
    },
    android: {
      priority: 'high',
      directBootOk: true
    }
  };

  console.log(`[Mobile] Sending call: ${callerId} → ${calleeId} (callId: ${callId})`);

  try {
    const response = await callApp.messaging().send(message);
    console.log(`[Mobile] Call notification sent: ${response}`);
    res.json({ success: true, callId, message: 'Call sent' });
  } catch (error) {
    console.error('[Mobile] FCM Error:', error.message);
    if (error.code === 'messaging/invalid-registration-token' ||
      error.code === 'messaging/registration-token-not-registered') {
      // Remove invalid token from DB
      await User.updateOne({ userId: calleeId }, { $unset: { fcmToken: 1 } });
      console.log(`[Mobile] Invalid token removed for user ${calleeId}`);
    }
    // Return 500 only for actual sending errors, not config errors
    res.status(500).json({ success: false, error: error.message });
  }
});

// 404 Handler
app.use((req, res) => {
  res.status(404).sendFile(path.join(__dirname, 'public', 'index.html')); // Fallback to index or create a specialized 404.html
});

const PORT = process.env.PORT || 3000;

if (require.main === module) {
  server.listen(PORT, () => {
    console.log(`Server running on http://0.0.0.0:${PORT}`);
  });
}

// Graceful shutdown - prevents port stuck issues
process.on('SIGTERM', () => {
  console.log('SIGTERM received, shutting down gracefully...');
  server.close(() => {
    console.log('Server closed');
    process.exit(0);
  });
});

process.on('SIGINT', () => {
  console.log('SIGINT received, shutting down gracefully...');
  server.close(() => {
    console.log('Server closed');
    process.exit(0);
  });
});

module.exports = { app, server, sendFcmV1Push };