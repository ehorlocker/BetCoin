package com.example.betcoin

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.example.betcoin.ui.theme.BetCoinTheme

private const val TAG = "BettingActivity"

class BettingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BetCoinTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column() {
                        BettingDropdowns()
                        BetAmountDropDown()
                    }
                }
            }
        }
    }
}

@Composable
fun BetAmountDropDown() {
    var betAmount by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = "0",
            onValueChange = { betAmount = it },
            modifier = Modifier
                .fillMaxWidth(0.4f)
        )

        Button(
            modifier = Modifier.fillMaxWidth(0.5f)
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .align(Alignment.CenterVertically),
            onClick = { Log.d(TAG, betAmount); }
        ) {
            Text("Bet!")
        }
    }
}

//TODO: Separate out dropdown composables
@Composable
fun BettingDropdowns(modifier: Modifier = Modifier) {
    Row (horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(20.dp)) {

        val accounts = listOf("Eric", "Austin", "Jay")

        var homeExpanded by remember { mutableStateOf(false) }
        var homeSelectedText by remember { mutableStateOf("") }
        val homeIcon = if (homeExpanded)
            Icons.Filled.KeyboardArrowUp
        else
            Icons.Filled.KeyboardArrowDown

        OutlinedTextField(
            value = homeSelectedText,
            onValueChange = { homeSelectedText = it },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(5.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Red,
                unfocusedBorderColor = Color.Red
            ),
            placeholder = {Text("Home")},
            interactionSource = remember { MutableInteractionSource() }
                .also { interactionSource ->
                    LaunchedEffect(interactionSource) {
                        interactionSource.interactions.collect {
                            if (it is PressInteraction.Release) {
                                homeExpanded = !homeExpanded
                            }
                        }
                    }
                },
            trailingIcon = {
                Icon(homeIcon, "contentDescription")
            },
            readOnly = true
        )

        DropdownMenu (
            expanded = homeExpanded,
            onDismissRequest = { homeExpanded = false },
        ) {
            accounts.forEach{ account ->
                DropdownMenuItem(onClick = {
                    homeSelectedText = account
                    homeExpanded = false
                }, text = {
                    Text(text = account)
                })
            }
        }

        Text("vs")

        var awayExpanded by remember { mutableStateOf(false) }
        var awaySelectedText by remember { mutableStateOf("") }
        val awayIcon = if (awayExpanded)
            Icons.Filled.KeyboardArrowUp
        else
            Icons.Filled.KeyboardArrowDown

        OutlinedTextField(
            value = awaySelectedText,
            onValueChange = { awaySelectedText = it },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(5.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Blue,
                unfocusedBorderColor = Color.Blue
            ),
            placeholder = {Text("Away")},
            interactionSource = remember { MutableInteractionSource() }
                .also { interactionSource ->
                    LaunchedEffect(interactionSource) {
                        interactionSource.interactions.collect {
                            if (it is PressInteraction.Release) {
                                awayExpanded = !awayExpanded
                            }
                        }
                    }
                },
            trailingIcon = {
                Icon(awayIcon, "contentDescription")
            },
            readOnly = true
        )

        DropdownMenu (
            expanded = awayExpanded,
            onDismissRequest = { awayExpanded = false },
        ) {
            accounts.forEach{account ->
                DropdownMenuItem(onClick = {
                    awaySelectedText = account
                    awayExpanded = false
                }, text = {
                    Text(text = account)
                })
            }
        }
    }


}

@Preview(showBackground = true)
@Composable
fun BettingPreview() {
    BetCoinTheme {
        Column() {
            BettingDropdowns()
            BetAmountDropDown()
        }
    }
}
