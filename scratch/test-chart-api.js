const axios = require('axios');
const fs = require('fs');

async function test() {
  try {
    const payload = {
      date: "1990-01-01",
      time: "12:00",
      lat: 13.0827,
      lng: 80.2707,
      timezone: 5.5
    };
    const response = await axios.post('https://astrohark.com/api/rasi-eng/charts/full', payload);
    fs.writeFileSync('chart-data.json', JSON.stringify(response.data, null, 2));
    console.log("Success! check chart-data.json");
  } catch(e) {
    console.error(e.response ? e.response.data : e.message);
  }
}
test();
