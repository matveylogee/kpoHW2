package ru.hse.restaurant.controllers

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import ru.hse.restaurant.models.Dish
import ru.hse.restaurant.models.Feedback
import ru.hse.restaurant.models.Ordering
import ru.hse.restaurant.models.User
import ru.hse.restaurant.repositories.FeedbackRepository
import ru.hse.restaurant.repositories.OrderingRepository
import ru.hse.restaurant.services.KitchenServiceImpl
import java.math.BigDecimal
import java.time.LocalTime

interface VisitorController {
    @PostMapping("/createOrder")
    suspend fun createOrder(dishId: MutableList<Int>): String
    @PostMapping("/payOrder")
    fun payOrder(
        @RequestParam orderId: Int,
        @RequestParam sum: BigDecimal
    ): String

    @DeleteMapping("/cancelOrder")
    fun cancelOrder(): String

    @PutMapping("/expandOrder")
    suspend fun expandOrder(
        @RequestParam dishId: MutableList<Int>,
    ): String

    @PostMapping("/giveFeedback")
    fun giveFeedback(
        @RequestParam orderId: Int,
        @RequestParam assessment: Int,
        @RequestParam text: String
    ): String
    var currVisitor: User?
}

@RequestMapping("/api/visitor")
@RestController
class VisitorControllerImpl(
    @Autowired private val orderingRepository: OrderingRepository,
    @Autowired private val feedbackRepository: FeedbackRepository,
    @Autowired private val kitchen: KitchenServiceImpl,
) : VisitorController {
    override var currVisitor: User? = null

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @PostMapping("/createOrder")
    override suspend fun createOrder(
        @RequestParam dishId: MutableList<Int>
    ): String {
        if (currVisitor == null) {
            return "No logged visitor"
        }
        val timeStart = LocalTime.now()
        val dishes: MutableList<Dish>
        try {
            dishes = getAllDishesByIds(dishId)
        } catch (e: Exception) {
            return e.message!!
        }

        return if (kitchen.serveOrder(currVisitor!!.username, dishes)) {
            "Order was cancelled"
        } else {
            val timeEnd = LocalTime.now()
            val price = kitchen.cookedDishes.sumOf { dish -> dish.getPrice() }
            val order = Ordering(0, dishId, timeStart, timeEnd, price, currVisitor!!.username)
            withContext(Dispatchers.IO) {
                orderingRepository.save(order)
            }
            kitchen.cookedDishes.clear()
            "Order was saved to the database"
        }
    }

    @PostMapping("/payOrder")
    override fun payOrder(
        @RequestParam orderId: Int,
        @RequestParam sum: BigDecimal
    ): String {
        if (currVisitor == null) {
            return "No logged visitor"
        }
        val query = entityManager.createNativeQuery(
            "SELECT * FROM orderings WHERE id = '$orderId'",
            Ordering::class.java
        )
        val resultList = query.resultList
        if (resultList.isEmpty()) {
            return "Order with this id doesn't exist"
        }
        val order = resultList.first() as Ordering
        if (sum < order.getPrice()) {
            return "Not enough money to pay the order"
        }
        println("Tips are ${sum - order.getPrice()}. Do you confirm? (Y/N)")
        var command: String = readln()
        while (command.uppercase() != "Y" && command.uppercase() != "N") {
            println("Wrong letter, come again")
            command = readln()
        }

        var newSum: BigDecimal = sum
        while (command.uppercase() == "N") {
            println("Write another sum")
            newSum = readln().toBigDecimal()
            while (newSum < order.getPrice()) {
                println("You have to pay at least the sum of the order, so try again")
                newSum = readln().toBigDecimal()
            }

            println("Tips are ${newSum - order.getPrice()}. Do you confirm? (Y/N)")
            command = readln()
            while (command.uppercase() != "Y" && command.uppercase() != "N") {
                println("Wrong letter, come again")
                command = readln()
            }
        }
        println("Revenue is $newSum")
        if (newSum - order.getPrice() == BigDecimal(777.00)) {
            println("Tips for waiters are three axes")
        } else {
            println("Tips for waiters are ${newSum - order.getPrice()}")
        }
        return "Dish was successfully payed"
    }

    @DeleteMapping("/cancelOrder")
    override fun cancelOrder(): String {
        if (currVisitor == null) {
            return "No logged visitor"
        }
        kitchen.cancelOrder(currVisitor!!.username)
        return "Order was successfully cancelled"
    }

    @PutMapping("/expandOrder")
    override suspend fun expandOrder(
        @RequestParam dishId: MutableList<Int>,
    ): String {
        if (currVisitor == null) {
            return "No logged visitor"
        }
        val dishes: MutableList<Dish>
        try {
            dishes = getAllDishesByIds(dishId)
        } catch (e: Exception) {
            return e.message!!
        }
        kitchen.expandOrder(currVisitor!!.username, dishes)
        return "Order was successfully expanded"
    }

    @PostMapping("/giveFeedback")
    override fun giveFeedback(
        @RequestParam orderId: Int,
        @RequestParam assessment: Int,
        @RequestParam text: String
    ): String {
        if (currVisitor == null) {
            return "No logged visitor"
        }
        val query =
            entityManager.createNativeQuery("SELECT * FROM orderings WHERE id = '$orderId'", Ordering::class.java)
        val resultList = query.resultList
        if (resultList.isEmpty()) {
            return "There is no order with this id"
        }
        if ((resultList.first() as Ordering).getCustomer() != currVisitor!!.username) {
            return "It is not your order"
        }
        if (assessment !in 1..5) {
            return "Assessment must be only between 1 and 5"
        }
        val feedback = Feedback(0, orderId, assessment, text)
        feedbackRepository.save(feedback)
        return "Feedback was saved"
    }

    private fun getAllDishesByIds(dishId: MutableList<Int>): MutableList<Dish> {
        val dishes: MutableList<Dish> = mutableListOf()
        for (id in dishId) {
            val query = entityManager.createNativeQuery(
                "SELECT * FROM menu WHERE id = '$id'",
                Dish::class.java
            )
            val resultList = query.resultList
            if (resultList.isEmpty()) {
                throw Exception("Dish with id $id doesn't exist")
            }
            val dish = resultList.first() as Dish
            dishes.add(dish)
        }
        return dishes
    }
}