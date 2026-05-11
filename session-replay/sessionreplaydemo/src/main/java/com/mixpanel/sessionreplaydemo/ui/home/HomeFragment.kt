package com.mixpanel.sessionreplaydemo.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.sessionreplaydemo.databinding.FragmentHomeBinding
import com.mixpanel.sessionreplaydemo.ui.imageheuristic.ImageHeuristicActivity
import com.mixpanel.sessionreplaydemo.ui.jokes.JokesActivity
import com.mixpanel.sessionreplaydemo.ui.maskingstandard.MaskingStandardComposeActivity
import com.mixpanel.sessionreplaydemo.ui.maskingstandard.MaskingStandardXmlActivity
import com.mixpanel.sessionreplaydemo.Constants
import com.mixpanel.sessionreplaydemo.ui.safecontainer.SafeContainerTestActivity

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this)[HomeViewModel::class.java]

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome
        //  SensitiveViewManager.addSensitiveView(textView)
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }

        MixpanelAPI
            .getInstance(this.context, Constants.MIXPANEL_TOKEN, true)
            .track("Tapped Home")

        // Set up jokes button click listener
        binding.jokesButton.setOnClickListener {
            val intent = Intent(requireContext(), JokesActivity::class.java)
            startActivity(intent)
        }

        // Set up safe container test button click listener
        binding.safeContainerButton.setOnClickListener {
            val intent = Intent(requireContext(), SafeContainerTestActivity::class.java)
            startActivity(intent)
        }

        // Set up image heuristic test button click listener
        binding.imageHeuristicButton.setOnClickListener {
            val intent = Intent(requireContext(), ImageHeuristicActivity::class.java)
            startActivity(intent)
        }

        // Set up masking standard XML test button click listener
        binding.maskingStandardXmlButton.setOnClickListener {
            val intent = Intent(requireContext(), MaskingStandardXmlActivity::class.java)
            startActivity(intent)
        }

        // Set up masking standard Compose test button click listener
        binding.maskingStandardComposeButton.setOnClickListener {
            val intent = Intent(requireContext(), MaskingStandardComposeActivity::class.java)
            startActivity(intent)
        }

        // Set up dialog test button click listener
        binding.dialogTestButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Test Dialog")
                .setMessage("This dialog tests that the debug mask overlay appears on top of dialog windows.")
                .setPositiveButton("OK", null)
                .show()
        }

        // Show a BottomSheetDialog (anchored to bottom)
        binding.bottomSheetButton.setOnClickListener {
            val bottomSheet = BottomSheetDialog(requireContext())
            val layout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 48, 48, 48)
                // Green bar - not masked
                addView(View(requireContext()).apply {
                    setBackgroundColor(0xFF4CAF50.toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 100
                    )
                })
                // Text - will be auto-masked
                addView(TextView(requireContext()).apply {
                    text = "This text should be masked"
                    textSize = 18f
                    setPadding(0, 24, 0, 24)
                })
                // Blue bar - not masked
                addView(View(requireContext()).apply {
                    setBackgroundColor(0xFF2196F3.toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 100
                    ).apply { topMargin = 16 }
                })
                // Orange bar - not masked
                addView(View(requireContext()).apply {
                    setBackgroundColor(0xFFFF9800.toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 100
                    ).apply { topMargin = 16 }
                })
            }
            bottomSheet.setContentView(layout)
            bottomSheet.show()
        }

        // Show an AlertDialog positioned at the top of the screen
        binding.topDialogButton.setOnClickListener {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Top Dialog")
                .setMessage("This dialog is positioned at the top of the screen.")
                .setPositiveButton("OK", null)
                .create()
            dialog.show()
            dialog.window?.let { window ->
                val params = window.attributes
                params.gravity = Gravity.TOP
                params.y = 100
                window.attributes = params
            }
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
