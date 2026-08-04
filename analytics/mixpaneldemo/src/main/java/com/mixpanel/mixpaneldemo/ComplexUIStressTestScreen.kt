@file:OptIn(ExperimentalMaterial3Api::class)

package com.mixpanel.mixpaneldemo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

private val productNames = listOf(
    "Wireless Bluetooth Headphones", "Organic Cotton T-Shirt", "Stainless Steel Water Bottle",
    "Leather Crossbody Bag", "Smart Fitness Tracker", "Ceramic Coffee Mug Set",
    "Bamboo Cutting Board", "Portable Phone Charger", "Wool Blend Scarf",
    "Cast Iron Skillet", "Yoga Mat Premium", "LED Desk Lamp",
    "Canvas Backpack", "Insulated Lunch Bag", "Silk Pillowcase Set",
    "Electric Toothbrush", "Plant-Based Protein Powder", "Stainless Steel Tumbler",
    "Memory Foam Pillow", "Resistance Band Set", "Glass Food Containers",
    "Wireless Mouse", "Essential Oil Diffuser", "Bamboo Toothbrush Pack",
    "Running Shoes Pro", "Cotton Bed Sheets", "Travel Neck Pillow",
    "Kitchen Scale Digital", "Reusable Shopping Bags", "Noise Cancelling Earbuds",
    "Vitamin C Serum", "Adjustable Dumbbells", "Stainless Lunch Box",
    "Organic Green Tea", "Bluetooth Speaker Mini"
)

private val categories = listOf(
    "All", "Electronics", "Clothing", "Kitchen", "Fitness",
    "Home", "Beauty", "Food", "Travel", "Accessories"
)

private val badges = listOf("SALE", "NEW", "HOT", null, null, "SALE", null, "NEW", null, null)

@Composable
fun ComplexUIStressTestScreen(navController: NavHostController) {
    var searchText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableIntStateOf(0) }
    var viewCount by remember { mutableIntStateOf(0) }

    // Count views after composition
    LaunchedEffect(Unit) {
        // Rough estimate based on structure
        // Header: ~10, Search: ~5, Chips: ~30, Cards: 35 * 18 = 630, FAB: ~3
        viewCount = 10 + 5 + (categories.size * 3) + (productNames.size * 18) + 3
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shop (Views: ~$viewCount)") },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.semantics { contentDescription = "stress_back_button" }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A237E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { },
                modifier = Modifier.semantics { contentDescription = "stress_fab_cart" },
                containerColor = Color(0xFFFF6F00)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add to cart")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // View count banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE8EAF6))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Estimated View Count: ~$viewCount",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A237E)
                )
            }

            // Search bar
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics { contentDescription = "stress_search_field" },
                placeholder = { Text("Search products...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search icon") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            // Category chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEachIndexed { index, category ->
                    FilterChip(
                        selected = selectedCategory == index,
                        onClick = { selectedCategory = index },
                        label = { Text(category) },
                        modifier = Modifier.semantics {
                            contentDescription = "stress_chip_$category"
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Section header
            Text(
                text = "Featured Products",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            // Product grid - 2 columns using nested Rows
            val chunkedProducts = productNames.chunkedPairs()
            chunkedProducts.forEachIndexed { rowIndex, pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProductCard(
                        index = rowIndex * 2,
                        name = pair.first,
                        modifier = Modifier.weight(1f)
                    )
                    if (pair.second != null) {
                        ProductCard(
                            index = rowIndex * 2 + 1,
                            name = pair.second!!,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
        }
    }
}

@Composable
private fun ProductCard(index: Int, name: String, modifier: Modifier = Modifier) {
    val badge = badges[index % badges.size]
    val originalPrice = 19.99 + (index * 7.53)
    val salePrice = originalPrice * 0.7
    val rating = 3 + (index % 3)
    val reviewCount = 10 + (index * 13) % 500
    var isFavorite by remember { mutableStateOf(index % 4 == 0) }

    Card(
        modifier = modifier
            .semantics { contentDescription = "Product $index" }
            .clickable { },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column {
            // Image placeholder with badge overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Color(
                            red = (100 + index * 17 % 155) / 255f,
                            green = (100 + index * 31 % 155) / 255f,
                            blue = (100 + index * 47 % 155) / 255f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Simulated image placeholder
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                // Badge
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(
                                if (badge == "SALE") Color(0xFFE53935) else Color(0xFF43A047),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Details section
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                // Product name
                Text(
                    text = name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics {
                        contentDescription = "stress_product_name_$index"
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Rating row
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 5 star icons
                    for (star in 1..5) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Star $star for product $index",
                            modifier = Modifier.size(14.dp),
                            tint = if (star <= rating) Color(0xFFFFC107) else Color(0xFFE0E0E0)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "($reviewCount)",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Price row
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (badge == "SALE") {
                        Text(
                            text = "$${String.format("%.2f", originalPrice)}",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textDecoration = TextDecoration.LineThrough
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$${String.format("%.2f", salePrice)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE53935)
                        )
                    } else {
                        Text(
                            text = "$${String.format("%.2f", originalPrice)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A237E)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Action row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { },
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .semantics { contentDescription = "stress_add_to_cart_$index" },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A237E)
                        )
                    ) {
                        Text("Add", fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = { isFavorite = !isFavorite },
                        modifier = Modifier
                            .size(32.dp)
                            .semantics { contentDescription = "stress_favorite_$index" }
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove favorite $index" else "Add favorite $index",
                            tint = if (isFavorite) Color(0xFFE53935) else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun List<String>.chunkedPairs(): List<Pair<String, String?>> {
    val result = mutableListOf<Pair<String, String?>>()
    for (i in indices step 2) {
        result.add(Pair(this[i], this.getOrNull(i + 1)))
    }
    return result
}
