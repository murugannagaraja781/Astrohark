const fs = require('fs');
const path = '/Users/wohozo/Documents/Astrohark/astroapp/android/app/src/main/java/com/astrohark/app/IncomingCallActivity.kt';
let code = fs.readFileSync(path, 'utf8');

// replace background brush with orange brush
const brushDef = `
    val orangeBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(Color(0xFFFF9800), Color(0xFFF57C00), Color(0xFFE65100))
    )
`;

code = code.replace(/val pulseAlpha by infiniteTransition\.animateFloat[\s\S]*?label = "pulseAlpha"\n    \)/, match => match + '\n' + brushDef);
code = code.replace(/background\(CosmicAppTheme\.backgroundBrush\)/, 'background(orangeBrush)');

// replace text colors
code = code.replace(/color = CosmicAppTheme\.colors\.accent/g, 'color = Color.White');
code = code.replace(/color = CosmicAppTheme\.colors\.textPrimary/g, 'color = Color.White');
code = code.replace(/color = CosmicAppTheme\.colors\.textSecondary/g, 'color = Color.White.copy(alpha = 0.8f)');
code = code.replace(/tint = CosmicAppTheme\.colors\.accent/g, 'tint = Color(0xFFE65100)');
code = code.replace(/border = androidx\.compose\.foundation\.BorderStroke\(2\.dp, CosmicAppTheme\.colors\.accent\.copy\(alpha = 0\.5f\)\)/g, 'border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.5f))');
code = code.replace(/color = CosmicAppTheme\.colors\.cardBg/g, 'color = Color.White');


fs.writeFileSync(path, code);
