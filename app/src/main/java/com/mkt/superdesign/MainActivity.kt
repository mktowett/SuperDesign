package com.mkt.superdesign

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Camera
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.Sun
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.DecimalFormat
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    val MIN_OPENGL_VERSION = 3.0
    private var upDistance = 0f
    private lateinit var arFragment: ArFragment
    private var andyRenderable: ModelRenderable? = null
    private var andyRenderableCube: ModelRenderable? = null
    private var myanchornode: AnchorNode? = null
    private val form_numbers = DecimalFormat("#0.00 m")
    private var anchor1: Anchor? = null
    private  var anchor2:Anchor? = null
    private var myhit: HitResult? = null
    private var text: TextView? = null
    private var sk_height_control: SeekBar? = null
    var anchorNodes: MutableList<AnchorNode> = ArrayList()
    var lineNodes: MutableList<Node> = ArrayList()
    private var measure_height = false
    private val arl_saved = ArrayList<String>()
    private var fl_measurement = 0.0f
    private val message: String? = null
    var mFabMenu: FloatingActionButton? = null
    var mFabWidth:  FloatingActionButton? = null
    var mFabHeight: FloatingActionButton? = null
    var mFabCapture: FloatingActionButton? = null
    var mFabClear: FloatingActionButton? = null
    var mAllFabsVisible = false


    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = (supportFragmentManager.findFragmentById(R.id.ux_fragment) as ArFragment?)!!
        text = findViewById<View>(R.id.text) as TextView

        if(!checkIsSupportedDeviceOrFinish(this)) {
            return
        }
        
        try {
            this.supportActionBar!!.hide()
        } catch (e: NullPointerException) {
            Log.d(TAG,e.message.toString())
        }
        
        // Request to write external storage
        if (ActivityCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this@MainActivity, arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), 1001
            )
        } 
        
        // Request to write external storage
        if (ActivityCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(
                    Manifest.permission.CAMERA
                ), 1002
            )
        }

        mFabClear!!.visibility = View.GONE
        mFabWidth!!.visibility = View.GONE
        mFabHeight!!.visibility = View.GONE
        mFabCapture!!.visibility = View.GONE

        mAllFabsVisible = false

        mFabMenu!!.setOnClickListener {
            if (!mAllFabsVisible) {
                mFabClear!!.show()
                mFabWidth!!.show()
                mFabHeight!!.show()
                mFabCapture!!.show()
            } else {
                mFabClear!!.hide()
                mFabWidth!!.hide()
                mFabHeight!!.hide()
                mFabCapture!!.hide()
            }
            mAllFabsVisible = !mAllFabsVisible
        }



        sk_height_control = findViewById<View>(R.id.sk_height_control) as SeekBar
        sk_height_control!!.isEnabled = false

        mFabClear!!.setOnClickListener {
            resetLayout()
            emptyNodes()
            measure_height = false
            text!!.text = "Select width or height"
            mFabMenu!!.callOnClick()
        }

        mFabWidth!!.setOnClickListener {
            resetLayout()
            measure_height = false
            text!!.text = "Click the extremes you want to measure"
            mFabMenu!!.callOnClick()
        }

        mFabHeight!!.setOnClickListener {
            resetLayout()
            emptyNodes()
            measure_height = true
            text!!.text = "Click the base of the object you want to measure"
            mFabMenu!!.callOnClick()
        }

        mFabCapture!!.setOnClickListener {
            if (fl_measurement != 0.0f) saveDialog() else Toast.makeText(
                this@MainActivity,
                "Make a measurement before saving",
                Toast.LENGTH_SHORT
            ).show()
            mFabMenu!!.callOnClick()
        }
        sk_height_control!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                upDistance = progress.toFloat()
                fl_measurement = progress / 100f
                text!!.text = "Height: " + form_numbers.format(fl_measurement.toDouble())
                myanchornode!!.localScale = Vector3(0.1f, progress / 10f, 0.3f)
                //ascend(myanchornode, upDistance);
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        ModelRenderable.builder()
            .setSource(this, R.raw.pin)
            .build()
            .thenAccept { renderable: ModelRenderable ->
                andyRenderable = renderable
            }
            .exceptionally { throwable: Throwable? ->
                val toast =
                    Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG)
                toast.setGravity(Gravity.CENTER, 0, 0)
                toast.show()
                null
            }

        ModelRenderable.builder()
            .setSource(this, R.raw.cubito3)
            .build()
            .thenAccept { renderable: ModelRenderable ->
                andyRenderableCube = renderable
            }
            .exceptionally { throwable: Throwable? ->
                val toast =
                    Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG)
                toast.setGravity(Gravity.CENTER, 0, 0)
                toast.show()
                null 
            }
        arFragment!!.setOnTapArPlaneListener { hitResult: HitResult, plane: Plane?, motionEvent: MotionEvent? ->
            if (andyRenderable == null || andyRenderableCube == null) {
                return@setOnTapArPlaneListener
            }
            myhit = hitResult
            if (!measure_height && anchorNodes.size % 2 == 0) {
                emptyNodes()
            }

            // Create the Anchor.
            val anchor = hitResult.createAnchor()
            val anchorNode =
                AnchorNode(anchor)
            if (!measure_height) {
                anchorNode.localScale = Vector3(1.0f, 1.0f, 1.0f)
                anchorNode.localRotation = Quaternion.axisAngle(
                    Vector3(1.0f, 0f, 0f),
                    90f
                )
            } else anchorNode.localScale = Vector3(0.25f, 0.01f, 0.25f)
            anchorNode.setParent(arFragment!!.arSceneView.scene)
            if (!measure_height) {
                if (anchor2 != null) {
                    emptyAnchors()
                }
                if (anchor1 == null) {
                    anchor1 = anchor
                } else {
                    anchor2 = anchor
                    fl_measurement = getMetersBetweenAnchors(anchor1!!, anchor2!!)
                    text!!.text = "Width: " +
                            form_numbers.format(fl_measurement.toDouble())
                }
            } else {
                emptyAnchors()
                anchor1 = anchor
                text!!.text = "Move the slider till the bar reaches the upper base"
                sk_height_control!!.isEnabled = true
            }
            myanchornode = anchorNode
            anchorNodes.add(anchorNode)

            // Create the transformable andy and add it to the anchor.
            val andy =
                TransformableNode(arFragment!!.transformationSystem)
            andy.setParent(anchorNode)
            if (!measure_height) {
                andy.renderable = andyRenderable
                if (anchorNodes.size % 2 == 0) {
                    val lastIndex = anchorNodes.size - 1
                    val pos1 = anchorNodes[lastIndex].worldPosition
                    val pos2 = anchorNodes[lastIndex - 1].worldPosition
                    lineBetweenPoints(pos1, pos2)
                }
            } else andy.renderable = andyRenderableCube
            andy.select()
            andy.scaleController.isEnabled = false
            andy.translationController.isEnabled = true
            andy.setOnTouchListener { hitTestResult, motionEvent ->
                if (!measure_height) {
                    if (anchorNodes.size % 2 == 0) {
                        emptyLineNodes()
                        val lastIndex = anchorNodes.size - 1
                        val pos1 = anchorNodes[lastIndex].worldPosition
                        val pos2 =
                            anchorNodes[lastIndex - 1].worldPosition
                        lineBetweenPoints(pos1, pos2)
                        text!!.text = "Width: " +
                                form_numbers.format(
                                    calculateDistance(
                                        pos1,
                                        pos2
                                    ).toDouble()
                                )
                    }
                }
                return@setOnTouchListener false
            }
        }

        val isRightToLeft = resources.getBoolean(R.bool.is_right_to_left)
        if (isRightToLeft) {
            val params = sk_height_control!!.layoutParams as CoordinatorLayout.LayoutParams
            params.setMargins(75, 0, 25, 25)
            sk_height_control!!.layoutParams = params
        }
    }
    //calculateDistance
    private fun calculateDistance(pos1: Vector3, pos2: Vector3): Float {
        val deltaX = pos1.x - pos2.x
        val deltaY = pos1.y - pos2.y
        val deltaZ = pos1.z - pos2.z
        return sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()).toFloat()
    }

    private fun getMetersBetweenAnchors(anchor1: Anchor, anchor2: Anchor): Float {
        val distance_vector = anchor1.pose.inverse()
            .compose(anchor2.pose).translation
        var totalDistanceSquared = 0f
        for (i in 0..2) totalDistanceSquared += distance_vector[i] * distance_vector[i]
        return sqrt(totalDistanceSquared.toDouble()).toFloat()
    }

    private fun ascend(an: AnchorNode, up: Float) {
        val anchor = myhit!!.trackable.createAnchor(
            myhit!!.hitPose.compose(Pose.makeTranslation(0f, up / 100f, 0f))
        )
        an.anchor = anchor
    }


    private fun checkIsSupportedDeviceOrFinish(activity: Activity): Boolean {
        val openGlVersionString = (activity.getSystemService(ACTIVITY_SERVICE) as ActivityManager)
            .deviceConfigurationInfo
            .glEsVersion
        if (openGlVersionString.toDouble() < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later")
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                .show()
            activity.finish()
            return false
        }
        return true
    }

    private fun saveDialog() {
        val mBuilder = AlertDialog.Builder(this@MainActivity)
        val mView = layoutInflater.inflate(R.layout.dialog_save, null)
        val et_measure = mView.findViewById<View>(R.id.et_measure) as EditText
        mBuilder.setTitle("Measurement title")
        mBuilder.setPositiveButton(
            "Ok"
        ) { dialogInterface, i ->
            if (et_measure.length() != 0) {
                arl_saved.add(et_measure.text.toString() + ": " + form_numbers.format(fl_measurement.toDouble()))
                dialogInterface.dismiss()
                takeScreenshotAR(et_measure.text.toString())
            } else Toast.makeText(this@MainActivity, "Title can't be empty", Toast.LENGTH_SHORT)
                .show()
        }
        mBuilder.setView(mView)
        val dialog = mBuilder.create()
        dialog.show()
    }

    /**
     * Set layout to its initial state
     */
    private fun resetLayout() {
        sk_height_control!!.progress = 0
        sk_height_control!!.isEnabled = false
        measure_height = false
        emptyAnchors()
    }

    private fun emptyAnchors() {
        anchor1 = null
        anchor2 = null
        for (n in anchorNodes) {
            arFragment!!.arSceneView.scene.removeChild(n)
            n.anchor!!.detach()
            n.setParent(null)
        }
    }

    private fun emptyNodes() {
        val children: List<Node> = java.util.ArrayList(
            arFragment!!.arSceneView.scene.children
        )
        for (node in children) {
            if (node is AnchorNode) {
                if (node.anchor != null) {
                    node.anchor!!.detach()
                }
            }
            if (node !is Camera && node !is Sun) {
                node.setParent(null)
            }
        }
    }

    private fun emptyLineNodes() {
        for (node in lineNodes) {
            if (node is AnchorNode) {
                if (node.anchor != null) {
                    node.anchor!!.detach()
                }
            }
            if (node !is Camera && node !is Sun) {
                node.setParent(null)
            }
        }
    }

    // Take screen of Main fragement Scene
    private fun takeScreenshot(filename: String) {
        try {
            // image naming and path  to include sd card  appending name you choose for file
            val mPath = Environment.getExternalStorageDirectory().toString() + "/" + filename + ".jpg"
            // create bitmap screen capture
            val v1 = window.decorView.rootView
            v1.isDrawingCacheEnabled = true
            val bitmap = Bitmap.createBitmap(v1.drawingCache)
            v1.isDrawingCacheEnabled = false
            val imageFile = File(mPath)
            val outputStream = FileOutputStream(imageFile)
            val quality = 100
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            outputStream.flush()
            outputStream.close()
//            openScreenshot(imageFile);
        } catch (e: Throwable) {
            // Several error may come out with file handling or DOM
            e.printStackTrace()
        }
    }

    // Take screen of AR fragement Scene
    private fun takeScreenshotAR(filename: String) {
        // image naming and path  to include sd card  appending name you choose for file
        val mPath = Environment.getExternalStorageDirectory().toString() + "/" + filename + ".jpg"
        val view = arFragment!!.arSceneView

        // Create a bitmap the size of the scene view.
        val bitmap = Bitmap.createBitmap(
            view.width, view.height,
            Bitmap.Config.ARGB_8888
        )

        // Create a handler thread to offload the processing of the image.
        val handlerThread = HandlerThread("PixelCopier")
        handlerThread.start()
        // Make the request to copy.
        PixelCopy.request(view, bitmap, { copyResult: Int ->
            if (copyResult == PixelCopy.SUCCESS) {
                try {
                    // Get bitmap for measurement text
                    val v1 = window.decorView.rootView
                        .findViewById<View>(R.id.text)
                    v1.isDrawingCacheEnabled = true
                    val bitmapText = Bitmap.createBitmap(v1.drawingCache)
                    v1.isDrawingCacheEnabled = false
                    val bitmapOverlay = overlay(bitmap, bitmapText)
                    saveBitmapToDisk(bitmapOverlay, filename)
                } catch (e: IOException) {
                    Log.d(TAG, e.toString())
                    return@request
                }
                //                SnackbarUtility.showSnackbarTypeLong(settingsButton, "Screenshot saved in /Pictures/Screenshots");
                Toast.makeText(
                    this@MainActivity,
                    "Screenshot saved in /Pictures/Screenshots",
                    Toast.LENGTH_LONG
                ).show()
            } else {
//                SnackbarUtility.showSnackbarTypeLong(settingsButton, "Failed to take screenshot");
                Toast.makeText(this@MainActivity, "Failed to take screenshoot", Toast.LENGTH_LONG)
                    .show()
            }
            handlerThread.quitSafely()
        }, Handler(handlerThread.looper))
    }

    private fun overlay(bmp1: Bitmap, bmp2: Bitmap): Bitmap {
        val bmOverlay = Bitmap.createBitmap(bmp1.width, bmp1.height, bmp1.config)
        val canvas = Canvas(bmOverlay)
        canvas.drawBitmap(bmp1, Matrix(), null)
        canvas.drawBitmap(bmp2, Matrix(), null)
        return bmOverlay
    }

    @Throws(IOException::class)
    fun saveBitmapToDisk(bitmap: Bitmap, filename: String) {
        try {
            val videoDirectory: File
            val isSDPresent = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
            val isSDSupportedDevice = Environment.isExternalStorageRemovable()
            videoDirectory = if (isSDSupportedDevice && isSDPresent) {
                // yes SD-card is present
                File(
                    Environment.getExternalStorageDirectory()
                        .toString() + File.separator + "Screenshots"
                )
            } else {
                File(filesDir.toString() + File.separator + "Screenshots")
            }
            if (!videoDirectory.exists() && !videoDirectory.isDirectory) videoDirectory.mkdir()
            val mediaFile = File(videoDirectory, "$filename.jpeg")
            val fileOutputStream = FileOutputStream(mediaFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream)
            fileOutputStream.flush()
            fileOutputStream.close()
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, "Error writing screenshoot : $e", Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun lineBetweenPoints(point1: Vector3?, point2: Vector3?) {
        /* First, find the vector extending between the two points and define a look rotation in terms of this
        Vector. */
        val difference = Vector3.subtract(point1, point2)
        val directionFromTopToBottom = difference.normalized()
        val rotationFromAToB = Quaternion.lookRotation(directionFromTopToBottom, Vector3.up())
        MaterialFactory.makeOpaqueWithColor(applicationContext, Color(255F, 0F, 0F))
            .thenAccept { material: Material? ->
                val model = ShapeFactory.makeCube(
                    Vector3(.01f, .01f, difference.length()),
                    Vector3.zero(), material
                )
                val node = Node()
                node.setParent(arFragment!!.arSceneView.scene)
                node.renderable = model
                node.worldPosition = Vector3.add(point1, point2).scaled(.5f)
                node.worldRotation = rotationFromAToB
                lineNodes.add(node)
            }
    }
}