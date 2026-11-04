package com.mixpanel.mixpaneldemo

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class ComplexUIXmlStressTestActivity : AppCompatActivity() {

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

    private lateinit var viewCountText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Complex UI Stress Test (XML)"

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val rootScroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // View count banner
        viewCountText = TextView(this).apply {
            text = "View Count: calculating..."
            setTextColor(Color.parseColor("#1A237E"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E8EAF6"))
            setPadding(dp(8), dp(12), dp(8), dp(12))
            contentDescription = "stress_xml_view_count"
        }
        rootLayout.addView(viewCountText, matchWrap())

        // Search bar section
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            gravity = Gravity.CENTER_VERTICAL
        }

        val searchIcon = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "stress_xml_search_icon"
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        searchRow.addView(searchIcon, LinearLayout.LayoutParams(dp(48), dp(48)))

        val searchField = EditText(this).apply {
            hint = "Search products..."
            setSingleLine(true)
            contentDescription = "stress_xml_search_field"
            setPadding(dp(12), dp(8), dp(12), dp(8))
            val bg = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.parseColor("#BDBDBD"))
            }
            background = bg
        }
        searchRow.addView(searchField, LinearLayout.LayoutParams(0, dp(48), 1f))

        rootLayout.addView(searchRow, matchWrap())

        // Category chips in HorizontalScrollView
        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        categories.forEachIndexed { index, category ->
            val chip = Button(this).apply {
                text = category
                textSize = 12f
                isAllCaps = false
                contentDescription = "stress_xml_chip_$category"
                setPadding(dp(16), dp(6), dp(16), dp(6))
                val bg = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    if (index == 0) {
                        setColor(Color.parseColor("#1A237E"))
                        setStroke(dp(1), Color.parseColor("#1A237E"))
                    } else {
                        setColor(Color.WHITE)
                        setStroke(dp(1), Color.parseColor("#BDBDBD"))
                    }
                }
                background = bg
                setTextColor(if (index == 0) Color.WHITE else Color.parseColor("#333333"))
                setOnClickListener { }
            }
            val chipParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(36)
            ).apply {
                marginEnd = dp(8)
            }
            chipRow.addView(chip, chipParams)
        }

        chipScroll.addView(chipRow)
        rootLayout.addView(chipScroll, matchWrap())

        // Section header
        val sectionHeader = TextView(this).apply {
            text = "Featured Products"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#212121"))
            setPadding(dp(12), dp(12), dp(12), dp(4))
        }
        rootLayout.addView(sectionHeader, matchWrap())

        // Product grid - 2 column layout
        for (rowIndex in 0 until (productNames.size + 1) / 2) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(8), dp(4), dp(8), dp(4))
            }

            val leftIndex = rowIndex * 2
            val rightIndex = rowIndex * 2 + 1

            rowLayout.addView(
                createProductCard(leftIndex, productNames[leftIndex], density),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(4)
                }
            )

            if (rightIndex < productNames.size) {
                rowLayout.addView(
                    createProductCard(rightIndex, productNames[rightIndex], density),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dp(4)
                    }
                )
            } else {
                // Empty spacer for odd number
                val spacer = View(this)
                rowLayout.addView(
                    spacer,
                    LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = dp(4) }
                )
            }

            rootLayout.addView(rowLayout, matchWrap())
        }

        // Bottom spacer
        val bottomSpacer = View(this)
        rootLayout.addView(bottomSpacer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(80)
        ))

        rootScroll.addView(rootLayout)
        setContentView(rootScroll)

        // Count views after layout
        rootScroll.post {
            val count = countViews(rootScroll)
            viewCountText.text = "View Count: $count"
        }
    }

    private fun createProductCard(index: Int, name: String, density: Float): MaterialCardView {
        fun dp(value: Int) = (value * density).toInt()

        val badge = badges[index % badges.size]
        val originalPrice = 19.99 + (index * 7.53)
        val salePrice = originalPrice * 0.7
        val rating = 3 + (index % 3)
        val reviewCount = 10 + (index * 13) % 500

        val card = MaterialCardView(this).apply {
            radius = dp(12).toFloat()
            cardElevation = dp(4).toFloat()
            setCardBackgroundColor(Color.WHITE)
            contentDescription = "Product $index"
        }

        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Image placeholder with badge
        val imageContainer = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(120)
            )
        }

        val imagePlaceholder = ImageView(this).apply {
            setBackgroundColor(Color.rgb(
                100 + (index * 17) % 155,
                100 + (index * 31) % 155,
                100 + (index * 47) % 155
            ))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = "stress_xml_product_image_$index"
        }
        imageContainer.addView(imagePlaceholder, android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Number overlay on image
        val numberOverlay = TextView(this).apply {
            text = "${index + 1}"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(77, 255, 255, 255))
            }
            background = bg
        }
        val overlayParams = android.widget.FrameLayout.LayoutParams(dp(40), dp(40)).apply {
            gravity = Gravity.CENTER
        }
        imageContainer.addView(numberOverlay, overlayParams)

        // Badge
        if (badge != null) {
            val badgeView = TextView(this).apply {
                text = badge
                setTextColor(Color.WHITE)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(6), dp(2), dp(6), dp(2))
                val bg = GradientDrawable().apply {
                    cornerRadius = dp(4).toFloat()
                    setColor(
                        if (badge == "SALE") Color.parseColor("#E53935")
                        else Color.parseColor("#43A047")
                    )
                }
                background = bg
            }
            val badgeParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins(dp(6), dp(6), 0, 0)
            }
            imageContainer.addView(badgeView, badgeParams)
        }

        cardContent.addView(imageContainer)

        // Details section
        val detailsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        // Product name
        val nameText = TextView(this).apply {
            text = name
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(Color.parseColor("#212121"))
            contentDescription = "stress_xml_product_name_$index"
        }
        detailsLayout.addView(nameText, matchWrap())

        // Rating row
        val ratingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }

        // 5 star images
        for (star in 1..5) {
            val starView = ImageView(this).apply {
                setImageResource(android.R.drawable.btn_star_big_on)
                if (star > rating) {
                    alpha = 0.3f
                }
                contentDescription = "Star $star for product $index"
            }
            ratingRow.addView(starView, LinearLayout.LayoutParams(dp(14), dp(14)).apply {
                marginEnd = dp(1)
            })
        }

        val reviewText = TextView(this).apply {
            text = "($reviewCount)"
            textSize = 11f
            setTextColor(Color.GRAY)
            setPadding(dp(4), 0, 0, 0)
        }
        ratingRow.addView(reviewText, wrapWrap())

        detailsLayout.addView(ratingRow, matchWrap())

        // Price row
        val priceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }

        if (badge == "SALE") {
            val originalPriceText = TextView(this).apply {
                text = "$${String.format("%.2f", originalPrice)}"
                textSize = 11f
                setTextColor(Color.GRAY)
                paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            }
            priceRow.addView(originalPriceText, wrapWrap())

            val salePriceText = TextView(this).apply {
                text = "$${String.format("%.2f", salePrice)}"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#E53935"))
                setPadding(dp(4), 0, 0, 0)
            }
            priceRow.addView(salePriceText, wrapWrap())
        } else {
            val priceText = TextView(this).apply {
                text = "$${String.format("%.2f", originalPrice)}"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#1A237E"))
            }
            priceRow.addView(priceText, wrapWrap())
        }

        detailsLayout.addView(priceRow, matchWrap())

        // Action row
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }

        val addButton = Button(this).apply {
            text = "Add"
            textSize = 11f
            isAllCaps = false
            contentDescription = "stress_xml_add_to_cart_$index"
            setPadding(dp(8), dp(4), dp(8), dp(4))
            val bg = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#1A237E"))
            }
            background = bg
            setTextColor(Color.WHITE)
            setOnClickListener { }
            minimumHeight = 0
            minHeight = 0
        }
        actionRow.addView(addButton, LinearLayout.LayoutParams(0, dp(32), 1f))

        val favButton = ImageButton(this).apply {
            var isFavorite = index % 4 == 0
            setImageResource(
                if (isFavorite) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "stress_xml_favorite_$index"
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setOnClickListener {
                isFavorite = !isFavorite
                setImageResource(
                    if (isFavorite) android.R.drawable.btn_star_big_on
                    else android.R.drawable.btn_star_big_off
                )
            }
        }
        actionRow.addView(favButton, LinearLayout.LayoutParams(dp(32), dp(32)).apply {
            marginStart = dp(4)
        })

        detailsLayout.addView(actionRow, matchWrap())

        cardContent.addView(detailsLayout, matchWrap())
        card.addView(cardContent, matchWrap())

        return card
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun wrapWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun countViews(view: View): Int {
        var count = 1
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                count += countViews(view.getChildAt(i))
            }
        }
        return count
    }
}
