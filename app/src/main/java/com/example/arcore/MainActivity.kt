package com.example.arcore

import android.content.SharedPreferences
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.arcore.common.helpers.CameraPermissionHelper
import com.example.arcore.common.helpers.DepthSettings
import com.example.arcore.common.helpers.DisplayRotationHelper
import com.example.arcore.common.helpers.FullScreenHelper
import com.example.arcore.common.helpers.SnackbarHelper
import com.example.arcore.common.helpers.TrackingStateHelper
import com.example.arcore.common.rendering.BackgroundRenderer
import com.example.arcore.common.rendering.Mode
import com.example.arcore.common.rendering.ObjectRenderer
import com.example.arcore.common.rendering.PlaneAttachment
import com.example.arcore.common.rendering.PlaneRenderer
import com.example.arcore.common.rendering.PointCloudRenderer
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


class MainActivity : AppCompatActivity(), GLSurfaceView.Renderer {

    private val TAG: String = MainActivity::class.java.simpleName


    private var installRequested = false
    private var useDepthForOcclusion = false
    private val sharedPreferences: SharedPreferences? = null

    private var mode: Mode = Mode.VIKING

    private var session: Session? = null

    // Gesture Detector é usado pra detectar gestos na tela
    private lateinit var gestureDetector: GestureDetector
    //gerencia estado de rastreamento do AR
    private lateinit var trackingStateHelper: TrackingStateHelper
    //lida com a tela quando é rotacionada
    private lateinit var displayRotationHelper: DisplayRotationHelper
    //mensagem na tela
    private val messageSnackbarHelper: SnackbarHelper = SnackbarHelper()

    //classe que fornece uma superficie de desenho open GLES onde pode renderizar uma cena de graficos 3d ou 2d
    private lateinit var surfaceView: GLSurfaceView


    //lida com a renderização, sombras, vertices, texturas e etc
    private val backgroundRenderer: BackgroundRenderer = BackgroundRenderer()

    private val planeRenderer: PlaneRenderer = PlaneRenderer()
    private val pointCloudRenderer: PointCloudRenderer = PointCloudRenderer()

    // TODO: Declare ObjectRenderers and PlaneAttachments here
    private val vikingObject = ObjectRenderer()
    private val cannonObject = ObjectRenderer()
    private val targetObject = ObjectRenderer()

    private var vikingAttachment: PlaneAttachment? = null
    private var cannonAttachment: PlaneAttachment? = null
    private var targetAttachment: PlaneAttachment? = null

    // Matriz temporária alocada aqui para reduzir o número de alocações e toques para cada quadro.
    private val maxAllocationSize = 16
    private val anchorMatrix = FloatArray(maxAllocationSize)
    //controla os objetos de acordo com os cliques
    private val queuedSingleTaps = ArrayBlockingQueue<MotionEvent>(maxAllocationSize)
    val depthSettings = DepthSettings()
    override fun onCreate(savedInstanceState: Bundle?) {
//        setTheme(R.style.AppTheme)

        super.onCreate(savedInstanceState)
        setContentView(com.example.arcore.R.layout.activity_main)
        surfaceView = findViewById(com.example.arcore.R.id.surfaceView)
        depthSettings.onCreate(this)

        trackingStateHelper = TrackingStateHelper(this@MainActivity)
        displayRotationHelper = DisplayRotationHelper(this@MainActivity)

        installRequested = false
        showOcclusionDialogIfNeeded()
        setupTapDetector()
        setupSurfaceView()



    }
    fun showOcclusionDialogIfNeeded() {
        val session = session ?: return
        val isDepthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
        if (!this.depthSettings.shouldShowDepthEnableDialog() || !isDepthSupported) {
            return // Don't need to show dialog.
        }

        // Asks the user whether they want to use depth-based occlusion.
        AlertDialog.Builder(this)
            .setTitle("titulo")
            .setMessage("Mensagem")
            .setPositiveButton("Confirmar") { _, _ ->
                this.depthSettings.setUseDepthForOcclusion(true)
            }
            .setNegativeButton("Declinar") { _, _ ->
                this.depthSettings.setUseDepthForOcclusion(false)
            }
            .show()
    }

//    fun onRadioButtonClicked(view: View) {
//        when (view.id) {
//            com.example.arcore.R.id.radioCannon -> mode = Mode.CANNON
//            com.example.arcore.R.id.radioTarget -> mode = Mode.TARGET
//            else -> mode = Mode.VIKING
//        }
//    }

    private fun setupSurfaceView() {
        // Set up renderer.
        surfaceView.preserveEGLContextOnPause = true
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, maxAllocationSize, 0)
        surfaceView.setRenderer(this)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        surfaceView.setWillNotDraw(false)
        surfaceView.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
    }

    private fun setupTapDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {



                onSingleTap(e)
                return true
            }

            override fun onDown(e: MotionEvent): Boolean {
                return true
            }
        })
    }

    private fun onSingleTap(e: MotionEvent) {
        // Queue tap if there is space. Tap is lost if queue is full.
        queuedSingleTaps.offer(e)
    }

    override fun onResume() {
        super.onResume()

        if (session == null) {
            if (!setupSession()) {
                return
            }
        }

        try {
            session?.resume()
            showOcclusionDialogIfNeeded()
        } catch (e: CameraNotAvailableException) {
            messageSnackbarHelper.showError(this@MainActivity, getString(com.example.arcore.R.string.camera_not_available))
            session = null
            return
        }

        surfaceView.onResume()
        displayRotationHelper.onResume()
    }

    private fun setupSession(): Boolean {
        var exception: Exception? = null
        var message: String? = null
        // função p ver se a sessão foi instalada

        try {
            when (ArCoreApk.getInstance().requestInstall(this@MainActivity, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    return false
                }
                ArCoreApk.InstallStatus.INSTALLED -> {
                }
                else -> {
                    message = getString(com.example.arcore.R.string.arcore_install_failed)
                }
            }

            // Requesting Camera Permission
            if (!CameraPermissionHelper.hasCameraPermission(this@MainActivity)) {
                CameraPermissionHelper.requestCameraPermission(this@MainActivity)
                return false
            }

            // Create the session.
            session = Session(this@MainActivity)

        } catch (e: UnavailableArcoreNotInstalledException) {
            message = getString(com.example.arcore.R.string.please_install_arcore)
            exception = e
        } catch (e: UnavailableUserDeclinedInstallationException) {
            message = getString(com.example.arcore.R.string.please_install_arcore)
            exception = e
        } catch (e: UnavailableApkTooOldException) {
            message = getString(com.example.arcore.R.string.please_update_arcore)
            exception = e
        } catch (e: UnavailableSdkTooOldException) {
            message = getString(com.example.arcore.R.string.please_update_app)
            exception = e
        } catch (e: UnavailableDeviceNotCompatibleException) {
            message = getString(com.example.arcore.R.string.arcore_not_supported)
            exception = e
        } catch (e: Exception) {
            message = getString(com.example.arcore.R.string.failed_to_create_session)
            exception = e
        }

        if (message != null) {
            messageSnackbarHelper.showError(this@MainActivity, message)
            Log.e(TAG, getString(com.example.arcore.R.string.failed_to_create_session), exception)
            return false
        }

        return true
    }

    override fun onPause() {
        super.onPause()

        if (session != null) {
            displayRotationHelper.onPause()
            surfaceView.onPause()
            session!!.pause()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (!CameraPermissionHelper.hasCameraPermission(this@MainActivity)) {
            Toast.makeText(
                this@MainActivity,
                getString(com.example.arcore.R.string.camera_permission_needed),
                Toast.LENGTH_LONG
            ).show()

            // Permission denied with checking "Do not ask again".
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this@MainActivity)) {
                CameraPermissionHelper.launchPermissionSettings(this@MainActivity)
            }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        FullScreenHelper.setFullScreenOnWindowFocusChanged(this@MainActivity, hasFocus)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(this@MainActivity)
            planeRenderer.createOnGlThread(this@MainActivity, getString(com.example.arcore.R.string.model_grid_png))
            pointCloudRenderer.createOnGlThread(this@MainActivity)

            // TODO - set up the objects
            // 1
            vikingObject.createOnGlThread(this@MainActivity, getString(com.example.arcore.R.string.model_viking_obj), getString(
                com.example.arcore.R.string.model_viking_png))
            cannonObject.createOnGlThread(this@MainActivity, getString(com.example.arcore.R.string.model_cannon_obj), getString(
                com.example.arcore.R.string.model_cannon_png))
            targetObject.createOnGlThread(this@MainActivity, getString(com.example.arcore.R.string.model_target_obj), getString(
                com.example.arcore.R.string.model_target_png))

            // 2
            targetObject.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f)
            vikingObject.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f)
            cannonObject.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f)

        } catch (e: IOException) {
            Log.e(TAG, getString(com.example.arcore.R.string.failed_to_read_asset), e)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        session?.let {
            // Notify ARCore session that the view size changed
            displayRotationHelper.updateSessionIfNeeded(it)

            try {
                it.setCameraTextureName(backgroundRenderer.textureId)

                val frame = it.update()
                val camera = frame.camera

                // Handle one tap per frame.
                handleTap(frame, camera)
                drawBackground(frame)

                // Keeps the screen unlocked while tracking, but allow it to lock when tracking stops.
                trackingStateHelper.updateKeepScreenOnFlag(camera.trackingState)

                // If not tracking, don't draw 3D objects, show tracking failure reason instead.
                if (!isInTrackingState(camera)) return

                val projectionMatrix = computeProjectionMatrix(camera)
                val viewMatrix = computeViewMatrix(camera)
                val lightIntensity = computeLightIntensity(frame)

                visualizeTrackedPoints(frame, projectionMatrix, viewMatrix)
                checkPlaneDetected()
                visualizePlanes(camera, projectionMatrix)

                // TODO: Call drawObject() for Viking, Cannon and Target here
                drawObject(
                    vikingObject,
                    vikingAttachment,
                    Mode.VIKING.scaleFactor,
                    projectionMatrix,
                    viewMatrix,
                    lightIntensity
                )

                drawObject(
                    cannonObject,
                    cannonAttachment,
                    Mode.CANNON.scaleFactor,
                    projectionMatrix,
                    viewMatrix,
                    lightIntensity
                )

            } catch (t: Throwable) {
                Log.e(TAG, getString(com.example.arcore.R.string.exception_on_opengl), t)
            }
        }
    }

    fun setUseDepthForOcclusion(enable: Boolean) {
        if (enable == useDepthForOcclusion) {
            return  // No change.
        }

        // Updates the stored default settings.
        useDepthForOcclusion = enable
        val editor = sharedPreferences!!.edit()
        editor.putBoolean(
            DepthSettings.SHARED_PREFERENCES_USE_DEPTH_FOR_OCCLUSION,
            useDepthForOcclusion
        )
        editor.apply()
    }

    private fun isInTrackingState(camera: Camera): Boolean {
        if (camera.trackingState == TrackingState.PAUSED) {
            messageSnackbarHelper.showMessage(
                this@MainActivity, TrackingStateHelper.getTrackingFailureReasonString(camera)
            )
            return false
        }

        return true
    }

    private fun drawObject(
        objectRenderer: ObjectRenderer,
        planeAttachment: PlaneAttachment?,
        scaleFactor: Float,
        projectionMatrix: FloatArray,
        viewMatrix: FloatArray,
        lightIntensity: FloatArray
    ) {
        if (planeAttachment?.isTracking == true) {
            planeAttachment.pose.toMatrix(anchorMatrix, 0)

            // Update and draw the model
            objectRenderer.updateModelMatrix(anchorMatrix, scaleFactor)
            objectRenderer.draw(viewMatrix, projectionMatrix, lightIntensity)
        }
    }

    private fun drawBackground(frame: Frame) {
        backgroundRenderer.draw(frame)
    }

    private fun computeProjectionMatrix(camera: Camera): FloatArray {
        val projectionMatrix = FloatArray(maxAllocationSize)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)

        return projectionMatrix
    }

    private fun computeViewMatrix(camera: Camera): FloatArray {
        val viewMatrix = FloatArray(maxAllocationSize)
        camera.getViewMatrix(viewMatrix, 0)

        return viewMatrix
    }

    /**
     * Compute lighting from average intensity of the image.
     */
    private fun computeLightIntensity(frame: Frame): FloatArray {
        val lightIntensity = FloatArray(4)
        frame.lightEstimate.getColorCorrection(lightIntensity, 0)

        return lightIntensity
    }

    /**
     * Visualizes tracked points.
     */
    private fun visualizeTrackedPoints(
        frame: Frame,
        projectionMatrix: FloatArray,
        viewMatrix: FloatArray
    ) {
        // Use try-with-resources to automatically release the point cloud.
        frame.acquirePointCloud().use { pointCloud ->
            pointCloudRenderer.update(pointCloud)
            pointCloudRenderer.draw(viewMatrix, projectionMatrix)
        }
    }

    /**
     *  Visualizes planes.
     */
    private fun visualizePlanes(camera: Camera, projectionMatrix: FloatArray) {
        planeRenderer.drawPlanes(
            session!!.getAllTrackables(Plane::class.java),
            camera.displayOrientedPose,
            projectionMatrix
        )
    }

    /**
     * Checks if any tracking plane exists then, hide the message UI, otherwise show searchingPlane message.
     */
    private fun checkPlaneDetected() {
        if (hasTrackingPlane()) {
            messageSnackbarHelper.hide(this@MainActivity)
        } else {
            messageSnackbarHelper.showMessage(
                this@MainActivity,
                getString(com.example.arcore.R.string.searching_for_surfaces)
            )
        }
    }

    /**
     * Checks if we detected at least one plane.
     */
    private fun hasTrackingPlane(): Boolean {
        val allPlanes = session!!.getAllTrackables(Plane::class.java)

        for (plane in allPlanes) {
            if (plane.trackingState == TrackingState.TRACKING) {
                return true
            }
        }

        return false
    }

    /**
     * Handle a single tap per frame
     */
    private fun handleTap(frame: Frame, camera: Camera) {
        val tap = queuedSingleTaps.poll()

        if (tap != null && camera.trackingState == TrackingState.TRACKING) {
            // Check if any plane was hit, and if it was hit inside the plane polygon
            for (hit in frame.hitTest(tap)) {
                val trackable = hit.trackable

                if ((trackable is Plane
                            && trackable.isPoseInPolygon(hit.hitPose)
                            && PlaneRenderer.calculateDistanceToPlane(hit.hitPose, camera.pose) > 0)
                    || (trackable is Point
                            && trackable.orientationMode
                            == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
                ) {
                    when (mode) {
                        Mode.VIKING -> vikingAttachment = addSessionAnchorFromAttachment(vikingAttachment, hit)
                        Mode.CANNON -> cannonAttachment = addSessionAnchorFromAttachment(cannonAttachment, hit)
                        Mode.TARGET -> targetAttachment = addSessionAnchorFromAttachment(targetAttachment, hit)
                    }
                    // TODO: Create an anchor if a plane or an oriented point was hit
                    break
                }
            }
        }
    }

    // TODO: Add addSessionAnchorFromAttachment() function here
    private fun addSessionAnchorFromAttachment(
        previousAttachment: PlaneAttachment?,
        hit: HitResult
    ): PlaneAttachment? {
        // 1
        previousAttachment?.anchor?.detach()

        // 2
        val plane = hit.trackable as Plane
        val anchor = session!!.createAnchor(hit.hitPose)

        // 3
        return PlaneAttachment(plane, anchor)
    }

}
