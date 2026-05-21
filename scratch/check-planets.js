const axios = require('axios');
async function test() {
    const payload = {
        date: "1990-05-15",
        time: "10:30",
        lat: 13.0827,
        lng: 80.2707,
        timezone: 5.5
    };
    try {
        const res = await axios.post("http://localhost:3000/api/chart", payload); // Or whatever the API is
        console.log(JSON.stringify(res.data.data.planets[0], null, 2));
    } catch(e) { console.error(e.message); }
}
test();
