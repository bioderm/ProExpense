package com.arduia.expense.ui.tmp

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.arduia.core.extension.px
import com.arduia.expense.R
import com.arduia.expense.databinding.ItemExpenseLogBinding
import com.arduia.expense.ui.vto.ExpenseVto
import kotlin.math.abs

class SwipeFrameLayout @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
    defStyleRes: Int = 0
) : FrameLayout(ctx, attrs, defStyleRes) {

    private val binding by lazy { ItemExpenseLogBinding.bind(this) }

    private var minEndMargin = ctx.px(40f)
    private var minStartMargin = ctx.px(30f)

    private var lockEndMargin = ctx.px(80f)
    private var lockStartMargin = ctx.px(46f)

    private var currentTranslateAnimation: ValueAnimator? = null

    private val swipeDxFactor = 0.4F
    private val translateInterpolator = LinearOutSlowInInterpolator()

    private var lastTranslationPhase = 0f

    private var currentState = STATE_IDLE
    private var currentDirection = DIRECTION_IDLE
    private var isSwipeTouchActive = false
    private var halfWidth = 0F

    private var selectedChangedListener: OnSelectedChangedListener? = null
    private var prepareChangedListener: OnPrepareChangedListener? = null

    private var currentPosition = 0F
    private var desiredDestination = 0F

    private val cdLongClickListener = OnLongClickListener {
        onStartSwipe()
        invertEndLockState()
        return@OnLongClickListener true
    }

    fun bindData(data: ExpenseVto) {
        with(binding) {
            tvAmount.text = data.amount
            tvCurrencySymbol.text = data.currencySymbol
            tvDate.text = data.date
            tvName.text = data.name
            imvCategory.setImageResource(data.category)
        }
        binding.cdExpense.setOnLongClickListener(cdLongClickListener)
    }

    private fun invertEndLockState() {
        if (currentState == STATE_START_LOCKED) return

        if (currentState == STATE_IDLE) {
            translateIdleToEnd()
        } else if (currentState == STATE_END_LOCKED) {
            translateEndToIdle()
        }
    }

    private fun translateIdleToEnd() {
        if (currentState != STATE_IDLE) return
        currentPosition = 0F
        desiredDestination = -lockEndMargin
        translateToDesiredDestination()
    }

    private fun calculateDuration(start: Float, end: Float): Long =
        (abs(abs(start) - abs(end)) * 0.4f).toLong()


    private fun translateEndToIdle() {
        if (currentState != STATE_END_LOCKED) return
        currentPosition =  -lockEndMargin
        desiredDestination = 0F
        translateToDesiredDestination()
    }

    fun onSwipe(isOnTouch: Boolean, dx: Float) {

        if (isSwipeTouchActive && (abs(binding.cdExpense.translationX)  <= halfWidth) ) {
            translateView((dx * swipeDxFactor) + lastTranslationPhase)
        }

        if (isSwipeTouchActive and isOnTouch.not()) {
            onSwipeReleased(dx, binding.cdExpense.translationX)
        }

        isSwipeTouchActive = isOnTouch

    }

    private fun translateView(translationX: Float) {
        binding.cdExpense.translationX = translationX

        when {
            translationX > 10 -> {
                onDirectionChanged(DIRECTION_START_TO_END)
            }
            translationX < -10 -> {
                onDirectionChanged(DIRECTION_END_TO_START)
            }
            else -> onDirectionChanged(DIRECTION_IDLE)
        }
    }

    private fun onDirectionChanged(direction: Int) {
        if (this.currentDirection == direction) return
        this.currentDirection = direction
        when (currentDirection) {
            DIRECTION_START_TO_END -> {
                binding.flBack.setBackgroundResource(R.color.green_400)
            }
            DIRECTION_END_TO_START -> {
                binding.flBack.setBackgroundResource(R.color.red_400)
            }
        }
    }


    private fun onSwipeReleased(dx: Float, translationX: Float) {
        currentPosition = translationX
        desiredDestination = when {
            (translationX >= minStartMargin) && (currentState == STATE_IDLE) -> {
                lockStartMargin
            }
            (translationX <= -minEndMargin) && (currentState == STATE_IDLE) -> {
                -lockEndMargin
            }
            else ->0F
        }
        translateToDesiredDestination()
    }

    private fun translateToDesiredDestination() {

        binding.cdExpense.isClickable = false
        currentTranslateAnimation?.cancel()

        val duration = calculateDuration(currentPosition, desiredDestination)
        currentTranslateAnimation =
            ValueAnimator.ofFloat(currentPosition, desiredDestination).apply {
                this.duration = duration
                interpolator = translateInterpolator
                addUpdateListener {
                    val value = it.animatedValue as Float
                    translateView(value)
                    if (value == desiredDestination) {
                        onTranslationFinished(lastPosition = value)
                        binding.cdExpense.isClickable = true
                    }
                }
            }
        currentTranslateAnimation?.start()
    }

    private fun onTranslationFinished(lastPosition: Float) {
        val oldState = currentState
        currentState = when (lastPosition) {
            -lockEndMargin -> {
                binding.cdExpense.setOnLongClickListener(cdLongClickListener)
                prepareChangedListener?.onPreparedChanged(true)
                STATE_END_LOCKED
            }
            lockStartMargin -> {
                binding.cdExpense.setOnLongClickListener{true}
                selectedChangedListener?.onSelectedChanged(true)
                STATE_START_LOCKED
            }
            else -> {
                binding.cdExpense.setOnLongClickListener(cdLongClickListener)
                STATE_IDLE
            }
        }

        if(currentState == STATE_IDLE){
            when(oldState){
                STATE_START_LOCKED -> {
                    //STATE START LOCK -> IDLE
                    selectedChangedListener?.onSelectedChanged(false)
                }
                STATE_END_LOCKED -> {
                    //STATE END LOCK -> IDLE
                    prepareChangedListener?.onPreparedChanged(false)
                }
            }
        }
    }

    fun onStartSwipe() {
        lastTranslationPhase = binding.cdExpense.translationX
        currentState = when (lastTranslationPhase) {
            lockStartMargin -> STATE_START_LOCKED
            -lockEndMargin -> STATE_END_LOCKED
            else -> STATE_IDLE
        }
        halfWidth = width / 2f
        isSwipeTouchActive = true

    }

    fun setOnSelectedChangedListener(changedListener: OnSelectedChangedListener) {
        this.selectedChangedListener = changedListener
    }

    fun setOnPrepareChangedListener(listener: OnPrepareChangedListener) {
        this.prepareChangedListener = listener
    }

    fun interface OnSelectedChangedListener {
        fun onSelectedChanged(isSelected: Boolean)
    }

    fun interface OnPrepareChangedListener {
        fun onPreparedChanged(isPrepared: Boolean)
    }

    companion object {
        private const val STATE_IDLE = 0
        private const val STATE_START_LOCKED = 1
        private const val STATE_END_LOCKED = 2

        private const val DIRECTION_IDLE = 0
        private const val DIRECTION_START_TO_END = 1
        private const val DIRECTION_END_TO_START = 2
    }
}