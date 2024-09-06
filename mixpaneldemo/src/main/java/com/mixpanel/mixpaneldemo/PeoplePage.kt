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
fun PeoplePage(navController: NavHostController) {
    val showDialog = remember { mutableStateOf(false) }
    val dialogMessage = remember { mutableStateOf("") }
    val context = LocalContext.current
    val mixpanel = MixpanelAPI.getInstance(context, MIXPANEL_PROJECT_TOKEN, true)

    val peopleActions = listOf(
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
        Triple("Unset Properties", "Event: \"Unset Properties!\"", {
            println("Unsetting a property")
            mixpanel.people.unset("property_to_unset")
        }),
        Triple("Increment Properties", "Event: \"Increment Properties!\"", {
            println("Incrementing multiple properties")
            mixpanel.people.increment(mapOf("property1" to 2, "property2" to 3))
        }),
        Triple("Increment Property", "Event: \"Increment Property!\"", {
            println("Incrementing a single property")
            mixpanel.people.increment("property_to_increment", 1.0)
        }),
        Triple("Append Properties", "Event: \"Append Properties!\"", {
            println("Appending properties")
            mixpanel.people.append("property_to_append", "new_value")
        }),
        Triple("Union Properties", "Event: \"Union Properties!\"", {
            println("Union properties")
            val propertiesToUnion = JSONArray(listOf("new_value"))
            mixpanel.people.union("property_to_union", propertiesToUnion)
        }),
        Triple("Track Charge w/o Properties", "Event: \"Track Charge w/o Properties!\"", {
            println("Tracking charge without properties")
            mixpanel.people.trackCharge(29.99, null)
        }),
        Triple("Track Charge w Properties", "Event: \"Track Charge w Properties!\"", {
            println("Tracking charge with properties")
            val chargeProperties = JSONObject(mapOf("item" to "Book", "category" to "Books"))
            mixpanel.people.trackCharge(29.99, chargeProperties)
        }),
        Triple("Clear Charges", "Event: \"Clear Charges!\"", {
            println("Clearing charges")
            mixpanel.people.clearCharges()
        }),
        Triple("Delete User", "Event: \"Delete User!\"", {
            println("Deleting user")
            mixpanel.people.deleteUser()
        }),
        Triple("Identify", "Event: \"Identify!\"", {
            println("Identifying user")
            mixpanel.identify("testUser")
        })
    )


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "People Calls") },
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
            items(peopleActions) { (title, message, action) ->
                TrackingButton(title, message, showDialog, dialogMessage, onButtonClick = action)
            }
        }

        if (showDialog.value) {
            AlertDialog(
                onDismissRequest = { showDialog.value = false },
                title = { Text(text = "People Event") },
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
