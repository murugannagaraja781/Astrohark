const fs = require('fs');
const path = '/Users/wohozo/Documents/Astrohark/astroapp/android/app/src/main/java/com/astrohark/app/ui/call/CallActivity.kt';
let code = fs.readFileSync(path, 'utf8');

// Change countdown calculation to fetch for the client, and run it for the client
// Wait, actually let's just run it for BOTH but fetch the correct user ID.
// If role == "astrologer", client is partnerId. If role == "user", client is session.userId.

const oldTimerCode = `            // Start Remaining Time Countdown (for astrologers only)
            if (role == "astrologer") {`;

const newTimerCode = `            // Start Remaining Time Countdown
            if (true) {
                val clientIdToFetch = if (role == "astrologer") partnerId else session?.userId`;

code = code.replace(oldTimerCode, newTimerCode);

code = code.replace(
    /\.url\("\$\{com\.astrohark\.app\.utils\.Constants\.SERVER_URL\}\/api\/user\/\$\{partnerId\}"\)/,
    '.url("${com.astrohark.app.utils.Constants.SERVER_URL}/api/user/${clientIdToFetch}")'
);

// Change the UI logic
code = code.replace(
    /if \(role == "astrologer" && remainingTime\.isNotEmpty\(\) && remainingTime != "00:00"\)/,
    'if (role != "astrologer" && remainingTime.isNotEmpty() && remainingTime != "00:00")'
);

fs.writeFileSync(path, code);
