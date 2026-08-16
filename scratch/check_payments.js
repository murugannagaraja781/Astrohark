const mongoose = require('mongoose');
require('dotenv').config();

const Payment = require('../models/Payment');

async function check() {
  await mongoose.connect(process.env.MONGODB_URI);
  console.log('Connected to MongoDB');
  const payments = await Payment.find({
    createdAt: { $gte: new Date(Date.now() - 24 * 60 * 60 * 1000 * 2) } // last 2 days
  }).sort({ createdAt: -1 });
  console.log(`Found ${payments.length} payments in the last 2 days:`);
  payments.forEach(p => {
    console.log(`ID: ${p._id}, User: ${p.userId}, Amt: ${p.amount}, Base: ${p.baseAmount}, GST: ${p.gstAmount}, Status: ${p.status}, isApp: ${p.isApp}, Reason: ${p.reason}`);
  });
  await mongoose.disconnect();
}
check().catch(console.error);
