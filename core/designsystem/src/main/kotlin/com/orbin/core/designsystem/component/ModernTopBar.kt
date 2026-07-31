package com.orbin.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Modern centered top app bar with Material Design 3 styling.
 * Used for main screen headers with title-focused layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernCenterTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
    onNavigationClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    CenterAlignedTopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        modifier = modifier,
        navigationIcon =
            if (navigationIcon != null && onNavigationClick != null) {
                {
                    IconButton(onClick = onNavigationClick) {
                        Icon(navigationIcon, contentDescription = "Navigate back")
                    }
                }
            } else {
                {}
            },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

/**
 * Modern small top app bar with consistent styling.
 * Default header for list screens and detail views.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernSmallTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
    onNavigationClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        modifier = modifier,
        navigationIcon =
            if (navigationIcon != null && onNavigationClick != null) {
                {
                    IconButton(onClick = onNavigationClick) {
                        Icon(navigationIcon, contentDescription = "Navigate back")
                    }
                }
            } else {
                {}
            },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

/**
 * Modern large top app bar with collapsible title.
 * Used for hero screens with visual prominence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernLargeTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
    onNavigationClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    LargeTopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        modifier = modifier,
        navigationIcon =
            if (navigationIcon != null && onNavigationClick != null) {
                {
                    IconButton(onClick = onNavigationClick) {
                        Icon(navigationIcon, contentDescription = "Navigate back")
                    }
                }
            } else {
                {}
            },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

/**
 * Modern medium top app bar with subtitle support.
 * Used for screens with hierarchical information.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernMediumTopAppBar(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
    onNavigationClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    MediumTopAppBar(
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        modifier = modifier,
        navigationIcon =
            if (navigationIcon != null && onNavigationClick != null) {
                {
                    IconButton(onClick = onNavigationClick) {
                        Icon(navigationIcon, contentDescription = "Navigate back")
                    }
                }
            } else {
                {}
            },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

/**
 * Modern back button with consistent styling.
 * Standard navigation icon for detail screens.
 */
@Composable
fun ModernBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
    }
}

/**
 * Modern search top app bar with search field.
 * Used for filterable lists and search screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernSearchTopAppBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNavigationClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            SearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onSearch = {},
                active = false,
                onActiveChange = {},
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "Search",
                    )
                },
                trailingIcon =
                    if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    } else {
                        null
                    },
            )
        },
        modifier = modifier,
        navigationIcon =
            if (onNavigationClick != null) {
                {
                    IconButton(onClick = onNavigationClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
                    }
                }
            } else {
                {}
            },
        actions = actions,
    )
}

/**
 * Modern floating action bar positioned at the bottom.
 * Used for contextual actions in lists with visibility control.
 */
@Composable
fun ModernFloatingActionBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
