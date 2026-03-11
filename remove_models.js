const fs = require('fs');

const file = fs.readFileSync('server.js', 'utf8');
const lines = file.split('\n');

const toRemove = [
  // User
  { startMatches: "const UserSchema = new mongoose.Schema({", endMatches: "const User = mongoose.model('User', UserSchema);", occurrences: 1 },
  { startMatches: "const CallRequestSchema = new mongoose.Schema({", endMatches: "const CallRequest = mongoose.model('CallRequest', CallRequestSchema);", occurrences: 1 },
  { startMatches: "const SessionSchema = new mongoose.Schema({", endMatches: "const Session = mongoose.model('Session', SessionSchema);", occurrences: 1 },
  { startMatches: "const PairMonthSchema = new mongoose.Schema({", endMatches: "const PairMonth = mongoose.model('PairMonth', PairMonthSchema);", occurrences: 1 },
  { startMatches: "const BillingLedgerSchema = new mongoose.Schema({", endMatches: "const BillingLedger = mongoose.model('BillingLedger', BillingLedgerSchema);", occurrences: 1 },
  { startMatches: "const WithdrawalSchema = new mongoose.Schema({", endMatches: "const Withdrawal = mongoose.model('Withdrawal', WithdrawalSchema);", occurrences: 1 },
  { startMatches: "const PaymentSchema = new mongoose.Schema({", endMatches: "const Payment = mongoose.model('Payment', PaymentSchema);", occurrences: 1 },
  { startMatches: "const ChatMessageSchema = new mongoose.Schema({", endMatches: "const ChatMessage = mongoose.model('ChatMessage', ChatMessageSchema);", occurrences: 1 },
  { startMatches: "const AcademyVideoSchema = new mongoose.Schema({", endMatches: "const AcademyVideo = mongoose.model('AcademyVideo', AcademyVideoSchema);", occurrences: 1 },
  { startMatches: "const BannerSchema = new mongoose.Schema({", endMatches: "const Banner = mongoose.model('Banner', BannerSchema);", occurrences: 1 },
  { startMatches: "const AccountDeletionRequestSchema = new mongoose.Schema({", endMatches: "const AccountDeletionRequest = mongoose.model('AccountDeletionRequest', AccountDeletionRequestSchema);", occurrences: 1 },
  { startMatches: "const GlobalSettingsSchema = new mongoose.Schema({", endMatches: "const GlobalSettings = mongoose.model('GlobalSettings', GlobalSettingsSchema);", occurrences: 1 },
];

let removeRanges = [];

for (const rm of toRemove) {
  let foundStats = 0;
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].includes(rm.startMatches)) {
      foundStats++;
      if (foundStats === rm.occurrences) {
        let endIdx = i;
        while (endIdx < lines.length) {
          if (lines[endIdx].includes(rm.endMatches)) {
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
let removedCount = 0;
for (let i = 0; i < lines.length; i++) {
  let skip = false;
  for (const [start, end] of removeRanges) {
    if (i >= start && i <= end) {
      skip = true;
      break;
    }
  }
  if (!skip) keepLines.push(lines[i]);
  else removedCount++;
}

fs.writeFileSync('server.js', keepLines.join('\n'));
console.log(`Removed ${removeRanges.length} blocks. Removed ${removedCount} lines.`);
