package com.ghostblocker

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class TouchBlockerService : Service() {

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    // 격자 설정
    private val COLS = 10
    private val ROWS = 6

    data class Cell(
        val view: View,
        val params: WindowManager.LayoutParams,
        val col: Int,
        val row: Int,
        var isHoleOpen: Boolean = false
    )

    private val cells = mutableListOf<Cell>()
    private var cellWidth = 0
    private var cellHeight = 0
    private var gridTop = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(1, buildNotification())

        val blockPercent = intent?.getIntExtra("block_percent", 39) ?: 39
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        // 상태표시줄 포함 — y=0 부터 시작
        gridTop = 0
        val totalBlockHeight = (screenHeight * blockPercent / 100.0).toInt()

        cellWidth = screenWidth / COLS
        cellHeight = totalBlockHeight / ROWS

        for (row in 0 until ROWS) {
            for (col in 0 until COLS) {
                val x = col * cellWidth
                val y = gridTop + row * cellHeight

                val params = WindowManager.LayoutParams(
                    cellWidth,
                    cellHeight,
                    x,
                    y,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                )
                params.gravity = Gravity.TOP or Gravity.START

                val cell = Cell(createCellView(), params, col, row)
                cells.add(cell)
                windowManager.addView(cell.view, cell.params)
            }
        }

        return START_STICKY
    }

    private fun createCellView(): View {
        return object : View(this) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                val toolType = ev.getToolType(0)
                return if (toolType == MotionEvent.TOOL_TYPE_STYLUS ||
                           toolType == MotionEvent.TOOL_TYPE_ERASER) {
                    false // S펜 → 통과
                } else {
                    true  // 손가락/고스트터치 → 차단
                }
            }

            override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
                val toolType = ev.getToolType(0)
                if (toolType == MotionEvent.TOOL_TYPE_STYLUS ||
                    toolType == MotionEvent.TOOL_TYPE_ERASER) {

                    // 뷰 내부 상대 좌표 → 화면 절대 좌표로 변환
                    val location = IntArray(2)
                    getLocationOnScreen(location)
                    val absX = location[0] + ev.x
                    val absY = location[1] + ev.y

                    when (ev.actionMasked) {
                        MotionEvent.ACTION_HOVER_ENTER,
                        MotionEvent.ACTION_HOVER_MOVE -> openHoleAt(absX, absY)
                        MotionEvent.ACTION_HOVER_EXIT -> scheduleCloseAllHoles()
                    }
                }
                return false
            }
        }.apply { setBackgroundColor(Color.TRANSPARENT) }
    }

    // S펜 위치 주변 셀만 구멍 뚫기
    private fun openHoleAt(absX: Float, absY: Float) {
        val hoverCol = (absX / cellWidth).toInt().coerceIn(0, COLS - 1)
        val hoverRow = ((absY - gridTop) / cellHeight).toInt().coerceIn(0, ROWS - 1)

        cells.forEach { cell ->
            val shouldBeOpen = Math.abs(cell.col - hoverCol) <= 1 &&
                               Math.abs(cell.row - hoverRow) <= 1

            if (shouldBeOpen && !cell.isHoleOpen) {
                cell.isHoleOpen = true
                cell.params.flags = cell.params.flags or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                try { windowManager.updateViewLayout(cell.view, cell.params) }
                catch (e: Exception) {}

            } else if (!shouldBeOpen && cell.isHoleOpen) {
                closeHole(cell)
            }
        }
    }

    private fun closeHole(cell: Cell) {
        cell.isHoleOpen = false
        cell.params.flags = cell.params.flags and
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        try { windowManager.updateViewLayout(cell.view, cell.params) }
        catch (e: Exception) {}
    }

    private fun scheduleCloseAllHoles() {
        handler.postDelayed({
            cells.filter { it.isHoleOpen }.forEach { closeHole(it) }
        }, 300)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, "blocker_channel")
            .setContentTitle("고스트터치 차단 중")
            .setContentText("S펜은 정상 작동합니다. 탭하여 설정 열기")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        cells.forEach {
            try { windowManager.removeView(it.view) } catch (e: Exception) {}
        }
        cells.clear()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
