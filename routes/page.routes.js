const express = require('express');
const router = express.Router();
const path = require('path');

router.get('/privacy-policy', (req, res) => res.sendFile(path.join(__dirname, '../public', 'privacy-policy.html')));
router.get('/terms-condition', (req, res) => res.sendFile(path.join(__dirname, '../public', 'terms-condition.html')));
router.get('/refund-cancellation-policy', (req, res) => res.sendFile(path.join(__dirname, '../public', 'refund-cancellation-policy.html')));
router.get('/return-policy', (req, res) => res.sendFile(path.join(__dirname, '../public', 'return-policy.html')));
router.get('/shipping-policy', (req, res) => res.sendFile(path.join(__dirname, '../public', 'shipping-policy.html')));

router.get('/wallet', (req, res) => {
    const status = req.query.status || 'unknown';
    const reason = req.query.reason || '';
    const scheme = status === 'success' ? 'astro5://payment-success' : 'astro5://payment-failed';
    const deepLink = `${scheme}?status=${status}&reason=${reason}`;
    const intentUrl = `intent://payment-${status === 'success' ? 'success' : 'failed'}?status=${status}#Intent;scheme=astro5;package=com.astrohark.app;end`;

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
        <p>Redirecting back to app...</p>
        <a href="${deepLink}" class="btn">Return to Home</a>
        <script>
          setTimeout(() => { window.location.href = "${intentUrl}"; }, 500);
          setTimeout(() => { window.location.href = "${deepLink}"; }, 1500);
        </script>
      </body>
    </html>
  `);
});

router.get('/', (req, res) => res.sendFile(path.join(__dirname, '../public', 'index.html')));

module.exports = router;
