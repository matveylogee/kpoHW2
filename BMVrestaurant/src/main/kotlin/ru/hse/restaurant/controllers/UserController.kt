package ru.hse.restaurant.controllers

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import ru.hse.restaurant.models.Dish
import ru.hse.restaurant.models.User
import ru.hse.restaurant.repositories.UserRepository
import java.util.*


interface UserController {
    @PostMapping("/signUp")
    fun createUser(
        @RequestParam username: String, @RequestParam password: String, @RequestParam type: String
    ): String

    @GetMapping("/signIn")
    fun logIn(
        @RequestParam username: String, @RequestParam password: String, @RequestParam type: String
    ): String

    @DeleteMapping("/logOut")
    fun logOut(
        @RequestParam username: String, @RequestParam password: String, @RequestParam type: String
    ): String

    @GetMapping("/getDishes")
    fun getDishes(): Any

    @GetMapping("/getCurrentUser")
    fun getCurrentUser(): Any

}

@RequestMapping("/api/user")
@RestController
class UserControllerImpl(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val adminController: AdminControllerImpl,
    @Autowired private val visitorController: VisitorControllerImpl
) : UserController {

    @PersistenceContext
    private lateinit var entityManager: EntityManager
    private var currUser: User? = null

    @PostMapping("/signUp")
    override fun createUser(
        @RequestParam username: String, @RequestParam password: String, @RequestParam type: String
    ): String {
        if (type != "admin" && type != "visitor") {
            return "Wrong user type (only available are admin or visitor)"
        }
        val query = entityManager.createNativeQuery(
            "SELECT * FROM users WHERE username = '$username' AND type = '$type'",
            User::class.java
        )
        if (query.resultList.isNotEmpty()) {
            return "User with this username and type already exists"
        }
        val tmp = User(UUID.randomUUID(), username, password, type)
        try {
            userRepository.save(tmp)
        } catch (e: Exception) {
            return "Something went wrong"
        }
        println("User $username signed up")
        return "You have signed up"
    }

    @GetMapping("/signIn")
    override fun logIn(
        @RequestParam username: String, @RequestParam password: String, @RequestParam type: String
    ): String {
        if (type != "admin" && type != "visitor") {
            return "Wrong user type (only available are admin or visitor)"
        }
        if (currUser != null && currUser!!.username == username) {
            return "You are already in system"
        }
        val query = entityManager.createNativeQuery(
            "SELECT * FROM users WHERE username = '$username' AND password = '$password' AND type = '$type'",
            User::class.java
        )
        val resultList = query.resultList
        if (resultList.isEmpty()) {
            return "User is not found"
        }
        currUser = resultList.first() as User
        if (currUser!!.type == "visitor") {
            visitorController.currVisitor = currUser
            adminController.currAdmin = null
        } else {
            adminController.currAdmin = currUser
            visitorController.currVisitor = null
        }
        println("User $username signed in")
        return "You signed in"
    }

    @DeleteMapping("/logOut")
    override fun logOut(
        @RequestParam username: String, @RequestParam password: String, @RequestParam type: String
    ): String {
        if (type != "admin" && type != "visitor") {
            return "Wrong user type (only available are admin or visitor)"
        }
        val query = entityManager.createNativeQuery(
            "SELECT * FROM users WHERE username = '$username' AND password = '$password' AND type = '$type'",
            User::class.java
        )
        val resultList = query.resultList
        if (resultList.isEmpty()) {
            return "User is not found"
        }
        if (currUser!!.username != username) {
            return "It is not the current user"
        }
        currUser = resultList.first() as User
        if (currUser!!.type == "visitor") {
            visitorController.currVisitor = null
        } else {
            adminController.currAdmin = null
        }
        currUser = null
        println("User $username has successfully logged out")
        return "You have successfully logged out"
    }

    @ResponseBody
    @GetMapping("/getDishes")
    override fun getDishes(): Any {
        val query = entityManager.createNativeQuery("SELECT * FROM menu", Dish::class.java)
        val resultList = query.resultList
        for (dish in resultList) {
            println(dish)
        }
        return resultList
    }

    @GetMapping("/getCurrentUser")
    override fun getCurrentUser(): Any {
        return if (currUser == null) {
            "There is no logged user"
        } else {
            currUser!!
        }
    }

}