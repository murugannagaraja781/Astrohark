const mongoose = require('mongoose');
require('dotenv').config();

const WithdrawalSchema = new mongoose.Schema({}, { strict: false });
const Withdrawal = mongoose.model('Withdrawal', WithdrawalSchema);
const UserSchema = new mongoose.Schema({}, { strict: false });
const User = mongoose.model('User', UserSchema);

async function simulateGetWithdrawals(data, cb) {
  let callback = typeof cb === 'function' ? cb : (typeof data === 'function' ? data : null);
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
          accountNumber: u?.bankDetails || 'N/A',
          accountHolderName: u?.realName || u?.name || 'N/A',
          ifscCode: '-',
          upiId: `${u?.upiId || ''} ${u?.upiNumber ? '(' + u.upiNumber + ')' : ''}`.trim() || 'N/A'
        } : null
      });
    }
    if (typeof callback === 'function') callback({ ok: true, list: enriched });
  } catch (e) {
    console.error(e);
    if (typeof callback === 'function') callback({ ok: false, list: [] });
  }
}

async function run() {
  await mongoose.connect(process.env.MONGODB_URI);
  console.log('Testing simulateGetGetWithdrawals with ({}, callback)...');
  await simulateGetWithdrawals({}, (res) => {
    console.log('Result received:', JSON.stringify(res, null, 2));
  });
  await mongoose.disconnect();
}
run();
