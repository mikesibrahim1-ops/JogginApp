# Bugfix Requirements Document

## Introduction

The Joggin app has two theme-related bugs. First, the app defaults to "Sunrise Energy" theme when no theme preference is saved, but the intended default is "Midnight Pulse." Second, the Sunrise Energy theme's options/config panel (slide-in pane from the right) uses a dark navy/grey background color (secondary = #1B263B) which clashes with the theme's orange-based identity - it should use a warm orange-derived color instead.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN the app launches with no previously saved theme preference THEN the system defaults to "Sunrise Energy" (AppTheme.SUNRISE) as the active theme

1.2 WHEN a corrupted or unrecognized theme name is stored in SharedPreferences THEN the system falls back to "Sunrise Energy" (AppTheme.SUNRISE)

1.3 WHEN the Sunrise Energy theme is active and the user opens the options/config panel THEN the panel background renders in dark navy/grey color (#1B263B) which visually clashes with the orange primary color of the theme

### Expected Behavior (Correct)

2.1 WHEN the app launches with no previously saved theme preference THEN the system SHALL default to "Midnight Pulse" (AppTheme.MIDNIGHT) as the active theme

2.2 WHEN a corrupted or unrecognized theme name is stored in SharedPreferences THEN the system SHALL fall back to "Midnight Pulse" (AppTheme.MIDNIGHT)

2.3 WHEN the Sunrise Energy theme is active and the user opens the options/config panel THEN the panel background SHALL render in a warm orange-derived color that is visually consistent with the Sunrise Energy primary color palette

### Unchanged Behavior (Regression Prevention)

3.1 WHEN the user has previously saved a valid theme preference (e.g., FOREST, OCEAN, or MIDNIGHT) THEN the system SHALL CONTINUE TO load and apply that saved theme correctly

3.2 WHEN the Forest Trail, Midnight Pulse, or Ocean Breeze theme is active and the user opens the options/config panel THEN the panel background SHALL CONTINUE TO use its respective secondary color token unchanged

3.3 WHEN the user manually selects "Sunrise Energy" from the theme picker THEN the system SHALL CONTINUE TO save and apply the Sunrise Energy theme correctly (only the default fallback changes)

3.4 WHEN any theme is active THEN all other color tokens (primary, accent, background, surface, etc.) SHALL CONTINUE TO render unchanged
