@file:OptIn(ExperimentalMaterial3Api::class)

package com.mixpanel.mixpaneldemo

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
import com.mixpanel.android.mpmetrics.MixpanelAPI
import org.json.JSONArray
import org.json.JSONObject


@Composable
fun GroupsPage(navController: NavHostController) {
    val showDialog = remember { mutableStateOf(false) }
    val dialogMessage = remember { mutableStateOf("") }
    val context = LocalContext.current
    val mixpanel = MixpanelAPI.getInstance(context, MIXPANEL_PROJECT_TOKEN, true)

    val groupsActions = listOf(
        Triple("Set Properties", "Event: \"Set Properties!\"", {
            println("Setting multiple properties")
            val properties = JSONObject(mapOf("property1" to "value1", "property2" to "value2"))
            mixpanel.people.set(properties)
        }),
        Triple("Set One Property", "Event: \"Set One Property!\"", {
            println("Setting a single property")
            mixpanel.people.set("property", "value")
        }),
        Triple("Set Properties Once", "Event: \"Set Properties Once!\"", {
            println("Setting properties once")
            val propertiesOnce = JSONObject(mapOf("property_once" to "value_once"))
            mixpanel.people.setOnce(propertiesOnce)
        }),
        Triple("Unset Property", "Event: \"Unset Property!\"", {
            println("Unsetting a property")
            mixpanel.people.unset("property_to_unset")
        }),
        Triple("Remove Property", "Event: \"Remove Property!\"", {
            println("Removing a property")
            mixpanel.people.remove("property_to_remove", 1)
        }),
        Triple("Union Properties", "Event: \"Union Properties!\"", {
            println("Union properties")
            val propertiesToUnion = JSONArray(listOf("new_value"))
            mixpanel.people.union("property_to_union", propertiesToUnion)
        }),
        Triple("Delete Group", "Event: \"Delete Group!\"", {
            println("Deleting a group")
            mixpanel.getPeople().deleteUser()
        }),
        Triple("Set Group", "Event: \"Set Group!\"", {
            println("Setting a group")
            mixpanel.people.set("group_key", "group_value")
        }),
        Triple("Set One Group", "Event: \"Set One Group!\"", {
            println("Setting one group")
            mixpanel.people.set("single_group_key", "single_group_value")
        })
    )


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Group Calls") },
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
            items(groupsActions) { (title, message, action) ->
                TrackingButton(title, message, showDialog, dialogMessage, onButtonClick = action)
            }
        }

        if (showDialog.value) {
            AlertDialog(
                onDismissRequest = { showDialog.value = false },
                title = { Text(text = "Group Event") },
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