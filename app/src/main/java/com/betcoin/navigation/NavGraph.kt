package com.betcoin.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.betcoin.ui.betdetail.BetDetailScreen
import com.betcoin.ui.bethistory.BetHistoryScreen
import com.betcoin.ui.home.HomeScreen
import com.betcoin.ui.leaderboard.LeaderboardScreen
import com.betcoin.ui.newbet.NewBetScreen
import com.betcoin.ui.onboarding.OnboardingScreen
import com.betcoin.ui.players.ManagePlayersScreen
import com.betcoin.ui.players.PlayerDetailScreen

/**
 * Navigation routes used in the BetCoin app.
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
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
 *   [Routes.HOME] otherwise
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
            )
        }
        composable(Routes.LEADERBOARD) {
            LeaderboardScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }
        composable(Routes.BET_HISTORY) {
            BetHistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }
        composable(Routes.MANAGE_PLAYERS) {
            ManagePlayersScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPlayerClick = { playerId ->
                    navController.navigate(Routes.playerDetail(playerId))
                },
                // onAdminPanel is wired to the Admin Panel in BET-29.
            )
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
    }
}
