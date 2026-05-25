package com.betcoin.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
    const val LEADERBOARD = "leaderboard"
    const val BET_HISTORY = "bet_history"
    const val MANAGE_PLAYERS = "manage_players"
    const val PLAYER_DETAIL = "player_detail/{userId}"
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
                    navController.navigate(Routes.NEW_BET)
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
        composable(Routes.NEW_BET) {
            NewBetScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }
        composable(Routes.LEADERBOARD) {
            LeaderboardScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPlayerClick = { userId ->
                    navController.navigate(Routes.PLAYER_DETAIL.replace("{userId}", "$userId"))
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
            )
        }
        composable(
            route = Routes.PLAYER_DETAIL,
            arguments = listOf(
                navArgument("userId") {
                    type = androidx.navigation.NavType.LongType
                }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            PlayerDetailScreen(
                userId = userId,
                viewModel = hiltViewModel(),
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }
    }
}
