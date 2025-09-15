package com.google.mediapipe.examples.handlandmarker

import android.app.Dialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment

class WelcomeDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_COMPANY_NAME = "companyName"

        fun newInstance(companyName: String): WelcomeDialogFragment {
            val fragment = WelcomeDialogFragment()
            val args = Bundle()
            args.putString(ARG_COMPANY_NAME, companyName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_welcome, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val companyName = arguments?.getString(ARG_COMPANY_NAME) ?: ""
        val welcomeText = view.findViewById<TextView>(R.id.welcome_text)
        val welcomeMessage = "Welcome, $companyName!\nApp configured successfully.\nStart performing biometrics."
        val spannable = SpannableString(welcomeMessage)
        if (companyName.isNotEmpty()) {
            val start = welcomeMessage.indexOf(companyName)
            val end = start + companyName.length
            spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        welcomeText.text = spannable

        Handler(Looper.getMainLooper()).postDelayed({
            dismiss()
            startActivity(Intent(requireContext(), MainActivity::class.java))
            requireActivity().finish()
        }, 2000) // 2 seconds delay
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }
}
