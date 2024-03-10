package ru.hse.restaurant.controllers

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import ru.hse.restaurant.models.Dish
import ru.hse.restaurant.models.Ordering
import ru.hse.restaurant.models.User
import ru.hse.restaurant.repositories.MenuRepository
import java.math.BigDecimal
import java.time.LocalTime


interface AdminController {
    @PostMapping("/createDish")
    fun createDish(
        @RequestParam name: String,
        @RequestParam description: String,
        @RequestParam price: BigDecimal,
        @RequestParam minutesToCook: Int
    ): String

    @DeleteMapping("/deleteDish")
    fun deleteDish(
        @RequestParam name: String,
        @RequestParam description: String,
        @RequestParam price: BigDecimal,
        @RequestParam minutesToCook: Int
    ): String

    @GetMapping("/getRevenue")
    fun getRevenue(): Any


    @GetMapping("/getDishesSortedByPopularity")
    fun getDishesSortedByPopularity(): Any

    @GetMapping("/getAverageAssessment")
    fun getAverageAssessment(): Any

    @GetMapping("/getAllOrdersInPeriod")
    fun getAllOrdersInPeriod(
        @RequestParam start: LocalTime,
        @RequestParam end: LocalTime,
    ): Any

    var currAdmin: User?
}

@RequestMapping("/api/admin")
@RestController
class AdminControllerImpl(
    @Autowired private val menuRepository: MenuRepository,
) : AdminController {
    override var currAdmin: User? = null

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @PostMapping("/createDish")
    override fun createDish(
        @RequestParam name: String,
        @RequestParam description: String,
        @RequestParam price: BigDecimal,
        @RequestParam minutesToCook: Int
    ): String {
        if (currAdmin == null) {
            return "No logged admin"
        }
        val dish = Dish(0, name, description, price, minutesToCook, currAdmin!!.username)
        return try {
            menuRepository.save(dish)
            "The dish was successfully created"
        } catch (e: Exception) {
            "This dish already exists"
        }
    }

    @DeleteMapping("/deleteDish")
    override fun deleteDish(
        @RequestParam name: String,
        @RequestParam description: String,
        @RequestParam price: BigDecimal,
        @RequestParam minutesToCook: Int
    ): String {
        if (currAdmin == null) {
            return "No logged admin"
        }
        val query = entityManager.createNativeQuery(
            "SELECT * FROM menu WHERE name = '$name' AND description = '$description' AND price = '$price' AND minutes_to_cook = '$minutesToCook'",
            Dish::class.java
        )
        if (query.resultList.isEmpty()) {
            return "This dish doesn't exist"
        }
        val dish = query.resultList.first() as Dish
        return try {
            menuRepository.delete(dish)
            "The dish was successfully deleted"
        } catch (e: Exception) {
            "Something went wrong"
        }
    }

    @GetMapping("/getRevenue")
    override fun getRevenue(): Any {
        if (currAdmin == null) {
            return "No logged admin"
        }
        val query = entityManager.createNativeQuery("SELECT SUM(price) FROM orderings")
        return query.singleResult as BigDecimal
    }


    @GetMapping("/getDishesSortedByPopularity")
    override fun getDishesSortedByPopularity(): Any {
        if (currAdmin == null) {
            return "No logged admin"
        }
        var query =
            entityManager.createNativeQuery("SELECT dishid FROM ordering_dishid GROUP BY dishid ORDER BY COUNT(*) DESC")
        val dishIds = query.resultList as List<*>
        val ans: MutableList<String> = mutableListOf()
        for (id in dishIds) {
            query = entityManager.createNativeQuery("SELECT * FROM menu WHERE id = '$id'", Dish::class.java)
            val dish = query.singleResult as Dish
            ans.add(dish.getName())
        }
        return ans
    }

    @GetMapping("/getAverageAssessment")
    override fun getAverageAssessment(): Any {
        if (currAdmin == null) {
            return "No logged admin"
        }
        val query = entityManager.createNativeQuery("SELECT AVG(assessment) FROM feedbacks")
        return query.singleResult as BigDecimal
    }

    @GetMapping("/getAllOrdersInPeriod")
    override fun getAllOrdersInPeriod(
        @RequestParam start: LocalTime,
        @RequestParam end: LocalTime,
    ): Any {
        if (currAdmin == null) {
            return "No logged admin"
        }
        val query = entityManager.createNativeQuery(
            "SELECT * FROM orderings WHERE (time_started BETWEEN '$start' AND '$end') AND (time_ended BETWEEN '$start' AND '$end')",
            Ordering::class.java
        )
        return query.resultList as List<*>
    }

}