package com.biometrics.fragment

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ConfirmationDialogFragment : DialogFragment() {

    interface ConfirmationListener {
        fun onConfirmation(confirmed: Boolean)
    }

    private var listener: ConfirmationListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        parentFragment?.let {
            if (it is ConfirmationListener) {
                listener = it
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cancel Biometrics")
            .setMessage("Are you sure you want to cancel biometrics?")
            .setPositiveButton("Yes") { _, _ ->
                listener?.onConfirmation(true)
            }
            .setNegativeButton("No") { _, _ ->
                listener?.onConfirmation(false)
            }
            .create()
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    companion object {
        const val TAG = "ConfirmationDialog"
    }
}