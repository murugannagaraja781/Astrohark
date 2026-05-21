const fs = require('fs');
const path = '/Users/wohozo/Documents/Astrohark/astroapp/android/app/src/main/java/com/astrohark/app/ui/chart/VipChartActivity.kt';
let code = fs.readFileSync(path, 'utf8');

const func = `
fun getPlanetStatusTamil(planetName: String, signName: String): String {
    return when (planetName) {
        "Sun" -> when (signName) { "Aries" -> "உச்சம்"; "Libra" -> "நீசம்"; "Leo" -> "ஆட்சி"; "Sagittarius", "Pisces", "Scorpio", "Cancer" -> "நட்பு"; "Taurus", "Capricorn", "Aquarius" -> "பகை"; "Gemini", "Virgo" -> "சமம்"; else -> "சமம்" }
        "Moon" -> when (signName) { "Taurus" -> "உச்சம்"; "Scorpio" -> "நீசம்"; "Cancer" -> "ஆட்சி"; "Aries", "Leo", "Sagittarius", "Pisces" -> "நட்பு"; "Gemini", "Virgo", "Capricorn", "Aquarius", "Libra" -> "சமம்"; else -> "சமம்" }
        "Mars" -> when (signName) { "Capricorn" -> "உச்சம்"; "Cancer" -> "நீசம்"; "Aries", "Scorpio" -> "ஆட்சி"; "Leo", "Sagittarius", "Pisces" -> "நட்பு"; "Gemini", "Virgo" -> "பகை"; "Taurus", "Libra", "Aquarius" -> "சமம்"; else -> "சமம்" }
        "Mercury" -> when (signName) { "Virgo" -> "உச்சம்/ஆட்சி"; "Pisces" -> "நீசம்"; "Gemini" -> "ஆட்சி"; "Taurus", "Leo", "Libra" -> "நட்பு"; "Cancer" -> "பகை"; "Aries", "Scorpio", "Sagittarius", "Capricorn", "Aquarius" -> "சமம்"; else -> "சமம்" }
        "Jupiter" -> when (signName) { "Cancer" -> "உச்சம்"; "Capricorn" -> "நீசம்"; "Sagittarius", "Pisces" -> "ஆட்சி"; "Aries", "Leo", "Scorpio" -> "நட்பு"; "Taurus", "Gemini", "Virgo", "Libra" -> "பகை"; "Aquarius" -> "சமம்"; else -> "சமம்" }
        "Venus" -> when (signName) { "Pisces" -> "உச்சம்"; "Virgo" -> "நீசம்"; "Taurus", "Libra" -> "ஆட்சி"; "Gemini", "Capricorn", "Aquarius" -> "நட்பு"; "Cancer", "Leo" -> "பகை"; "Aries", "Scorpio", "Sagittarius" -> "சமம்"; else -> "சமம்" }
        "Saturn" -> when (signName) { "Libra" -> "உச்சம்"; "Aries" -> "நீசம்"; "Capricorn", "Aquarius" -> "ஆட்சி"; "Taurus", "Gemini", "Virgo" -> "நட்பு"; "Cancer", "Leo", "Scorpio" -> "பகை"; "Sagittarius", "Pisces" -> "சமம்"; else -> "சமம்" }
        "Rahu" -> when (signName) { "Taurus" -> "உச்சம்"; "Scorpio" -> "நீசம்"; "Virgo", "Aquarius" -> "ஆட்சி"; else -> "நட்பு" }
        "Ketu" -> when (signName) { "Scorpio" -> "உச்சம்"; "Taurus" -> "நீசம்"; "Pisces", "Aries" -> "ஆட்சி"; else -> "நட்பு" }
        else -> "-"
    }
}
`;

if (!code.includes('getPlanetStatusTamil')) {
    code = code.replace(/fun getMonthName\(m: Int\): String = \[^\]]*\]\[m\]/, match => match + '\n' + func);
}

code = code.replace(
    /Text\(text = "\$\{planet\.house\}-ம் வீடு", color = Color\.Blue, fontSize = 11\.sp, modifier = Modifier\.weight\(1f\), textAlign = TextAlign\.Center\)/g,
    'Text(text = getPlanetStatusTamil(planet.name, planet.signName), color = Color.Blue, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)'
);

fs.writeFileSync(path, code);
