@file:OptIn(ExperimentalMaterial3Api::class)

package com.mixpanel.mixpaneldemo

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mixpanel.android.identity.MixpanelIdentity
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.android.mpmetrics.MixpanelOptions
import org.json.JSONArray
import org.json.JSONObject

// Hierarchical groups is an init-time option and instances are cached per token, so this page
// uses its own project rather than the one DemoApplication initializes.
const val MIXPANEL_HG_PROJECT_TOKEN = "YOUR_HG_PROJECT_TOKEN"

// Filter logcat on "HGDemo|MixpanelAPI.API|MixpanelAPI.Messages" to see each call followed by
// what it did: MixpanelAPI.API logs warnings and dropped calls, and MixpanelAPI.Messages logs
// "Successfully posted to <endpoint>: <body>" for every request.
private const val LOGTAG = "HGDemo"

@Composable
fun HierarchicalGroupsPage(navController: NavHostController) {
    val showDialog = remember { mutableStateOf(false) }
    val dialogMessage = remember { mutableStateOf("") }
    val context = LocalContext.current
    val mixpanel = remember {
        val options = MixpanelOptions.Builder()
            .hierarchicalGroups(true)
            .build()
        // No automatic events, so each flush sends only what the button's call queued
        MixpanelAPI.getInstance(context, MIXPANEL_HG_PROJECT_TOKEN, false, options).apply {
            setEnableLogging(true)
        }
    }

    val userId = remember { mutableStateOf("tyler_android") }
    val companyId = remember { mutableStateOf("violet_tiger") }
    val teamId = remember { mutableStateOf("android_team") }

    // The same group profile operations, for the company and for the custom team_id group
    fun groupActions(label: String, groupKey: String, group: () -> MixpanelAPI.Group?) = listOf(
        Triple("$label: Set Property", "Group: set \"tier\" on $groupKey", {
            println("Setting a $label property")
            group()?.set("tier", "enterprise")
        }),
        Triple("$label: Set Properties (Map)", "Group: setMap on $groupKey", {
            println("Setting multiple $label properties")
            group()?.setMap(mapOf("size" to 12, "region" to "us"))
        }),
        Triple("$label: Set Property Once", "Group: setOnce \"created\" on $groupKey", {
            println("Setting a $label property once")
            group()?.setOnce("created", "2026-10-27")
        }),
        Triple("$label: Union Property", "Group: union \"tags\" on $groupKey", {
            println("Union $label property")
            group()?.union("tags", JSONArray(listOf("android")))
        }),
        Triple("$label: Remove Property", "Group: remove from \"tags\" on $groupKey", {
            println("Removing from $label property")
            group()?.remove("tags", "android")
        }),
        Triple("$label: Unset Property", "Group: unset \"tier\" on $groupKey", {
            println("Unsetting a $label property")
            group()?.unset("tier")
        }),
        Triple("$label: Delete Group", "Group: delete $groupKey", {
            println("Deleting $label group")
            group()?.deleteGroup()
        })
    )

    val hgActions = listOf(
        // Identity
        Triple("Identify", "Identify: user + company", {
            println("Identifying ${userId.value} at ${companyId.value}")
            mixpanel.identify(MixpanelIdentity(userId.value, companyId.value))
        }),
        Triple("Invalid: Identify Without Company", "Identify: user only", {
            println("Identifying ${userId.value} without a company")
            mixpanel.identify(MixpanelIdentity(userId.value))
        }),
        Triple("Invalid: Identify With Distinct ID", "identify(String)", {
            println("Identifying ${userId.value} with identify(String)")
            mixpanel.identify(userId.value)
        }),
        Triple("Invalid: Alias", "alias", {
            println("Aliasing new_alias")
            mixpanel.alias("new_alias", null)
        }),
        Triple("Show Identity", "Identity (see logcat)", {
            Log.i(
                LOGTAG,
                "identity=${mixpanel.identity} optedOut=${mixpanel.hasOptedOutTracking()} " +
                    "superProperties=${mixpanel.superProperties}"
            )
        }),

        // Opt out and in
        Triple("Opt Out", "optOutTracking", {
            println("Opting out")
            mixpanel.optOutTracking()
        }),
        Triple("Opt In", "optInTracking", {
            println("Opting in anonymously")
            mixpanel.optInTracking()
        }),
        Triple("Opt In With Identity", "optInTrackingWithIdentity", {
            println("Opting in as ${userId.value} at ${companyId.value}")
            mixpanel.optInTrackingWithIdentity(MixpanelIdentity(userId.value, companyId.value))
        }),
        Triple("Invalid: Opt In With Distinct ID", "optInTracking(String)", {
            println("Opting in as ${userId.value} with optInTracking(String)")
            mixpanel.optInTracking(userId.value)
        }),

        // Events
        Triple("Track Event", "Event: \"HG Event!\"", {
            println("Tracking HG event")
            mixpanel.track("HG Event!", JSONObject(mapOf("source" to "hierarchical_groups_page")))
        }),
        Triple("Set Team Group", "setGroup: team_id", {
            println("Setting team_id to ${teamId.value}")
            mixpanel.setGroup("team_id", teamId.value)
        }),
        Triple("Invalid: Set Team Groups (List)", "setGroup: team_id list", {
            println("Setting team_id to a list of ${teamId.value}")
            mixpanel.setGroup("team_id", listOf<Any>(teamId.value))
        }),
        Triple("Invalid: Add Team Group", "addGroup: team_id", {
            println("Adding team_id ${teamId.value}")
            mixpanel.addGroup("team_id", teamId.value)
        }),
        Triple("Remove Team Group", "removeGroup: team_id", {
            println("Removing team_id ${teamId.value}")
            mixpanel.removeGroup("team_id", teamId.value)
        }),
        Triple("Track With Team Group", "Event: \"HG Team Event!\"", {
            println("Tracking HG event with team_id ${teamId.value}")
            mixpanel.trackWithGroups("HG Team Event!", null, mapOf("team_id" to teamId.value))
        }),

        // User profile ($user_id group profile)
        Triple("User: Set Property", "People: set \"plan\"", {
            println("Setting a user property")
            mixpanel.people.set("plan", "enterprise")
        }),
        Triple("User: Set Properties (Map)", "People: setMap", {
            println("Setting multiple user properties")
            mixpanel.people.setMap(mapOf("role" to "admin", "seats" to 5))
        }),
        Triple("User: Set Property Once", "People: setOnce \"first_seen\"", {
            println("Setting a user property once")
            mixpanel.people.setOnce("first_seen", "2026-10-27")
        }),
        Triple("User: Union Property", "People: union \"devices\"", {
            println("Union user property")
            mixpanel.people.union("devices", JSONArray(listOf("ios", "android")))
        }),
        Triple("User: Remove Property", "People: remove from \"devices\"", {
            println("Removing from user property")
            mixpanel.people.remove("devices", "android")
        }),
        Triple("User: Unset Property", "People: unset \"plan\"", {
            println("Unsetting a user property")
            mixpanel.people.unset("plan")
        }),
        Triple("Invalid: User Clear Charges", "People: clearCharges", {
            println("Clearing charges")
            mixpanel.people.clearCharges()
        }),
        Triple("User: Delete User", "People: deleteUser", {
            println("Deleting user")
            mixpanel.people.deleteUser()
        }),

        // User profile operations /groups has no equivalent for, dropped with an error log
        Triple("Invalid: User Increment", "People: increment \"logins\"", {
            println("Incrementing a user property")
            mixpanel.people.increment("logins", 1.0)
        }),
        Triple("Invalid: User Increment (Map)", "People: increment map", {
            println("Incrementing multiple user properties")
            mixpanel.people.increment(mapOf("logins" to 1, "sessions" to 2))
        }),
        Triple("Invalid: User Append", "People: append \"history\"", {
            println("Appending to a user property")
            mixpanel.people.append("history", "opened_hg_page")
        }),
        Triple("Invalid: User Merge", "People: merge \"settings\"", {
            println("Merging a user property")
            mixpanel.people.merge("settings", JSONObject(mapOf("theme" to "dark")))
        }),
        Triple("Invalid: User Track Charge", "People: trackCharge 9.99", {
            println("Tracking a charge")
            mixpanel.people.trackCharge(9.99, null)
        })
    ) + groupActions("Company", "\$company_id") { mixpanel.company } +
        groupActions("Team", "team_id") { mixpanel.getGroup("team_id", teamId.value) } +
        listOf(
            Triple("Flush", "Flush queued data", {
                println("Flushing")
                mixpanel.flush()
            }),
            Triple("Reset", "Reset identity", {
                println("Resetting")
                mixpanel.reset()
            })
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Hierarchical Groups") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = userId.value,
                    onValueChange = { userId.value = it },
                    label = { Text("User ID") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = companyId.value,
                    onValueChange = { companyId.value = it },
                    label = { Text("Company ID") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = teamId.value,
                    onValueChange = { teamId.value = it },
                    label = { Text("Team ID") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(hgActions) { (title, message, action) ->
                TrackingButton(title, message, showDialog, dialogMessage, onButtonClick = {
                    Log.i(LOGTAG, "===== $title =====")
                    action()
                    // Flush after every call so its requests show up on their own
                    mixpanel.flush()
                })
            }
        }

        if (showDialog.value) {
            AlertDialog(
                onDismissRequest = { showDialog.value = false },
                title = { Text(text = "Hierarchical Groups") },
                text = { Text(dialogMessage.value) },
                confirmButton = {
                    Button(onClick = { showDialog.value = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}
