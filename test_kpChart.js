const express = require('express');
const bodyParser = require('body-parser');
const kpChartRouter = require('./routes/rasiEng/kpChart');

const app = express();
app.use(bodyParser.json());
app.use('/kp-chart', kpChartRouter);

const PORT = 3000;
app.listen(PORT, async () => {
    console.log(`Test server running on port ${PORT}`);
    
    // Simulate a request using user's book data: 31-03-1987 08:28:00 AM, Jayamkondam (11.1243 N, 79.2153 E)
    try {
        const fetch = require('node-fetch');
        const res = await fetch(`http://localhost:${PORT}/kp-chart/kp-chart`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                date: '1987-03-31',
                time: '08:28:00',
                lat: 11.1243,
                lon: 79.2153
            })
        });
        const data = await res.json();
        console.log(JSON.stringify(data, null, 2));
        process.exit(0);
    } catch(err) {
        console.error(err);
        process.exit(1);
    }
});
