const fs = require('fs');

const file = fs.readFileSync('server.js', 'utf8');
const lines = file.split('\n');

const toRemove = [
  // Authentication (send-otp, verify-otp)
  { startMatches: "app.post('/api/send-otp'", endMatches: "});", occurrences: 1 },
  { startMatches: "app.post('/api/verify-otp'", endMatches: "});", occurrences: 1 },
  
  // Admin routes
  { startMatches: "app.post('/api/admin/academy/videos'", endMatches: "});", occurrences: 1 },
  { startMatches: "app.put('/api/admin/academy/videos/:id'", endMatches: "});", occurrences: 1 },
  { startMatches: "app.delete('/api/admin/academy/videos/:id'", endMatches: "});", occurrences: 1 },
  { startMatches: "app.get('/api/admin/banners'", endMatches: "});", occurrences: 1 },
  { startMatches: "app.post('/api/admin/banners'", endMatches: "});", occurrences: 1 },
  { startMatches: "app.put('/api/admin/banners/:id'", endMatches: "});", occurrences: 1 },
  { startMatches: "app.delete('/api/admin/banners/:id'", endMatches: "});", occurrences: 1 },
  { startMatches: "app.get('/api/admin/deletion-requests'", endMatches: "});", occurrences: 1 },
  { startMatches: "app.post('/api/admin/process-deletion'", endMatches: "});", occurrences: 1 },
  
  // Astrologer routes
  { startMatches: "app.post('/api/astrologer/register'", endMatches: "});", occurrences: 1 },
  { startMatches: "app.post('/api/astrologer/online'", endMatches: "});", occurrences: 1 },
  { startMatches: "app.post('/api/astrologer/service-toggle'", endMatches: "});", occurrences: 1 },

  // Horoscope
  { startMatches: "app.post('/api/charts/birth-chart'", endMatches: "});", occurrences: 1 },
  { startMatches: "app.post('/api/match/porutham'", endMatches: "});", occurrences: 1 },

  // Common
  { startMatches: "app.post('/api/city-autocomplete'", endMatches: "});", occurrences: 1 },
  { startMatches: "app.post('/api/city-timezone'", endMatches: "});", occurrences: 1 },

  // User
  { startMatches: "app.get('/api/user/:userId'", endMatches: "});", occurrences: 1 },
  { startMatches: "app.get('/api/chat/history/:sessionId'", endMatches: "});", occurrences: 1 },

  // Payment
  { startMatches: "app.post('/api/payment/token'", endMatches: "});", occurrences: 1 },
  { startMatches: "app.get('/api/verify-payment-token'", endMatches: "});", occurrences: 1 },
  { startMatches: "app.post('/api/payment/validate-coupon'", endMatches: "});", occurrences: 1 },
  { startMatches: "app.post('/api/payment/create'", endMatches: "});", occurrences: 1 },
  { startMatches: "app.post('/api/payment/callback'", endMatches: "});", occurrences: 1 },
  { startMatches: "app.get('/api/payment/history/:userId'", endMatches: "});", occurrences: 1 }
];

let currentLine = 0;
let removeRanges = [];

for (const rm of toRemove) {
  let foundStats = 0;
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].includes(rm.startMatches)) {
      foundStats++;
      if (foundStats === rm.occurrences) {
        let endIdx = i;
        let braceCount = 0;
        let started = false;
        while (endIdx < lines.length) {
          braceCount += (lines[endIdx].match(/\{/g) || []).length;
          braceCount -= (lines[endIdx].match(/\}/g) || []).length;
          started = true;
          if (started && braceCount === 0 && lines[endIdx].includes(rm.endMatches)) {
            removeRanges.push([i, endIdx]);
            break;
          }
          endIdx++;
        }
        break;
      }
    }
  }
}

let keepLines = [];
for (let i = 0; i < lines.length; i++) {
  let skip = false;
  for (const [start, end] of removeRanges) {
    if (i >= start && i <= end) {
      skip = true;
      break;
    }
  }
  if (!skip) keepLines.push(lines[i]);
}

fs.writeFileSync('server_cleaned.js', keepLines.join('\n'));
console.log(`Removed ${removeRanges.length} blocks. Removed ${lines.length - keepLines.length} lines.`);
