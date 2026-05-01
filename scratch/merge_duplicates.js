const mongoose = require('mongoose');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../.env') });
const User = require('../models/User');

async function mergeDuplicates() {
    try {
        await mongoose.connect(process.env.MONGODB_URI);
        const users = await User.find({});
        const map = new Map();

        users.forEach(u => {
            const last10 = u.phone.slice(-10);
            if (!map.has(last10)) map.set(last10, []);
            map.get(last10).push(u);
        });

        for (const [num, list] of map.entries()) {
            if (list.length > 1) {
                console.log(`Processing Phone: ...${num}`);
                
                // Find the "real" account (the one with the better role or older)
                // and the "accidental" account (usually the 91-prefixed one with 'client' role)
                const with91 = list.find(u => u.phone.startsWith('91') && u.phone.length > 10);
                const without91 = list.find(u => u.phone.length === 10);

                if (with91 && without91) {
                    console.log(`  - Found pair: ${without91.phone} (${without91.role}) and ${with91.phone} (${with91.role})`);
                    
                    // Determine which one to keep.
                    // If without91 is superadmin or astrologer, we definitely want to keep its role.
                    // Usually without91 is the original.
                    
                    const toKeep = (without91.role === 'superadmin' || without91.role === 'astrologer') ? without91 : (with91.role === 'superadmin' || with91.role === 'astrologer' ? with91 : without91);
                    const toDelete = (toKeep === without91) ? with91 : without91;

                    console.log(`  - Keeping: ${toKeep.phone} (${toKeep.role}) | Deleting: ${toDelete.phone} (${toDelete.role})`);

                    // 1. Transfer balance
                    toKeep.walletBalance = (toKeep.walletBalance || 0) + (toDelete.walletBalance || 0);
                    
                    // 2. Ensure role is the highest one
                    const roles = ['client', 'astrologer', 'superadmin'];
                    if (roles.indexOf(toDelete.role) > roles.indexOf(toKeep.role)) {
                        toKeep.role = toDelete.role;
                    }
                    
                    // 3. Update phone to 91-prefixed if it's not already
                    const targetPhone = '91' + num;
                    
                    // 4. Perform DB operations
                    await User.deleteOne({ _id: toDelete._id });
                    console.log(`    ✓ Deleted ${toDelete.phone}`);
                    
                    toKeep.phone = targetPhone;
                    await toKeep.save();
                    console.log(`    ✓ Updated ${toKeep.name} to ${targetPhone} with role ${toKeep.role}`);
                }
            }
        }

        console.log('Merge complete.');
        process.exit(0);
    } catch (err) {
        console.error('Merge failed:', err);
        process.exit(1);
    }
}
mergeDuplicates();
