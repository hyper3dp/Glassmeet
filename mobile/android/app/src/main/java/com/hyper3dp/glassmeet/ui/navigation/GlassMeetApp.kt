package com.hyper3dp.glassmeet.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.hyper3dp.glassmeet.data.friendly
import com.hyper3dp.glassmeet.ui.components.ErrorState
import com.hyper3dp.glassmeet.ui.components.LoadingState
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.hyper3dp.glassmeet.data.AuthRepository
import com.hyper3dp.glassmeet.data.ProfileRepository
import com.hyper3dp.glassmeet.ui.analytics.AnalyticsScreen
import com.hyper3dp.glassmeet.ui.auth.AuthFlow
import com.hyper3dp.glassmeet.ui.auth.CreateAccountScreen
import com.hyper3dp.glassmeet.ui.auth.ResetPasswordScreen
import com.hyper3dp.glassmeet.ui.auth.SignInScreen
import com.hyper3dp.glassmeet.ui.auth.VerifyCodeScreen
import com.hyper3dp.glassmeet.ui.availability.AvailabilityScreen
import com.hyper3dp.glassmeet.ui.calendar.CalendarConnectionsScreen
import com.hyper3dp.glassmeet.ui.home.HomeScreen
import com.hyper3dp.glassmeet.ui.meetings.MeetingScreen
import com.hyper3dp.glassmeet.ui.meetings.MeetingsScreen
import com.hyper3dp.glassmeet.ui.scheduling.EventTypeFormScreen
import com.hyper3dp.glassmeet.ui.scheduling.SchedulingScreen
import com.hyper3dp.glassmeet.ui.settings.SettingsScreen
import com.hyper3dp.glassmeet.ui.settings.WhatsAppScreen
import com.hyper3dp.glassmeet.util.Snack
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.auth
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Add
import com.hyper3dp.glassmeet.ui.notifications.NotificationsScreen
import com.hyper3dp.glassmeet.util.Intents

@Composable
fun GlassMeetApp() {
    val status by AuthRepository.sessionStatus.collectAsStateWithLifecycle()
    val recovering by AuthFlow.recovering.collectAsStateWithLifecycle()
    val signedIn = status is SessionStatus.Authenticated || status is SessionStatus.RefreshFailure
    when {
        status is SessionStatus.Initializing -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        signedIn && !recovering -> ProfileGate { SignedInApp() }
        else -> SignedOutApp(startOnReset = recovering)
    }
}

/** The app needs the host's profile (name, time zone): load it first, or say why it didn't load. */
@Composable
private fun ProfileGate(content: @Composable () -> Unit) {
    val profile by ProfileRepository.profile.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(attempt) {
        if (ProfileRepository.profile.value == null) {
            error = null
            try {
                ProfileRepository.load()
            } catch (e: Exception) {
                error = e.friendly("Your profile didn't load.")
            }
        }
    }
    val message = error
    when {
        profile != null -> content()
        message != null -> Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f)) { ErrorState(message, onRetry = { attempt++ }) }
                TextButton(onClick = { scope.launch { AuthRepository.signOut() } }, modifier = Modifier.padding(bottom = 24.dp)) { Text("Sign out") }
            }
        }
        else -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { LoadingState() }
    }
}

// ── Signed out: Sign in, Create account, Verify code, Reset password ──

@Composable
private fun SignedOutApp(startOnReset: Boolean) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = if (startOnReset) ResetPasswordRoute else SignInRoute) {
        composable<SignInRoute> {
            SignInScreen(
                onCreateAccount = { nav.navigate(CreateAccountRoute) },
                onForgotPassword = { nav.navigate(ResetPasswordRoute) },
                onVerify = { email -> nav.navigate(VerifyCodeRoute(email)) },
            )
        }
        composable<CreateAccountRoute> {
            CreateAccountScreen(onBack = { nav.popBackStack() }, onVerify = { email -> nav.navigate(VerifyCodeRoute(email)) })
        }
        composable<VerifyCodeRoute> { entry ->
            VerifyCodeScreen(email = entry.toRoute<VerifyCodeRoute>().email, onBack = { nav.popBackStack() })
        }
        composable<ResetPasswordRoute> {
            ResetPasswordScreen(onBack = { if (!nav.popBackStack()) nav.navigate(SignInRoute) })
        }
    }
}

// ── Signed in: five destinations in a Material 3 navigation bar ──

private data class Tab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val graph: Any,
    val start: Any,
    val matches: (NavDestination) -> Boolean,
)

private val tabs = listOf(
    Tab("Home", Icons.Outlined.Home, Icons.Filled.Home, HomeGraph, HomeRoute) { it.hasRoute<HomeGraph>() },
    Tab("Meetings", Icons.Outlined.Groups, Icons.Filled.Groups, MeetingsGraph, MeetingsRoute) { it.hasRoute<MeetingsGraph>() },
    Tab("Scheduling", Icons.Outlined.Link, Icons.Filled.Link, SchedulingGraph, SchedulingRoute) { it.hasRoute<SchedulingGraph>() },
    Tab("Availability", Icons.Outlined.Schedule, Icons.Filled.Schedule, AvailabilityGraph, AvailabilityRoute) { it.hasRoute<AvailabilityGraph>() },
    Tab("Settings", Icons.Outlined.Settings, Icons.Filled.Settings, SettingsGraph, SettingsRoute) { it.hasRoute<SettingsGraph>() },
)

private fun NavHostController.switchTo(route: Any) {
    // leaving Settings from one of its pages takes Settings back to its main list
    val leavingSettings = route != SettingsGraph && currentDestination?.hierarchy?.any { it.hasRoute<SettingsGraph>() } == true
    if (leavingSettings) popBackStack(SettingsRoute, inclusive = false)
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Opens a GlassMeet web path (a notification's link, or a link from outside the app). */
private fun NavHostController.openWebPath(link: String) {
    val uri = android.net.Uri.parse(link)
    when (uri.path?.trim('/')) {
        "bookings" -> {
            switchTo(MeetingsGraph)
            uri.getQueryParameter("id")?.let { navigate(MeetingRoute(it)) { launchSingleTop = true } }
        }
        "availability" -> switchTo(AvailabilityGraph)
        "calendar-connections" -> { switchTo(SettingsGraph); navigate(CalendarConnectionsRoute) { launchSingleTop = true } }
        "event-types" -> switchTo(SchedulingGraph)
        "dashboard", "" -> switchTo(HomeGraph)
    }
}

@Composable
private fun SignedInApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val userId = remember { com.hyper3dp.glassmeet.data.supabase.auth.currentUserOrNull()?.id }
    // a new account sees onboarding once; hosts who already have event types or meetings skip it
    var onboarding by remember { mutableStateOf<Boolean?>(if (userId == null || com.hyper3dp.glassmeet.ui.onboarding.Onboarding.isDone(context, userId)) false else null) }
    LaunchedEffect(userId) {
        if (onboarding != null || userId == null) return@LaunchedEffect
        val events = runCatching { com.hyper3dp.glassmeet.data.EventTypesRepository.list() }
        val bookings = runCatching { com.hyper3dp.glassmeet.data.BookingsRepository.list(includeExpired = true) }
        val isNew = events.getOrNull()?.isEmpty() == true && bookings.getOrNull()?.isEmpty() == true
        if (!isNew) com.hyper3dp.glassmeet.ui.onboarding.Onboarding.markDone(context, userId)
        onboarding = isNew
    }
    when (onboarding) {
        null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { LoadingState() }
        true -> com.hyper3dp.glassmeet.ui.onboarding.OnboardingScreen(hasEventTypes = false) {
            if (userId != null) com.hyper3dp.glassmeet.ui.onboarding.Onboarding.markDone(context, userId)
            onboarding = false
        }
        false -> MainApp()
    }
}

@Composable
private fun MainApp() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val snackbar = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { Snack.messages.collect { snackbar.showSnackbar(it) } }

    // booking messages: checked every minute while the app is on screen, like the web's bell
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                com.hyper3dp.glassmeet.data.NotificationsRepository.refresh()
                kotlinx.coroutines.delay(60_000)
            }
        }
    }

    // a calendar connection came back from the browser: open its page, which finishes it
    val calendarCallback by NavRequests.calendarCallback.collectAsStateWithLifecycle()
    LaunchedEffect(calendarCallback) {
        if (calendarCallback != null && destination?.hasRoute<CalendarConnectionsRoute>() != true) {
            nav.switchTo(SettingsGraph)
            nav.navigate(CalendarConnectionsRoute) { launchSingleTop = true }
        }
    }

    // a GlassMeet link opened from outside: the host's own event type opens its editor, anyone
    // else's booking page opens in a browser tab, meeting links open the meeting
    val openLink by NavRequests.openLink.collectAsStateWithLifecycle()
    LaunchedEffect(openLink) {
        val link = openLink ?: return@LaunchedEffect
        NavRequests.openLink.value = null
        if (link.startsWith("book:")) {
            val id = link.removePrefix("book:")
            val mine = runCatching { com.hyper3dp.glassmeet.data.EventTypesRepository.list().any { it.id == id } }.getOrDefault(false)
            if (mine) {
                nav.switchTo(SchedulingGraph)
                nav.navigate(EventTypeFormRoute(id)) { launchSingleTop = true }
            } else {
                Intents.customTab(context, com.hyper3dp.glassmeet.data.Backend.bookingUrl(id))
            }
        } else {
            nav.openWebPath(link)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = destination?.hierarchy?.any { tab.matches(it) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            // tapping the current tab again goes back to its first page
                            if (selected) nav.popBackStack(tab.start, inclusive = false).let { if (!it) nav.switchTo(tab.graph) }
                            else nav.switchTo(tab.graph)
                        },
                        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                        label = { Text(tab.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = HomeGraph,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(padding),
        ) {
            navigation<HomeGraph>(startDestination = HomeRoute) {
                composable<HomeRoute> {
                    HomeScreen(
                        onOpenMeeting = { id -> nav.navigate(HomeMeetingRoute(id)) },
                        onOpenRequests = {
                            NavRequests.meetingsFilter.value = "pending"
                            nav.switchTo(MeetingsGraph)
                        },
                        onOpenMeetings = { nav.switchTo(MeetingsGraph) },
                        onOpenAnalytics = { nav.navigate(AnalyticsRoute) },
                        onOpenScheduling = { nav.switchTo(SchedulingGraph) },
                        onOpenNotifications = { nav.navigate(NotificationsRoute) { launchSingleTop = true } },
                    )
                }
                composable<NotificationsRoute> {
                    NotificationsScreen(onBack = { nav.popBackStack() }, onOpen = { link -> nav.openWebPath(link) })
                }
                composable<AnalyticsRoute> { AnalyticsScreen(onBack = { nav.popBackStack() }) }
                composable<HomeMeetingRoute> { e -> MeetingScreen(id = e.toRoute<HomeMeetingRoute>().id, onBack = { nav.popBackStack() }) }
            }
            navigation<MeetingsGraph>(startDestination = MeetingsRoute) {
                composable<MeetingsRoute> { MeetingsScreen(onOpenMeeting = { id -> nav.navigate(MeetingRoute(id)) }) }
                composable<MeetingRoute> { e -> MeetingScreen(id = e.toRoute<MeetingRoute>().id, onBack = { nav.popBackStack() }) }
            }
            navigation<SchedulingGraph>(startDestination = SchedulingRoute) {
                composable<SchedulingRoute> {
                    SchedulingScreen(
                        onEdit = { id -> nav.navigate(EventTypeFormRoute(id)) },
                        onNew = { nav.navigate(EventTypeFormRoute(null)) },
                    )
                }
                composable<EventTypeFormRoute> { e -> EventTypeFormScreen(id = e.toRoute<EventTypeFormRoute>().id, onBack = { nav.popBackStack() }) }
            }
            navigation<AvailabilityGraph>(startDestination = AvailabilityRoute) {
                composable<AvailabilityRoute> { AvailabilityScreen() }
            }
            navigation<SettingsGraph>(startDestination = SettingsRoute) {
                composable<SettingsRoute> {
                    SettingsScreen(
                        onOpenWhatsApp = { nav.navigate(WhatsAppRoute) },
                        onOpenCalendars = { nav.navigate(CalendarConnectionsRoute) },
                        onOpenAddresses = { nav.navigate(AddressesRoute) },
                        onOpenNotifications = { nav.navigate(SettingsNotificationsRoute) { launchSingleTop = true } },
                    )
                }
                composable<SettingsNotificationsRoute> {
                    NotificationsScreen(onBack = { nav.popBackStack() }, onOpen = { link -> nav.openWebPath(link) })
                }
                composable<WhatsAppRoute> { WhatsAppScreen(onBack = { nav.popBackStack() }) }
                composable<AddressesRoute> { com.hyper3dp.glassmeet.ui.settings.AddressesScreen(onBack = { nav.popBackStack() }) }
                composable<CalendarConnectionsRoute> { CalendarConnectionsScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }
}
