package io.legado.app.inkpaper.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.legado.app.inkpaper.design.InkTheme
import io.legado.app.inkpaper.ui.bookdetail.InkBookDetailScreen
import io.legado.app.inkpaper.ui.bookshelf.InkBookshelfScreen
import io.legado.app.inkpaper.ui.discover.InkDiscoverScreen
import io.legado.app.inkpaper.ui.discover.InkExploreBooksScreen
import io.legado.app.inkpaper.ui.me.InkAdvancedToolsScreen
import io.legado.app.inkpaper.ui.me.InkAllBookmarksScreen
import io.legado.app.inkpaper.ui.me.InkMeScreen
import io.legado.app.inkpaper.ui.me.InkReadRecordsScreen
import io.legado.app.inkpaper.ui.search.InkSearchScreen
import io.legado.app.inkpaper.ui.settings.InkBackupScreen
import io.legado.app.inkpaper.ui.settings.InkReadHabitsScreen
import io.legado.app.inkpaper.ui.settings.InkReplaceRulesScreen
import io.legado.app.inkpaper.ui.sources.InkBookSourcesScreen

private object InkRoute {
    const val BOOKSHELF = "bookshelf"
    const val DISCOVER = "discover"
    const val ME = "me"
    const val SEARCH = "search?q={q}"
    const val BOOK_DETAIL = "book_detail/{bookKey}"
    const val EXPLORE = "explore/{sourceKey}"
    const val BOOK_SOURCES = "book_sources"
    const val REPLACE_RULES = "replace_rules"
    const val BACKUP = "backup"
    const val READ_HABITS = "read_habits"
    const val READ_RECORDS = "read_records"
    const val ALL_BOOKMARKS = "all_bookmarks"
    const val ADVANCED_TOOLS = "advanced_tools"

    fun search(query: String? = null): String =
        if (query.isNullOrBlank()) "search" else "search?q=${InkNavCache.encodeKey(query)}"
    fun bookDetail(bookUrl: String): String = "book_detail/${InkNavCache.encodeKey(bookUrl)}"
    fun explore(sourceUrl: String): String = "explore/${InkNavCache.encodeKey(sourceUrl)}"
}

private data class InkTabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val mainTabs = listOf(
    InkTabItem(InkRoute.BOOKSHELF, "书架", Icons.AutoMirrored.Outlined.MenuBook),
    InkTabItem(InkRoute.DISCOVER, "发现", Icons.Outlined.Explore),
    InkTabItem(InkRoute.ME, "我的", Icons.Outlined.PersonOutline),
)

@Composable
fun InkAppRoot(
    onTabRouteChanged: (String?) -> Unit = {},
) {
    val colors = InkTheme.colors
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    onTabRouteChanged(currentRoute)

    val showBottomBar = currentRoute in setOf(
        InkRoute.BOOKSHELF,
        InkRoute.DISCOVER,
        InkRoute.ME,
    )

    Scaffold(
        containerColor = colors.bg,
        bottomBar = {
            if (showBottomBar) {
                InkBottomBar(
                    currentRoute = currentRoute,
                    onSelectTab = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = InkRoute.BOOKSHELF,
            enterTransition = {
                fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 14 }
            },
            exitTransition = {
                fadeOut(tween(160))
            },
            popEnterTransition = {
                fadeIn(tween(200))
            },
            popExitTransition = {
                fadeOut(tween(180)) + slideOutHorizontally(tween(200)) { it / 14 }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable(InkRoute.BOOKSHELF) {
                InkBookshelfScreen(
                    onOpenSearch = { navController.navigate(InkRoute.search()) },
                    onOpenDetail = { book ->
                        InkNavCache.putBook(book)
                        navController.navigate(InkRoute.bookDetail(book.bookUrl))
                    },
                    onOpenBookSources = { navController.navigate(InkRoute.BOOK_SOURCES) },
                    onOpenBackup = { navController.navigate(InkRoute.BACKUP) },
                )
            }
            composable(InkRoute.DISCOVER) {
                InkDiscoverScreen(
                    onOpenSearch = { navController.navigate(InkRoute.search()) },
                    onOpenExplore = { sourceUrl ->
                        navController.navigate(InkRoute.explore(sourceUrl))
                    },
                    onOpenBookSources = { navController.navigate(InkRoute.BOOK_SOURCES) },
                )
            }
            composable(InkRoute.ME) {
                InkMeScreen(
                    onOpenReadHabits = { navController.navigate(InkRoute.READ_HABITS) },
                    onOpenBookSources = { navController.navigate(InkRoute.BOOK_SOURCES) },
                    onOpenReplaceRules = { navController.navigate(InkRoute.REPLACE_RULES) },
                    onOpenBackup = { navController.navigate(InkRoute.BACKUP) },
                    onOpenReadRecords = { navController.navigate(InkRoute.READ_RECORDS) },
                    onOpenAllBookmarks = { navController.navigate(InkRoute.ALL_BOOKMARKS) },
                    onOpenAdvancedTools = { navController.navigate(InkRoute.ADVANCED_TOOLS) },
                )
            }
            composable(
                route = InkRoute.SEARCH,
                arguments = listOf(
                    navArgument("q") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val rawQuery = entry.arguments?.getString("q").orEmpty()
                val initialQuery = if (rawQuery.isBlank()) "" else InkNavCache.decodeKey(rawQuery)
                InkSearchScreen(
                    onBack = { navController.popBackStack() },
                    onSelectResult = { searchBook ->
                        InkNavCache.putSearchBook(searchBook)
                        navController.navigate(InkRoute.bookDetail(searchBook.bookUrl))
                    },
                    initialQuery = initialQuery,
                    onOpenBookSources = { navController.navigate(InkRoute.BOOK_SOURCES) },
                    onBookAddedByUrl = { book ->
                        InkNavCache.putBook(book)
                        navController.navigate(InkRoute.bookDetail(book.bookUrl))
                    },
                )
            }
            composable(
                route = InkRoute.BOOK_DETAIL,
                arguments = listOf(navArgument("bookKey") { type = NavType.StringType }),
            ) { entry ->
                val encoded = entry.arguments?.getString("bookKey").orEmpty()
                val bookUrl = InkNavCache.decodeKey(encoded)
                InkBookDetailScreen(
                    bookKey = bookUrl,
                    onBack = { navController.popBackStack() },
                    onSearchAuthor = { author ->
                        navController.navigate(InkRoute.search(author))
                    },
                )
            }
            composable(
                route = InkRoute.EXPLORE,
                arguments = listOf(navArgument("sourceKey") { type = NavType.StringType }),
            ) { entry ->
                val encoded = entry.arguments?.getString("sourceKey").orEmpty()
                val sourceUrl = InkNavCache.decodeKey(encoded)
                InkExploreBooksScreen(
                    sourceUrl = sourceUrl,
                    onBack = { navController.popBackStack() },
                    onSelectBook = { searchBook ->
                        InkNavCache.putSearchBook(searchBook)
                        navController.navigate(InkRoute.bookDetail(searchBook.bookUrl))
                    },
                )
            }
            composable(InkRoute.BOOK_SOURCES) {
                InkBookSourcesScreen(onBack = { navController.popBackStack() })
            }
            composable(InkRoute.REPLACE_RULES) {
                InkReplaceRulesScreen(onBack = { navController.popBackStack() })
            }
            composable(InkRoute.BACKUP) {
                InkBackupScreen(onBack = { navController.popBackStack() })
            }
            composable(InkRoute.READ_HABITS) {
                InkReadHabitsScreen(onBack = { navController.popBackStack() })
            }
            composable(InkRoute.READ_RECORDS) {
                InkReadRecordsScreen(onBack = { navController.popBackStack() })
            }
            composable(InkRoute.ALL_BOOKMARKS) {
                InkAllBookmarksScreen(onBack = { navController.popBackStack() })
            }
            composable(InkRoute.ADVANCED_TOOLS) {
                InkAdvancedToolsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun InkBottomBar(
    currentRoute: String?,
    onSelectTab: (String) -> Unit,
) {
    val colors = InkTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            mainTabs.forEach { tab ->
                val selected = currentRoute == tab.route
                val tint = if (selected) colors.ink else colors.inkSecondary
                val interactionSource = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { onSelectTab(tab.route) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = tab.label,
                        color = tint,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
