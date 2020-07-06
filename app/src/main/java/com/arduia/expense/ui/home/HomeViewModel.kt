package com.arduia.expense.ui.home

import android.app.Application
import androidx.lifecycle.*
import com.arduia.expense.data.AccRepository
import com.arduia.expense.data.local.ExpenseEnt
import com.arduia.expense.di.ServiceLoader
import com.arduia.expense.ui.common.BaseLiveData
import com.arduia.expense.ui.common.EventLiveData
import com.arduia.expense.ui.common.event
import com.arduia.expense.ui.common.post
import com.arduia.expense.ui.vto.ExpenseDetailsVto
import com.arduia.expense.ui.vto.ExpensePointVto
import com.arduia.expense.ui.vto.ExpenseVto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.util.*

class HomeViewModel(private val app:Application) : AndroidViewModel(app), LifecycleObserver{

    private val _recentData =  BaseLiveData<List<ExpenseVto>>()
    val recentData get() = _recentData.asLiveData()

    private val _detailData = EventLiveData<ExpenseDetailsVto>()
    val detailData get() =  _detailData.asLiveData()

    private val _weeklyCost = BaseLiveData<String>()
    val weeklyCost get() = _weeklyCost.asLiveData()

    private val _graphPoints = BaseLiveData<ExpensePointVto>()
    val graphPoints get() = _graphPoints.asLiveData()

    private val currencyFormatter = DecimalFormat("###,###.0")

    private val serviceLoader by lazy {
        ServiceLoader.getInstance(app)
    }

    private val expenseMapper by lazy {
        serviceLoader.getExpenseMapper()
    }

    private val accRepository: AccRepository by lazy {
        serviceLoader.getAccountingRepository()
    }

    private val accMapper by lazy {
        serviceLoader.getExpenseMapper()
    }

    fun selectItemForDetail(selectedItem: ExpenseVto){
        viewModelScope.launch(Dispatchers.IO){
            val item = accRepository.getExpense(selectedItem.id).first()
            val detailData = accMapper.mapToExpenseDetailVto(item)
            _detailData post event(detailData)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    private fun onCreate(){
        viewModelScope.launch(Dispatchers.IO){
            accRepository.getRecentExpense().collect {
                val value = it.map { trans ->  this@HomeViewModel.expenseMapper.mapToExpenseVto(trans) }
                _recentData.postValue(value)
            }
        }

        viewModelScope.launch (Dispatchers.IO) {
            accRepository.getWeeklyCostRates().collect {

                val totalLastWeekCost = it.map { data -> data.value }.sum()
                _weeklyCost post currencyFormatter.format(totalLastWeekCost)

                val rawRates = genRates(it)
                _graphPoints post expenseMapper.mapToGraphPointVto(rawRates)
            }
        }
    }


    private fun genRates(list: List<ExpenseEnt>): Map<Int,Int>{

        //To Check Day Count
        val calendar = Calendar.getInstance()
        val actualDayCosts = hashMapOf<Int, Long>()

        list.forEach { exp ->

            calendar.timeInMillis = exp.created_date
            val day = calendar[Calendar.DAY_OF_WEEK]

            val costOfDay = actualDayCosts[day]

            //If First Entry,
            actualDayCosts[day] = if(costOfDay == null)
                exp.value
            else
                costOfDay + exp.value

        }

        //Max Cost in Week
        val maxValue = actualDayCosts.maxBy { result -> result.value }?.value

        val dayRates = hashMapOf<Int, Int>()

        actualDayCosts.forEach { result ->
            dayRates[result.key] = ((result.value/ maxValue!!) * 100).toInt()
        }
        return dayRates
    }
}