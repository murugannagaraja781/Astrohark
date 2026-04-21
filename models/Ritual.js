const mongoose = require('mongoose');

const RitualSchema = new mongoose.Schema({
    title: {
        type: String,
        required: true
    },
    title_ta: {
        type: String, // Tamil title
        required: false
    },
    subtitle: {
        type: String,
        required: true
    },
    subtitle_ta: {
        type: String, // Tamil subtitle
        required: false
    },
    description: {
        type: String,
        required: false
    },
    description_ta: {
        type: String,
        required: false
    },
    imageUrl: {
        type: String,
        required: true
    },
    price: {
        type: Number,
        default: 0
    },
    isActive: {
        type: Boolean,
        default: true
    },
    order: {
        type: Number,
        default: 0
    }
}, { timestamps: true });

module.exports = mongoose.model('Ritual', RitualSchema);
