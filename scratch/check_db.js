const mongoose = require('mongoose');
require('dotenv').config();

const WithdrawalSchema = new mongoose.Schema({
  astroId: String,
  amount: Number,
  status: { type: String, default: 'pending' },
  requestedAt: { type: Date, default: Date.now },
  processedAt: Date
});
const Withdrawal = mongoose.model('Withdrawal', WithdrawalSchema);

async function check() {
  await mongoose.connect(process.env.MONGODB_URI);
  console.log('Connected to MongoDB');
  const count = await Withdrawal.countDocuments();
  console.log('Total Withdrawals in DB:', count);
  const list = await Withdrawal.find().sort({ requestedAt: -1 }).limit(10);
  console.log('Latest 10 Withdrawals:', JSON.stringify(list, null, 2));
  await mongoose.disconnect();
}
check().catch(console.error);
