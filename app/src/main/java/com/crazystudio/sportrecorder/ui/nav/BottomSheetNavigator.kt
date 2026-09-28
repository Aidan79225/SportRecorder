package com.crazystudio.sportrecorder.ui.nav

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.FloatingWindow
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestinationBuilder
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.Navigator
import androidx.navigation.compose.LocalOwnersProvider
import androidx.navigation.get
import kotlin.reflect.KClass
import kotlin.reflect.KType

private const val NAVIGATOR_NAME = "bottomSheet"

/**
 * Navigator for routes shown as a Material 3 [ModalBottomSheet]. AndroidX ships a sheet navigator
 * only for Material 2, so this is a port of Navigation Compose's own `DialogNavigator`: a
 * [FloatingWindow] destination whose entries stay on the back stack (own ViewModelStore, own
 * SavedStateHandle) while [BottomSheetHost] renders the visible ones.
 */
// No-arg constructor: Navigator(name) is @RestrictTo the androidx.navigation group (lint RestrictedApi);
// the public constructor reads the name from @Navigator.Name, as material-navigation's own navigator does.
@Navigator.Name(NAVIGATOR_NAME)
class BottomSheetNavigator : Navigator<BottomSheetNavigator.Destination>() {

    internal val backStack get() = state.backStack
    internal val transitionsInProgress get() = state.transitionsInProgress

    /**
     * A user dismissal (swipe, scrim, back) — the sheet has already animated out. For an entry
     * already popped from code (back or a drag interrupted its slide-out, and M3 then reports the
     * dismissal) there is nothing left to pop, so just release it.
     */
    internal fun dismiss(entry: NavBackStackEntry) {
        if (state.backStack.value.contains(entry)) popBackStack(entry, false) else onTransitionComplete(entry)
    }

    override fun navigate(entries: List<NavBackStackEntry>, navOptions: NavOptions?, navigatorExtras: Extras?) {
        entries.forEach { entry -> state.push(entry) }
    }

    override fun createDestination(): Destination = Destination(this) {}

    override fun popBackStack(popUpTo: NavBackStackEntry, savedState: Boolean) {
        state.popWithTransition(popUpTo, savedState)
        // popWithTransition also marks the incoming sheet below the popped one as transitioning,
        // holding it in STARTED; release it so it can move to RESUMED. The popped entry itself is
        // released by the host once its slide-out ends.
        val popIndex = state.transitionsInProgress.value.indexOf(popUpTo)
        state.transitionsInProgress.value.forEachIndexed { index, entry ->
            if (index > popIndex) onTransitionComplete(entry)
        }
    }

    internal fun onTransitionComplete(entry: NavBackStackEntry) {
        if (state.transitionsInProgress.value.contains(entry)) state.markTransitionComplete(entry)
    }

    @NavDestination.ClassType(Composable::class)
    class Destination(
        navigator: BottomSheetNavigator,
        internal val content: @Composable (NavBackStackEntry) -> Unit,
    ) : NavDestination(navigator), FloatingWindow
}

/** Builder behind [bottomSheet], mirroring `NavGraphBuilder.dialog<T>`. */
@PublishedApi
internal class BottomSheetNavigatorDestinationBuilder(
    private val sheetNavigator: BottomSheetNavigator,
    route: KClass<*>,
    typeMap: Map<KType, NavType<*>>,
    private val content: @Composable (NavBackStackEntry) -> Unit,
) : NavDestinationBuilder<BottomSheetNavigator.Destination>(sheetNavigator, route, typeMap) {
    override fun instantiateDestination(): BottomSheetNavigator.Destination =
        BottomSheetNavigator.Destination(sheetNavigator, content)
}

/** Add a typed route rendered as a Material 3 modal bottom sheet. */
inline fun <reified T : Any> NavGraphBuilder.bottomSheet(
    typeMap: Map<KType, NavType<*>> = emptyMap(),
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    destination(
        BottomSheetNavigatorDestinationBuilder(provider[BottomSheetNavigator::class], T::class, typeMap, content),
    )
}

/**
 * Renders every visible sheet entry as a [ModalBottomSheet] that skips the half-height stop, so a
 * tall sheet (the meal editor) opens with its confirm button on screen. A user dismissal pops the
 * entry; a programmatic pop slides the sheet out before the entry is released.
 *
 * Placement: compose it after the `NavHost` that attaches [navigator] (reading the navigator's
 * state before it is attached throws "You cannot access the Navigator's state until the Navigator
 * is attached"), and keep it composed for as long as the NavController can hold sheet entries. In
 * `AppRoot` that means inside the same `Scaffold` content lambda, after `NavHost`: a sibling placed
 * after `Scaffold(...)` runs first, because Scaffold subcomposes its content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetHost(navigator: BottomSheetNavigator) {
    val saveableStateHolder = rememberSaveableStateHolder()
    val backStack by navigator.backStack.collectAsState()
    val visibleEntries = rememberVisibleEntries(backStack)
    visibleEntries.PopulateVisibleEntries(backStack)
    val transitionsInProgress by navigator.transitionsInProgress.collectAsState()
    val composedEntries = remember { mutableStateListOf<NavBackStackEntry>() }
    // Unlike a dialog, a sheet animates out. A pop moves its entry to CREATED at once, which drops
    // it from visibleEntries (ON_STOP) before the sheet has moved; keep rendering a composed,
    // popped entry until its slide-out below marks the transition complete.
    val exitingEntries = transitionsInProgress.filter {
        it !in backStack && it in composedEntries && it !in visibleEntries
    }

    (visibleEntries + exitingEntries).forEach { entry ->
        key(entry.id) {
            val destination = entry.destination as BottomSheetNavigator.Destination
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val inBackStack = backStack.contains(entry)
            LaunchedEffect(inBackStack) {
                if (!inBackStack) {
                    // Popped from code (e.g. after saving): animate out, then let the entry go.
                    // Back or a drag during the slide interrupts hide() (MutatorMutex); release anyway.
                    try {
                        sheetState.hide()
                    } finally {
                        navigator.onTransitionComplete(entry)
                    }
                }
            }
            ModalBottomSheet(
                onDismissRequest = { navigator.dismiss(entry) },
                sheetState = sheetState,
            ) {
                DisposableEffect(entry) {
                    composedEntries.add(entry)
                    onDispose {
                        navigator.onTransitionComplete(entry)
                        composedEntries.remove(entry)
                    }
                }
                entry.LocalOwnersProvider(saveableStateHolder) { destination.content(entry) }
            }
        }
    }
    // Entries popped before they were ever composed would otherwise stay "in transition" forever.
    LaunchedEffect(transitionsInProgress, composedEntries) {
        transitionsInProgress.forEach { entry ->
            if (!navigator.backStack.value.contains(entry) && !composedEntries.contains(entry)) {
                navigator.onTransitionComplete(entry)
            }
        }
    }
}

@Composable
private fun rememberVisibleEntries(backStack: Collection<NavBackStackEntry>): SnapshotStateList<NavBackStackEntry> =
    remember(backStack) {
        mutableStateListOf<NavBackStackEntry>().also { list ->
            list.addAll(backStack.filter { it.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) })
        }
    }

@Composable
private fun MutableList<NavBackStackEntry>.PopulateVisibleEntries(backStack: Collection<NavBackStackEntry>) {
    backStack.forEach { entry ->
        DisposableEffect(entry.lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START && !contains(entry)) add(entry)
                if (event == Lifecycle.Event.ON_STOP) remove(entry)
            }
            entry.lifecycle.addObserver(observer)
            onDispose { entry.lifecycle.removeObserver(observer) }
        }
    }
}
