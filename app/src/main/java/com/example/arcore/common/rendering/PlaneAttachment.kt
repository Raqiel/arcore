package com.example.arcore.common.rendering

import com.google.ar.core.Anchor
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState


class PlaneAttachment(var plane: Plane, var anchor: Anchor) {

    val isTracking: Boolean
        get() = (plane.trackingState == TrackingState.TRACKING
                && anchor.trackingState == TrackingState.TRACKING)

    val pose: Pose
        get() {
            val pose = anchor.pose
            pose.getTranslation(poseTranslation, 0)
            pose.getRotationQuaternion(poseRotation, 0)
            poseTranslation[1] = plane.centerPose.ty()
            return Pose(
                poseTranslation,
                poseRotation
            )
        }

    companion object {
        private val poseTranslation = FloatArray(3)
        private val poseRotation = FloatArray(4)
    }

}