package com.google.mediapipe.examples.handlandmarker

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment

class BiometricsCompletedDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_biometrics_completed, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val okButton: Button = view.findViewById(R.id.dialog_ok_button)
        okButton.setOnClickListener {
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            // Optional: Set dialog properties like title, cancellable, etc.
            setCanceledOnTouchOutside(false)
        }
    }

    companion object {
        const val TAG = "BiometricsCompletedDialogFragment"
    }
}