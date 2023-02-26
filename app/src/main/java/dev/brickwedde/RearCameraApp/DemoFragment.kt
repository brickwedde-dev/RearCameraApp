/*
 * Copyright 2017-2022 Jiangdg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.brickwedde.RearCameraApp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import dev.brickwedde.RearCameraApp.databinding.FragmentDemoBinding
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.ICaptureCallBack
import com.jiangdg.ausbc.callback.IPlayCallBack
import com.jiangdg.ausbc.utils.*
import com.jiangdg.ausbc.utils.bus.BusKey
import com.jiangdg.ausbc.utils.bus.EventBus
import com.jiangdg.utils.imageloader.ILoader
import com.jiangdg.utils.imageloader.ImageLoaders
import com.jiangdg.ausbc.widget.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

/** CameraFragment Usage Demo
 *
 * @author Created by jiangdg on 2022/1/28
 */
class DemoFragment : CameraFragment(), View.OnClickListener, CaptureMediaView.OnViewClickListener {
    private var mMoreMenu: PopupWindow? = null
    private var isCapturingVideoOrAudio: Boolean = false
    private var isPlayingMic: Boolean = false
    private var mRecTimer: Timer? = null
    private var mRecSeconds = 0
    private var mRecMinute = 0
    private var mRecHours = 0

    private val mTakePictureTipView: TipView by lazy {
        mViewBinding.takePictureTipViewStub.inflate() as TipView
    }

    private val mMainHandler: Handler by lazy {
        Handler(Looper.getMainLooper()) {
            when(it.what) {
                WHAT_START_TIMER -> {
                    if (mRecSeconds % 2 != 0) {
                        mViewBinding.recStateIv.visibility = View.VISIBLE
                    } else {
                        mViewBinding.recStateIv.visibility = View.INVISIBLE
                    }
                    mViewBinding.recTimeTv.text = calculateTime(mRecSeconds, mRecMinute)
                }
                WHAT_STOP_TIMER -> {
                    mViewBinding.toolbarGroup.visibility = View.VISIBLE
                    mViewBinding.recTimerLayout.visibility = View.GONE
                    mViewBinding.recTimeTv.text = calculateTime(0, 0)
                }
            }
            true
        }
    }

    private var mCameraMode = CaptureMediaView.CaptureMode.MODE_CAPTURE_PIC

    private lateinit var mViewBinding: FragmentDemoBinding

    override fun initView() {
        super.initView()
        mViewBinding.cameraTypeBtn.setOnClickListener(this)
        mViewBinding.settingsBtn.setOnClickListener(this)
        mViewBinding.resolutionBtn.setOnClickListener(this)
        switchLayoutClick()
    }

    override fun initData() {
        super.initData()
        EventBus.with<Int>(BusKey.KEY_FRAME_RATE).observe(this, {
            mViewBinding.frameRateTv.text = "frame rate:  $it fps"
        })
    }

    override fun onCameraState(
        self: MultiCameraClient.ICamera,
        code: ICameraStateCallBack.State,
        msg: String?
    ) {
        when (code) {
            ICameraStateCallBack.State.OPENED -> handleCameraOpened()
            ICameraStateCallBack.State.CLOSED -> handleCameraClosed()
            ICameraStateCallBack.State.ERROR -> handleCameraError(msg)
        }
    }

    private fun handleCameraError(msg: String?) {
        mViewBinding.uvcLogoIv.visibility = View.VISIBLE
        mViewBinding.frameRateTv.visibility = View.GONE
        ToastUtils.show("camera opened error: $msg")
    }

    private fun handleCameraClosed() {
        mViewBinding.uvcLogoIv.visibility = View.VISIBLE
        mViewBinding.frameRateTv.visibility = View.GONE
        ToastUtils.show("camera closed success")
    }

    private fun handleCameraOpened() {
        mViewBinding.uvcLogoIv.visibility = View.GONE
        mViewBinding.frameRateTv.visibility = View.VISIBLE
        ToastUtils.show("camera opened success")
    }

    private fun switchLayoutClick() {
        updateCameraModeSwitchUI()
        showRecentMedia()
    }

    override fun getCameraView(): IAspectRatio {
        return AspectRatioTextureView(requireContext())
    }

    override fun getCameraViewContainer(): ViewGroup {
        return mViewBinding.cameraViewContainer
    }

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View {
        mViewBinding = FragmentDemoBinding.inflate(inflater, container, false)
        return mViewBinding.root
    }

    override fun getGravity(): Int = Gravity.CENTER

    override fun onViewClick(mode: CaptureMediaView.CaptureMode?) {
        if (! isCameraOpened()) {
            ToastUtils.show("camera not worked!")
            return
        }
        when (mode) {
            CaptureMediaView.CaptureMode.MODE_CAPTURE_PIC -> {
                captureImage()
            }
            CaptureMediaView.CaptureMode.MODE_CAPTURE_AUDIO -> {
                captureAudio()
            }
            else -> {
                captureVideo()
            }
        }
    }

    private fun captureAudio() {
        if (isCapturingVideoOrAudio) {
            captureAudioStop()
            return
        }
        captureAudioStart(object : ICaptureCallBack {
            override fun onBegin() {
                isCapturingVideoOrAudio = true
                mViewBinding.toolbarGroup.visibility = View.GONE
                mViewBinding.recTimerLayout.visibility = View.VISIBLE
                startMediaTimer()
            }

            override fun onError(error: String?) {
                ToastUtils.show(error ?: "未知异常")
                isCapturingVideoOrAudio = false
                stopMediaTimer()
            }

            override fun onComplete(path: String?) {
                isCapturingVideoOrAudio = false
                mViewBinding.toolbarGroup.visibility = View.VISIBLE
                mViewBinding.recTimerLayout.visibility = View.GONE
                stopMediaTimer()
                ToastUtils.show(path ?: "error")
            }

        })
    }

    private fun captureVideo() {
        if (isCapturingVideoOrAudio) {
            captureVideoStop()
            return
        }
        captureVideoStart(object : ICaptureCallBack {
            override fun onBegin() {
                isCapturingVideoOrAudio = true
                mViewBinding.toolbarGroup.visibility = View.GONE
                mViewBinding.recTimerLayout.visibility = View.VISIBLE
                startMediaTimer()
            }

            override fun onError(error: String?) {
                ToastUtils.show(error ?: "未知异常")
                isCapturingVideoOrAudio = false
                stopMediaTimer()
            }

            override fun onComplete(path: String?) {
                ToastUtils.show(path ?: "")
                isCapturingVideoOrAudio = false
                mViewBinding.toolbarGroup.visibility = View.VISIBLE
                mViewBinding.recTimerLayout.visibility = View.GONE
                showRecentMedia(false)
                stopMediaTimer()
            }

        })
    }

    private fun captureImage() {
        captureImage(object : ICaptureCallBack {
            override fun onBegin() {
                mTakePictureTipView.show("", 100)
            }

            override fun onError(error: String?) {
                ToastUtils.show(error ?: "未知异常")
            }

            override fun onComplete(path: String?) {
                showRecentMedia(true)
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onClick(v: View?) {
//        if (! isCameraOpened()) {
//            ToastUtils.show("camera not worked!")
//            return
//        }
        clickAnimation(v!!, object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                when (v) {
                    mViewBinding.cameraTypeBtn -> {
                    }
                    mViewBinding.settingsBtn -> {
                    }
                    mViewBinding.resolutionBtn -> {
                        showResolutionDialog()
                    }
                    else -> {
                    }
                }
            }
        })
    }

    @SuppressLint("CheckResult")
    private fun showUsbDevicesDialog(usbDeviceList: MutableList<UsbDevice>?, curDevice: UsbDevice?) {
        if (usbDeviceList.isNullOrEmpty()) {
            ToastUtils.show("Get usb device failed")
            return
        }
        val list = arrayListOf<String>()
        var selectedIndex: Int = -1
        for (index in (0 until usbDeviceList.size)) {
            val dev = usbDeviceList[index]
            val devName = if (Build.VERSION.SDK_INT >=Build.VERSION_CODES.LOLLIPOP && !dev.productName.isNullOrEmpty()) {
                "${dev.productName}(${curDevice?.deviceId})"
            } else {
                dev.deviceName
            }
            val curDevName = if (Build.VERSION.SDK_INT >=Build.VERSION_CODES.LOLLIPOP && !curDevice?.productName.isNullOrEmpty()) {
                "${curDevice!!.productName}(${curDevice.deviceId})"
            } else {
                curDevice?.deviceName
            }
            if (devName == curDevName) {
                selectedIndex = index
            }
            list.add(devName)
        }
        MaterialDialog(requireContext()).show {
            listItemsSingleChoice(
                items = list,
                initialSelection = selectedIndex
            ) { dialog, index, text ->
                if (selectedIndex == index) {
                    return@listItemsSingleChoice
                }
                switchCamera(usbDeviceList[index])
            }
        }
    }

    @SuppressLint("CheckResult")
    private fun showResolutionDialog() {
        mMoreMenu?.dismiss()
        getAllPreviewSizes().let { previewSizes ->
            if (previewSizes.isNullOrEmpty()) {
                ToastUtils.show("Get camera preview size failed")
                return
            }
            val list = arrayListOf<String>()
            var selectedIndex: Int = -1
            for (index in (0 until previewSizes.size)) {
                val w = previewSizes[index].width
                val h = previewSizes[index].height
                getCurrentPreviewSize()?.apply {
                    if (width == w && height == h) {
                        selectedIndex = index
                    }
                }
                list.add("$w x $h")
            }
            MaterialDialog(requireContext()).show {
                listItemsSingleChoice(
                    items = list,
                    initialSelection = selectedIndex
                ) { dialog, index, text ->
                    if (selectedIndex == index) {
                        return@listItemsSingleChoice
                    }
                    updateResolution(previewSizes[index].width, previewSizes[index].height)
                }
            }
        }
    }

    private  fun getVersionName(): String? {
        context ?: return null
        val packageManager = requireContext().packageManager
        try {
            val packageInfo = packageManager?.getPackageInfo(requireContext().packageName, 0)
            return packageInfo?.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return null
    }

    private fun playMic() {
        if (isPlayingMic) {
            stopPlayMic()
            return
        }
        startPlayMic(object : IPlayCallBack {
            override fun onBegin() {
                isPlayingMic = true
            }

            override fun onError(error: String) {
                isPlayingMic = false
            }

            override fun onComplete() {
                isPlayingMic = false
            }
        })
    }

    private fun showRecentMedia(isImage: Boolean? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            context ?: return@launch
            if (! isFragmentAttached()) {
                return@launch
            }
            try {
                if (isImage != null) {
                    MediaUtils.findRecentMedia(requireContext(), isImage)
                } else {
                    MediaUtils.findRecentMedia(requireContext())
                }?.also { path ->
                    val size = Utils.dp2px(requireContext(), 38F)
                    ImageLoaders.of(this@DemoFragment)
                        .loadAsBitmap(path, size, size, object : ILoader.OnLoadedResultListener {
                            override fun onLoadedSuccess(bitmap: Bitmap?) {
                                lifecycleScope.launch(Dispatchers.Main) {
                                }
                            }

                            override fun onLoadedFailed(error: Exception?) {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    ToastUtils.show("Capture image error.${error?.localizedMessage}")
                                }
                            }
                        })
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    ToastUtils.show("${e.localizedMessage}")
                }
                Logger.e(TAG, "showRecentMedia failed", e)
            }
        }
    }

    private fun updateCameraModeSwitchUI() {
    }

    private fun clickAnimation(v: View, listener: Animator.AnimatorListener) {
        val scaleXAnim: ObjectAnimator = ObjectAnimator.ofFloat(v, "scaleX", 1.0f, 0.4f, 1.0f)
        val scaleYAnim: ObjectAnimator = ObjectAnimator.ofFloat(v, "scaleY", 1.0f, 0.4f, 1.0f)
        val alphaAnim: ObjectAnimator = ObjectAnimator.ofFloat(v, "alpha", 1.0f, 0.4f, 1.0f)
        val animatorSet = AnimatorSet()
        animatorSet.duration = 150
        animatorSet.addListener(listener)
        animatorSet.playTogether(scaleXAnim, scaleYAnim, alphaAnim)
        animatorSet.start()
    }

    private fun startMediaTimer() {
        val pushTask: TimerTask = object : TimerTask() {
            override fun run() {
                //秒
                mRecSeconds++
                //分
                if (mRecSeconds >= 60) {
                    mRecSeconds = 0
                    mRecMinute++
                }
                //时
                if (mRecMinute >= 60) {
                    mRecMinute = 0
                    mRecHours++
                    if (mRecHours >= 24) {
                        mRecHours = 0
                        mRecMinute = 0
                        mRecSeconds = 0
                    }
                }
                mMainHandler.sendEmptyMessage(WHAT_START_TIMER)
            }
        }
        if (mRecTimer != null) {
            stopMediaTimer()
        }
        mRecTimer = Timer()
        //执行schedule后1s后运行run，之后每隔1s运行run
        mRecTimer?.schedule(pushTask, 1000, 1000)
    }

    private fun stopMediaTimer() {
        if (mRecTimer != null) {
            mRecTimer?.cancel()
            mRecTimer = null
        }
        mRecHours = 0
        mRecMinute = 0
        mRecSeconds = 0
        mMainHandler.sendEmptyMessage(WHAT_STOP_TIMER)
    }

    private fun calculateTime(seconds: Int, minute: Int, hour: Int? = null): String {
        val mBuilder = java.lang.StringBuilder()
        //时
        if (hour != null) {
            if (hour < 10) {
                mBuilder.append("0")
                mBuilder.append(hour)
            } else {
                mBuilder.append(hour)
            }
            mBuilder.append(":")
        }
        // 分
        if (minute < 10) {
            mBuilder.append("0")
            mBuilder.append(minute)
        } else {
            mBuilder.append(minute)
        }
        //秒
        mBuilder.append(":")
        if (seconds < 10) {
            mBuilder.append("0")
            mBuilder.append(seconds)
        } else {
            mBuilder.append(seconds)
        }
        return mBuilder.toString()
    }

    companion object {
        private const val TAG  = "DemoFragment"
        private const val WHAT_START_TIMER = 0x00
        private const val WHAT_STOP_TIMER = 0x01
    }
}
