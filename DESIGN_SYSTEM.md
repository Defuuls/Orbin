# Orbin Modern Material Design 3 System

## Overview

This document describes Orbin's modern, comprehensive Material Design 3 design system, implemented to provide a polished, professional, and consistent user experience across all platforms and screen sizes.

## Design Philosophy

- **Modern Minimalism**: Clean, uncluttered interfaces with purpose-driven components
- **Semantic Color Usage**: Colors convey meaning and hierarchy through Material Design 3 semantics
- **Accessibility First**: WCAG AA compliance with generous touch targets and high contrast
- **Responsive Design**: Adaptive layouts for phones, tablets, and foldable devices
- **Animation Excellence**: Smooth, purposeful transitions that don't distract
- **Consistency**: Single source of truth for all design decisions

## Theme System

### Color Schemes

The app supports multiple color schemes with dynamic color support on Android 12+:

- **Orbin**: Primary blue palette (default)
- **Tomorrow**: Curated light theme
- **Tomorrow Night**: Curated dark theme
- **Dynamic**: System wallpaper-based colors (Android 12+)
- **Imageboard Skins**: 21+ ported community themes (Yotsuba, Win95, Lain, etc.)

### Theme Modes

- **System**: Follows device theme preference
- **Light**: Always light theme
- **Dark**: Always dark theme

### AMOLED Support

Enable true black backgrounds in dark mode to reduce OLED power consumption:

```kotlin
OrbinTheme(
    themeMode = ThemeMode.DARK,
    amoled = true,  // True black backgrounds
)
```

## Shape Scale

Updated to modern Material Design 3 standards with larger radii:

| Size | Radius | Use Case |
|------|--------|----------|
| Extra Small | 8dp | Minor elements, small buttons |
| Small | 12dp | Input fields, small containers |
| Medium | 16dp | Cards, dialogs, standard containers |
| Large | 20dp | Prominent cards, major components |
| Extra Large | 28dp | Hero elements, app bars |

Specialized shapes via `ModernShapes`:
- `none` (0dp): Sharp corners
- `tiny` (4dp): Subtle rounding
- `pill` (50dp): Pills and badges

## Spacing Scale

8dp-based grid system for consistent visual rhythm:

| Token | Value | Use Case |
|-------|-------|----------|
| xs | 2dp | Micro spacing |
| xs2 | 4dp | Tight spacing |
| sm | 8dp | Default spacing |
| sm2 | 12dp | Comfortable spacing |
| md | 16dp | Content padding |
| md2 | 20dp | Section spacing |
| lg | 24dp | Large spacing |
| lg2 | 28dp | Extra large spacing |
| xl | 32dp | Spacing between sections |
| xl2 | 36dp | Generous spacing |

Usage:

```kotlin
Column(
    modifier = Modifier.padding(horizontal = md, vertical = md2),
    verticalArrangement = Arrangement.spacedBy(sm),
) {
    // Content
}
```

## Component Library

### Cards & Surfaces

#### ModernCard
Elevated card with Material Design 3 styling for content surfaces.

**Features:**
- 4-8dp elevation with shadow
- Configurable colors
- Click handlers with ripple
- Responsive sizing

```kotlin
ModernCard(
    onClick = { /* Handle click */ },
    modifier = Modifier.fillMaxWidth(),
) {
    Text("Card content")
}
```

### Buttons

Three levels of emphasis following Material Design 3:

#### ModernButton (Primary)
High emphasis, primary actions.

```kotlin
ModernButton(
    label = "Save",
    onClick = { /* Save */ },
    leadingIcon = { Icon(Icons.Default.Check, null) },
)
```

#### ModernTonalButton (Secondary)
Medium emphasis, secondary actions.

```kotlin
ModernTonalButton(
    label = "Discard",
    onClick = { /* Discard */ },
)
```

#### ModernOutlinedButton (Tertiary)
Low emphasis, tertiary actions.

```kotlin
ModernOutlinedButton(
    label = "Cancel",
    onClick = { /* Cancel */ },
)
```

### Chips

#### ModernFilterChip
For categorical selection with toggleable state.

```kotlin
ModernFilterChip(
    label = "Technology",
    selected = true,
    onSelectedChange = { selected -> /* Update */ },
)
```

#### ModernInputChip
For user selections with removal capability.

```kotlin
ModernInputChip(
    label = "Selected Tag",
    onRemove = { /* Remove */ },
)
```

#### ModernAssistChip
For actions and suggestions.

```kotlin
ModernAssistChip(
    label = "Suggest",
    onClick = { /* Action */ },
)
```

#### ModernSuggestionChip
For search suggestions and recommendations.

```kotlin
ModernSuggestionChip(
    label = "Popular search",
    onClick = { /* Select */ },
)
```

#### ChipGroup
Horizontal scrollable row of filter chips.

```kotlin
ChipGroup(
    chips = listOf("Tech", "Science", "News"),
    selectedChips = setOf("Tech"),
    onChipClick = { chip, selected -> /* Update */ },
)
```

### Top App Bars

#### ModernSmallTopAppBar
Standard header bar for most screens.

```kotlin
ModernSmallTopAppBar(
    title = "Settings",
    navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
    onNavigationClick = { onBack() },
    actions = {
        IconButton(onClick = { /* Action */ }) {
            Icon(Icons.Filled.Settings, null)
        }
    },
)
```

#### ModernCenterTopAppBar
Centered title for focused layouts.

```kotlin
ModernCenterTopAppBar(
    title = "Boards",
    navigationIcon = Icons.Filled.Close,
    onNavigationClick = { dismiss() },
)
```

#### ModernLargeTopAppBar
Collapsible hero bar for prominent screens.

```kotlin
ModernLargeTopAppBar(
    title = "Feed",
    scrollBehavior = scrollBehavior,
)
```

#### ModernSearchTopAppBar
Integrated search field.

```kotlin
ModernSearchTopAppBar(
    searchQuery = query,
    onSearchQueryChange = { query = it },
)
```

### List Items

#### ModernListItem
Flexible list item with optional leading/trailing content.

```kotlin
ModernListItem(
    title = "Board Name",
    subtitle = "Description",
    leading = { Icon(Icons.Default.Book, null) },
    trailing = { Switch(checked = true, onCheckedChange = {}) },
    onClick = { /* Navigate */ },
)
```

#### ModernCompactListItem
Dense variant for tight layouts.

```kotlin
ModernCompactListItem(
    title = "Option",
    leading = Icons.Default.Settings,
    onClick = { /* Action */ },
)
```

#### ModernCardListItem
Elevated card-based list item for prominence.

```kotlin
ModernCardListItem(
    title = "Featured Board",
    subtitle = "Category",
    description = "Long description...",
    onClick = { /* Navigate */ },
)
```

### Navigation

#### ModernNavigationBar
Bottom navigation for mobile layouts.

```kotlin
ModernNavigationBar {
    ModernNavigationBarItem(
        icon = Icons.Default.Home,
        label = "Home",
        selected = true,
        onClick = { /* Navigate */ },
    )
    // More items...
}
```

#### ModernNavigationRail
Vertical navigation for tablet/landscape.

```kotlin
ModernNavigationRail {
    ModernNavigationRailItem(
        icon = Icons.Default.Settings,
        label = "Settings",
        selected = false,
        onClick = { /* Navigate */ },
    )
    // More items...
}
```

### Loading States

#### SkeletonLoader
Animated placeholder for loading content.

```kotlin
SkeletonLoader(
    width = 200.dp,
    height = 16.dp,
)
```

#### PulsingDotLoader
Three-dot pulse animation.

```kotlin
PulsingDotLoader(
    dotColor = MaterialTheme.colorScheme.primary,
)
```

#### ScalingProgressIndicator
Scaling circular progress with animation.

```kotlin
ScalingProgressIndicator(
    size = 56.dp,
)
```

#### LoadingSkeletonList
Multiple skeleton loaders for list placeholders.

```kotlin
LoadingSkeletonList(
    itemCount = 3,
)
```

## Usage Examples

### Complete Settings Screen
```kotlin
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            ModernSmallTopAppBar(
                title = "Settings",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = md),
        ) {
            Text("Appearance", style = MaterialTheme.typography.labelLarge)
            ModernListItem(
                title = "Dark Theme",
                trailing = { Switch(checked = true, onCheckedChange = {}) },
            )
        }
    }
}
```

### Modern Board List
```kotlin
@Composable
fun BoardsList(boards: List<Board>) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = sm2, vertical = sm),
        verticalArrangement = Arrangement.spacedBy(sm),
    ) {
        items(boards) { board ->
            ModernCardListItem(
                title = "/${board.id}/ - ${board.title}",
                description = board.description,
                onClick = { /* Navigate */ },
            )
        }
    }
}
```

## Accessibility

### Touch Targets
Minimum 48dp, preferably 56dp for all interactive elements:

```kotlin
IconButton(
    onClick = { /* Action */ },
    modifier = Modifier.size(56.dp),
)
```

### Color Contrast
All text meets WCAG AA standards (4.5:1 for body, 3:1 for large text).

### Semantic Colors
Use semantic color tokens for meaning:
- `primary`: Primary actions and highlights
- `secondary`: Supporting information
- `error`: Errors and destructive actions
- `onSurface`: Default text color

## Dark Mode Considerations

- Text is lighter on dark backgrounds
- Surface brightness reduced for eye comfort
- AMOLED mode available for true black on OLED screens
- All components tested for sufficient contrast

## Responsive Design

### Phone Layout
- Single column, full width
- Bottom navigation
- Simplified navigation hierarchy

### Tablet Layout
- Multi-column layouts
- Navigation rail or split screen
- Larger touch targets where space available

### Foldable Support
- Respects hinge locations
- Adaptive layouts for different orientations
- Window size classes for responsive breakpoints

## Animation Guidelines

### Durations
- **Quick**: 150ms - UI feedback, ripples
- **Standard**: 300ms - Navigation, state changes
- **Emphasized**: 500ms - Hero elements, complex animations

### Easing Functions
- **FastOutSlowInEasing**: Standard Material curve
- **EaseInOutCubic**: Specialized animations
- **LinearEasing**: Constant-speed animations

### Best Practices
1. Use animations for feedback, not decoration
2. Keep animations subtle and purposeful
3. Don't animate every state change
4. Consider performance on low-end devices

## Implementing New Screens

1. **Use modern components**: Prefer `Modern*` components over base Material 3
2. **Follow spacing scale**: Use defined spacing tokens consistently
3. **Semantic colors**: Use primary/secondary/error appropriately
4. **Responsive layout**: Test on phone and tablet
5. **Dark mode**: Verify in dark theme with AMOLED option
6. **Accessibility**: Check touch targets and contrast

## Migration Guide

### From Old Components

**Before:**
```kotlin
ListItem(
    headlineContent = { Text("Title") },
    supportingContent = { Text("Subtitle") },
)
```

**After:**
```kotlin
ModernListItem(
    title = "Title",
    subtitle = "Subtitle",
)
```

## File Organization

```
core/designsystem/
├── theme/
│   ├── Color.kt          # Color palette
│   ├── Type.kt           # Typography
│   ├── Shape.kt          # Corner radii
│   ├── Spacing.kt        # 8dp spacing grid
│   └── Theme.kt          # Theme composition
└── component/
    ├── ModernCard.kt          # Cards and buttons
    ├── ModernChip.kt          # Chip variants
    ├── ModernTopBar.kt        # App bars
    ├── ModernListItems.kt     # List items
    ├── ModernAnimations.kt    # Loading states
    ├── ModernNavigation.kt    # Navigation components
    └── index.kt               # Documentation
```

## Performance Considerations

- LazyColumn/LazyRow for long lists
- Keys for list items to prevent recomposition
- Remember{ } for expensive computations
- Avoid unnecessary recompositions with derivedStateOf

## Future Enhancements

- [ ] Material Design Motion spec compliance
- [ ] Extended color palette
- [ ] Custom gesture handling
- [ ] Accessibility testing suite
- [ ] Component preview catalog
- [ ] Figma design kit sync

## References

- [Material Design 3 Guidelines](https://m3.material.io/)
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [WCAG 2.1 Accessibility Guidelines](https://www.w3.org/WAI/WCAG21/quickref/)

---

**Last Updated**: 2026-07-31  
**Version**: 1.0  
**Status**: Production Ready
