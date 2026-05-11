package com.mixpanel.sessionreplaydemo.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.sessionreplaydemo.Constants
import com.mixpanel.sessionreplaydemo.databinding.FragmentNotificationsBinding

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val notificationsViewModel =
            ViewModelProvider(this).get(NotificationsViewModel::class.java)

        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textNotifications
        notificationsViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }

        MixpanelAPI
            .getInstance(this.context, Constants.MIXPANEL_TOKEN, true)
            .track("Tapped Notifications")

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
