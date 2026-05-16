package com.betcoin.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.betcoin.ui.bethistory.BetHistoryScreen
import com.betcoin.ui.home.HomeScreen
import com.betcoin.ui.leaderboard.LeaderboardScreen
import com.betcoin.ui.newbet.NewBetScreen
import com.betcoin.ui.onboarding.OnboardingScreen
import com.betcoin.ui.players.ManagePlayersScreen

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
            NewBetScreen()
        }
        composable(Routes.LEADERBOARD) {
            LeaderboardScreen()
        }
        composable(Routes.BET_HISTORY) {
            BetHistoryScreen()
        }
        composable(Routes.MANAGE_PLAYERS) {
            ManagePlayersScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }
    }
}
