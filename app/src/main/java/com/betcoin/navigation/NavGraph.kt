package com.betcoin.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.betcoin.ui.admin.AdminGateViewModel
import com.betcoin.ui.admin.AdminPanelScreen
import com.betcoin.ui.admin.AdminPinGate
import com.betcoin.ui.admin.AdminPinRecoveryScreen
import com.betcoin.ui.betdetail.BetDetailScreen
import com.betcoin.ui.bethistory.BetHistoryScreen
import com.betcoin.ui.home.HomeScreen
import com.betcoin.ui.leaderboard.LeaderboardScreen
import com.betcoin.ui.newbet.NewBetScreen
import com.betcoin.ui.onboarding.OnboardingScreen
import com.betcoin.ui.players.ManagePlayersScreen
import com.betcoin.ui.players.PlayerDetailScreen
import com.betcoin.ui.recovery.RecoveryScreen

/**
 * Navigation routes used in the BetCoin app.
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"

    /** Shown at launch when an interrupted New Bet flow left a pending bet behind. */
    const val RECOVERY = "recovery"
    const val NEW_BET = "new_bet"

    /** Optional New Bet argument: an existing pending bet to open at Step B (Recovery, Repeat). */
    const val NEW_BET_BET_ID_ARG = "betId"
    const val NEW_BET_ROUTE = "$NEW_BET?$NEW_BET_BET_ID_ARG={$NEW_BET_BET_ID_ARG}"

    /** New Bet at Step A, or at Step B for the pending bet [betId]. */
    fun newBet(betId: Long? = null): String =
        if (betId == null) NEW_BET else "$NEW_BET?$NEW_BET_BET_ID_ARG=$betId"
    const val LEADERBOARD = "leaderboard"
    const val BET_HISTORY = "bet_history"
    const val MANAGE_PLAYERS = "manage_players"
    const val ADMIN_PANEL = "admin_panel"
    const val ADMIN_PIN_RECOVERY = "admin_pin_recovery"

    /** Navigation argument holding the player's user ID. */
    const val PLAYER_ID_ARG = "playerId"
    const val PLAYER_DETAIL = "player_detail/{$PLAYER_ID_ARG}"

    /** Route to the Player Detail screen of [playerId]. */
    fun playerDetail(playerId: Long): String = "player_detail/$playerId"

    /** Navigation argument holding the bet's ID on [BET_DETAIL]. */
    const val BET_ID_ARG = "betId"
    const val BET_DETAIL = "bet_detail/{$BET_ID_ARG}"

    /** The [BET_DETAIL] route for [betId]. */
    fun betDetail(betId: Long): String = "bet_detail/$betId"
}

/**
 * The navigation graph for BetCoin.
 *
 * Defines all screens and their navigation logic. The start destination
 * is determined by the caller (see [MainActivity]).
 *
 * @param navController the [NavHostController] that manages navigation
 * @param startDestination the initial route — [Routes.ONBOARDING] for first launch,
 *   [Routes.RECOVERY] when an interrupted bet is pending, [Routes.HOME] otherwise
 *   (see [StartDestinationResolver])
 * @param modifier optional [Modifier] applied to the NavHost
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                viewModel = hiltViewModel(),
                onOnboardingComplete = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = hiltViewModel(),
                onNewBet = {
                    navController.navigate(Routes.newBet())
                },
                onLeaderboard = {
                    navController.navigate(Routes.LEADERBOARD)
                },
                onBetHistory = {
                    navController.navigate(Routes.BET_HISTORY)
                },
                onManagePlayers = {
                    navController.navigate(Routes.MANAGE_PLAYERS)
                },
            )
        }
        composable(Routes.RECOVERY) { backStackEntry ->
            RecoveryScreen(
                viewModel = hiltViewModel(),
                onReturnToBet = { betId ->
                    // Ignore repeat taps while Recovery is exiting (would duplicate the back stack).
                    if (backStackEntry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.RECOVERY) { inclusive = true }
                        }
                        navController.navigate(Routes.newBet(betId))
                    }
                },
                onGoHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.RECOVERY) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.NEW_BET_ROUTE,
            arguments = listOf(
                navArgument(Routes.NEW_BET_BET_ID_ARG) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) {
            NewBetScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLocked = { betId ->
                    navController.navigate(Routes.betDetail(betId)) {
                        popUpTo(Routes.NEW_BET_ROUTE) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.LEADERBOARD) {
            LeaderboardScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = { navController.popBackStack() },
                onPlayerClick = { playerId -> navController.navigate(Routes.playerDetail(playerId)) },
            )
        }
        composable(Routes.BET_HISTORY) {
            BetHistoryScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = { navController.popBackStack() },
                onBetClick = { betId -> navController.navigate(Routes.betDetail(betId)) },
            )
        }
        composable(Routes.MANAGE_PLAYERS) {
            var showAdminGate by remember { mutableStateOf(false) }
            ManagePlayersScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPlayerClick = { playerId ->
                    navController.navigate(Routes.playerDetail(playerId))
                },
                onAdminPanel = { showAdminGate = true },
            )
            if (showAdminGate) {
                val gateViewModel: AdminGateViewModel = hiltViewModel()
                AdminPinGate(
                    verifier = gateViewModel::verifyPin,
                    onVerified = {
                        showAdminGate = false
                        navController.navigate(Routes.ADMIN_PANEL)
                    },
                    onForgotPin = {
                        showAdminGate = false
                        navController.navigate(Routes.ADMIN_PIN_RECOVERY)
                    },
                    onDismiss = { showAdminGate = false },
                )
            }
        }
        composable(
            route = Routes.PLAYER_DETAIL,
            arguments = listOf(navArgument(Routes.PLAYER_ID_ARG) { type = NavType.LongType }),
        ) {
            PlayerDetailScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }
        composable(
            route = Routes.BET_DETAIL,
            arguments = listOf(navArgument(Routes.BET_ID_ARG) { type = NavType.LongType }),
        ) {
            BetDetailScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }
        composable(Routes.ADMIN_PANEL) {
            AdminPanelScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }
        composable(Routes.ADMIN_PIN_RECOVERY) {
            AdminPinRecoveryScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = {
                    navController.popBackStack()
                },
                onRecovered = {
                    navController.navigate(Routes.ADMIN_PANEL) {
                        popUpTo(Routes.ADMIN_PIN_RECOVERY) { inclusive = true }
                    }
                },
            )
        }
    }
}
