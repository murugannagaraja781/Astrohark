const express = require('express');
const chartsRouter = require('./routes/rasiEng/charts');
const app = express();
app.use(express.json());
app.use('/api/rasi-eng/charts', chartsRouter);
app.listen(3000, () => {
    console.log('Test server on 3000');
});
