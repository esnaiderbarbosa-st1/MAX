package com.example.data.api

import com.example.data.model.CardioSession
import com.example.data.model.ChatMessage
import com.example.data.model.Classroom
import com.example.data.model.CustomExerciseEntity
import com.example.data.model.FeedItem
import com.example.data.model.UserProfileData
import com.example.data.model.Workout
import com.example.data.model.WorkoutHistoryEntry

// Retrofit/HTTP representations for endpoints
data class LoginRequest(val email: String, val senha: String)
data class LoginResponse(val token: String, val user: UserProfileData)
data class RegisterRequest(val nome: String, val email: String, val senha: String, val role: String)
data class JoinClassroomRequest(val accessCode: String)
data class LaunchWorkoutRequest(val workoutId: String, val studentId: String)
data class WorkoutFeedbackRequest(val workoutId: String, val rating: Int, val notes: String, val durationSeconds: Long)

interface FitnessApi {
    // 1. POST /login
    suspend fun login(request: LoginRequest): LoginResponse

    // 2. POST /register
    suspend fun register(request: RegisterRequest): UserProfileData

    // 3. GET /profile/:userId
    suspend fun getProfile(userId: String): UserProfileData

    // 4. PATCH /profile/:userId
    suspend fun updateProfile(userId: String, profile: UserProfileData): UserProfileData

    // 5. GET /admin/users
    suspend fun getAdminUsers(): List<UserProfileData>

    // 6. PATCH /admin/users/:id
    suspend fun updateAdminUser(id: String, profile: UserProfileData): UserProfileData

    // 7. DELETE /admin/users/:id
    suspend fun deleteAdminUser(id: String): Boolean

    // 8. GET /admin/global-icon-style
    suspend fun getGlobalIconStyle(): String

    // 9. POST /admin/global-icon-style
    suspend fun setGlobalIconStyle(style: String): String

    // 10. GET /classrooms
    suspend fun getClassrooms(): List<Classroom>

    // 11. POST /classrooms
    suspend fun createClassroom(name: String, professorId: String, professorName: String): Classroom

    // 12. POST /classrooms/join
    suspend fun joinClassroom(request: JoinClassroomRequest, studentId: String): Classroom

    // 13. DELETE /classrooms/:id
    suspend fun deleteClassroom(id: String): Boolean

    // 14. POST /classrooms/:id/students
    suspend fun addStudentToClassroom(classroomId: String, studentId: String): Classroom

    // 15. DELETE /classrooms/:id/students/:studentId
    suspend fun removeStudentFromClassroom(classroomId: String, studentId: String): Classroom

    // 16. POST /workouts/launch
    suspend fun launchWorkout(request: LaunchWorkoutRequest): Boolean

    // 17. GET /workouts/assigned
    suspend fun getAssignedWorkouts(studentId: String): List<Workout>

    // 18. POST /workouts/feedback
    suspend fun sendWorkoutFeedback(feedback: WorkoutFeedbackRequest, studentId: String): Boolean

    // 19. GET /workouts/feedback
    suspend fun getWorkoutsFeedback(professorId: String): List<WorkoutFeedbackRequest>

    // 20. GET /chats/:id/group
    suspend fun getGroupChatMessages(classroomId: String): List<ChatMessage>

    // 21. POST /chats/:id/group
    suspend fun sendGroupChatMessage(classroomId: String, message: ChatMessage): ChatMessage

    // 22. GET /chats/private/:senderId/:recipientId
    suspend fun getPrivateChatMessages(senderId: String, recipientId: String): List<ChatMessage>

    // 23. POST /chats/private
    suspend fun sendPrivateChatMessage(message: ChatMessage): ChatMessage

    // 24. GET /social/users
    suspend fun getSocialUsers(): List<UserProfileData>
}

// Simulates real network endpoints, with high-quality simulated data
// and latency, keeping our offline-first application 100% functional
class FitnessApiMock : FitnessApi {

    override suspend fun login(request: LoginRequest): LoginResponse {
        val user = UserProfileData(nome = "Usuario Real Mock", peso = 82f)
        return LoginResponse("token_12345", user)
    }

    override suspend fun register(request: RegisterRequest): UserProfileData {
        return UserProfileData(nome = request.nome, id = "u_${System.currentTimeMillis()}")
    }

    override suspend fun getProfile(userId: String): UserProfileData {
        return UserProfileData(id = userId)
    }

    override suspend fun updateProfile(userId: String, profile: UserProfileData): UserProfileData {
        return profile
    }

    override suspend fun getAdminUsers(): List<UserProfileData> {
        return listOf(
            UserProfileData("1", "Usuário Teste", nivelExperiencia = "Intermediário"),
            UserProfileData("admin", "Admin Master", nivelExperiencia = "Avançado"),
            UserProfileData("u2", "Rodrigo Silva", nivelExperiencia = "Iniciante")
        )
    }

    override suspend fun updateAdminUser(id: String, profile: UserProfileData): UserProfileData {
        return profile
    }

    override suspend fun deleteAdminUser(id: String): Boolean = true

    override suspend fun getGlobalIconStyle(): String = "A"

    override suspend fun setGlobalIconStyle(style: String): String = style

    override suspend fun getClassrooms(): List<Classroom> {
        return listOf(
            Classroom("c_1", "Turma dos Monstros", "prof_1", "Prof. Ricardo", "[]", "88273612")
        )
    }

    override suspend fun createClassroom(name: String, professorId: String, professorName: String): Classroom {
        return Classroom("c_${System.currentTimeMillis()}", name, professorId, professorName, "[]", "12345678")
    }

    override suspend fun joinClassroom(request: JoinClassroomRequest, studentId: String): Classroom {
        return Classroom("joined_id", "Turma Ingressada", "prof_2", "Prof. Helena", "[\"$studentId\"]", request.accessCode)
    }

    override suspend fun deleteClassroom(id: String): Boolean = true

    override suspend fun addStudentToClassroom(classroomId: String, studentId: String): Classroom {
        return Classroom(classroomId, "Turma Modificada", "p", "p", "[\"$studentId\"]", "1234")
    }

    override suspend fun removeStudentFromClassroom(classroomId: String, studentId: String): Classroom {
        return Classroom(classroomId, "Turma Modificada", "p", "p", "[]", "1234")
    }

    override suspend fun launchWorkout(request: LaunchWorkoutRequest): Boolean = true

    override suspend fun getAssignedWorkouts(studentId: String): List<Workout> = emptyList()

    override suspend fun sendWorkoutFeedback(feedback: WorkoutFeedbackRequest, studentId: String): Boolean = true

    override suspend fun getWorkoutsFeedback(professorId: String): List<WorkoutFeedbackRequest> = emptyList()

    override suspend fun getGroupChatMessages(classroomId: String): List<ChatMessage> {
        return listOf(
            ChatMessage("m1", classroomId, "p1", "Prof. Ricardo", null, "Sejam bem-vindos à Turma dos Monstros!", System.currentTimeMillis() - 100000),
            ChatMessage("m2", classroomId, "p1", "Prof. Ricardo", null, "Hoje o treino é focado em força bruta!", System.currentTimeMillis() - 50000)
        )
    }

    override suspend fun sendGroupChatMessage(classroomId: String, message: ChatMessage): ChatMessage = message

    override suspend fun getPrivateChatMessages(senderId: String, recipientId: String): List<ChatMessage> = emptyList()

    override suspend fun sendPrivateChatMessage(message: ChatMessage): ChatMessage = message

    override suspend fun getSocialUsers(): List<UserProfileData> = getAdminUsers()
}
