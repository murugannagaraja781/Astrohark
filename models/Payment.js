const mongoose = require('mongoose');

const PaymentSchema = new mongoose.Schema({
    transactionId: { type: String, unique: true },
    merchantTransactionId: String,
    userId: String,
    amount: Number,
    baseAmount: Number,
    gstAmount: Number,
    withGst: { type: Boolean, default: false },
    status: { type: String, enum: ['pending', 'success', 'failed'], default: 'pending' },
    createdAt: { type: Date, default: Date.now },
    providerRefId: String,
    isApp: { type: Boolean, default: false },
    isSuperWallet: { type: Boolean, default: false },
    offerPercentage: { type: Number, default: 0 },
    couponCode: String,
    couponBonus: { type: Number, default: 0 }
});

PaymentSchema.index({ transactionId: 1 });
PaymentSchema.index({ userId: 1 });
PaymentSchema.index({ status: 1 });
PaymentSchema.index({ createdAt: -1 });

module.exports = mongoose.model('Payment', PaymentSchema);
