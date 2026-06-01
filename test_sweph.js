const sweph = require('sweph-wasm');

async function test() {
    try {
        console.log("sweph exports:", Object.keys(sweph));
        // sweph is probably an ES module or needs initialization
        const ephemerisData = await sweph.swe_calc_ut(2460000, 0, 2); // 2 = SEFLG_SWIEPH
        console.log("Data:", ephemerisData);
    } catch(e) {
        console.error("Error:", e);
    }
}
test();
