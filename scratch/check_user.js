const mongoose = require('mongoose');
require('dotenv').config();

const UserSchema = new mongoose.Schema({}, { strict: false });
const User = mongoose.model('User', UserSchema);

const WithdrawalSchema = new mongoose.Schema({}, { strict: false });
const Withdrawal = mongoose.model('Withdrawal', WithdrawalSchema);

async function check() {
  await mongoose.connect(process.env.MONGODB_URI);
  const wlist = await Withdrawal.find().lean();
  console.log('Withdrawal records count:', wlist.length);

  for (const w of wlist) {
    const byUserId = await User.findOne({ userId: w.astroId }).lean();
    console.log(`For w.astroId=${w.astroId}:`);
    console.log('  by userId:', byUserId ? byUserId.name : null);
  }
  await mongoose.disconnect();
}
check().catch(console.error);
