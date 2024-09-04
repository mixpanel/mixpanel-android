package com.mixpanel.mixpaneldemo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun NavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = "landingPage",
        modifier = modifier
    ) {
        composable("landingPage") { LandingPage(navController) }
        composable("trackingPage") { TrackingPage(navController) }
        composable("peoplePage") { PeoplePage(navController) }
        composable("utilityPage") { UtilityPage(navController) }
        composable("gdprPage") { GDPRPage(navController) }
        composable("groupsPage") { GroupsPage(navController) }
    }
}
