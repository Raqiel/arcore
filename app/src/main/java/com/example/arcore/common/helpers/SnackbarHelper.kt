package com.example.arcore.common.helpers

import android.app.Activity
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar

class SnackbarHelper {

    private var messageSnackbar: Snackbar? = null

    private var lastMessage = ""
    private val isShowing: Boolean
        get() = messageSnackbar != null

    /**
     * Shows a snackbar with a given message.
     */
    fun showMessage(activity: Activity, message: String) {
        if (!message.isEmpty() && (!isShowing || lastMessage != message)) {
            lastMessage = message
            show(activity, message, DismissBehavior.HIDE)
        }
    }

    /**
     * Shows a snackbar with a given message, and a dismiss button.
     */
    @Suppress("unused")
    fun showMessageWithDismiss(activity: Activity, message: String?) {
        show(activity, message, DismissBehavior.SHOW)
    }

    /**
     * Shows a snackbar with a given error message. When dismissed, will finish the activity. Useful
     * for notifying errors, where no further interaction with the activity is possible.
     */
    fun showError(activity: Activity, errorMessage: String?) {
        show(activity, errorMessage, DismissBehavior.FINISH)
    }

    /**
     * Hides the currently showing snackbar, if there is one. Safe to call from any thread. Safe to
     * call even if snackbar is not shown.
     */
    fun hide(activity: Activity) {
        if (!isShowing) {
            return
        }
        lastMessage = ""
        val messageSnackbarToHide = messageSnackbar
        messageSnackbar = null
        activity.runOnUiThread { messageSnackbarToHide!!.dismiss() }
    }

    private fun show(
        activity: Activity, message: String?, dismissBehavior: DismissBehavior
    ) {
        activity.runOnUiThread {
            messageSnackbar = Snackbar.make(
                activity.findViewById(android.R.id.content),
                message!!,
                Snackbar.LENGTH_INDEFINITE
            )

            messageSnackbar?.let {
                it.view.setBackgroundColor(BACKGROUND_COLOR)

                if (dismissBehavior != DismissBehavior.HIDE) {
                    it.setAction(
                        "Dismiss"
                    ) { messageSnackbar!!.dismiss() }
                    if (dismissBehavior == DismissBehavior.FINISH) {
                        it.addCallback(
                            object : BaseTransientBottomBar.BaseCallback<Snackbar?>() {
                                override fun onDismissed(
                                    transientBottomBar: Snackbar?,
                                    event: Int
                                ) {
                                    super.onDismissed(transientBottomBar, event)
                                    activity.finish()
                                }
                            })
                    }
                }
                it.show()
            }
        }
    }

    companion object {
        private const val BACKGROUND_COLOR = -0x40cdcdce

        enum class DismissBehavior {
            HIDE, SHOW, FINISH
        }
    }

}