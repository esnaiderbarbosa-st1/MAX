package com.example.data.repository

import com.example.constants.TemplatesAndExercises
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.utils.JsonUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class FitnessRepository(private val db: AppDatabase) {

    private val moshi = JsonUtils.moshi

    // --- REPO GETTERS FLOWS ---

    // 1. User profile - emits the single local user, or default if null
    val userProfileFlow: Flow<UserProfileData> = db.userDao().getUserProfile("1").map { profile ->
        val resolved = profile ?: UserProfileData()
        com.example.utils.WeightCalculator.activeUserProfile = resolved
        resolved
    }

    // 2. Custom + Premade Exercises
    val customExercisesFlow: Flow<List<CustomExerciseEntity>> = db.customExerciseDao().getAllCustomExercises()

    // 3. All Workouts (Templates + User Created Custom Workouts)
    val workoutsFlow: Flow<List<Workout>> = db.workoutDao().getAllWorkouts().map { list ->
        // If DB has custom workouts, combine them.
        // Otherwise, make sure templates are included!
        val templates = TemplatesAndExercises.PRE_MADE_TEMPLATES
        (list + templates).distinctBy { it.id }
    }

    // 4. Workout History Entries
    val historyFlow: Flow<List<WorkoutHistoryEntry>> = db.historyDao().getAllHistory()

    // 5. Cardio Sessions
    val cardioSessionsFlow: Flow<List<CardioSession>> = db.cardioDao().getAllCardioSessions()

    // 5.1 Running Sessions Flow (com desduplicação automática de sessões duplicadas no mesmo dia)
    val runningSessionsFlow: Flow<List<RunningSession>> = db.runningSessionDao().getAllRunningSessions().map { list ->
        list.deduplicatedRunningSessions()
    }

    suspend fun saveRunningSession(session: RunningSession) {
        db.runningSessionDao().insertRunningSession(session)
    }

    suspend fun deleteRunningSession(id: String) {
        db.runningSessionDao().deleteRunningSessionById(id)
    }

    suspend fun updateRunnerProfile(heightCm: Int, weightKg: Float, runningGoal: String, baselinePace: Float? = null) {
        val current = userProfileFlow.first()
        val updated = current.copy(
            heightCm = heightCm,
            weightKg = weightKg,
            runningGoal = runningGoal,
            altura = heightCm,
            peso = weightKg,
            baselinePaceMinPerKm = baselinePace ?: current.baselinePaceMinPerKm
        )
        db.userDao().insertProfile(updated)
    }

    suspend fun updateRunnerBaselinePace(baselinePaceMinPerKm: Float) {
        val current = userProfileFlow.first()
        val updated = current.copy(baselinePaceMinPerKm = baselinePaceMinPerKm)
        db.userDao().insertProfile(updated)
    }

    // 6. Classrooms
    val classroomsFlow: Flow<List<Classroom>> = db.classroomDao().getAllClassrooms()

    // 7. Feed Items
    val feedFlow: Flow<List<FeedItem>> = db.feedDao().getFeedItems()

    // 8. Pending Sync Actions Flow
    val pendingSyncActionsFlow: Flow<List<PendingSyncAction>> = db.syncDao().getPendingActionsFlow()

    // 9. Beta Evaluations Flow
    val betaEvaluationsFlow: Flow<List<BetaEvaluationEntity>> = db.betaLabDao().getAllEvaluations()

    suspend fun saveBetaEvaluation(evaluation: BetaEvaluationEntity) {
        db.betaLabDao().insertEvaluation(evaluation)
    }

    suspend fun deleteBetaEvaluation(id: String) {
        db.betaLabDao().deleteEvaluationById(id)
    }

    // 10. Active Workout Sessions
    private val sessionWriteMutex = Mutex()

    val activeWorkoutSessionFlow: Flow<WorkoutSessionEntity?> = db.workoutSessionDao().getActiveSessionFlow()
    val allActiveWorkoutSessionsFlow: Flow<List<WorkoutSessionEntity>> = db.workoutSessionDao().getAllActiveSessionsFlow()
    val archivedWorkoutSessionsFlow: Flow<List<WorkoutSessionEntity>> = db.workoutSessionDao().getArchivedSessionsFlow()

    suspend fun getActiveWorkoutSession(): WorkoutSessionEntity? = db.workoutSessionDao().getActiveSession()

    suspend fun getAllActiveWorkoutSessions(): List<WorkoutSessionEntity> = db.workoutSessionDao().getAllActiveSessions()

    suspend fun getWorkoutSessionById(id: String): WorkoutSessionEntity? = db.workoutSessionDao().getSessionById(id)

    suspend fun getWorkoutSessionVersion(id: String): Int? = db.workoutSessionDao().getSessionVersion(id)

    suspend fun saveWorkoutSession(session: WorkoutSessionEntity) = sessionWriteMutex.withLock {
        WorkoutSessionValidator.validateForPersist(session)
        db.workoutSessionDao().insertOrUpdate(session)
    }

    suspend fun updateWorkoutSessionExercises(sessionId: String, exercisesJson: String): Boolean = sessionWriteMutex.withLock {
        db.workoutSessionDao().updateExercisesJson(sessionId, exercisesJson) > 0
    }

    suspend fun updateWorkoutSessionExercisesOptimistic(
        sessionId: String,
        exercisesJson: String,
        expectedVersion: Int
    ): Boolean = sessionWriteMutex.withLock {
        db.workoutSessionDao().updateExercisesJsonOptimistic(sessionId, exercisesJson, expectedVersion) > 0
    }

    suspend fun updateWorkoutSessionStatus(
        sessionId: String,
        status: String,
        endTime: Long?,
        durationSeconds: Long
    ): Boolean = sessionWriteMutex.withLock {
        val current = db.workoutSessionDao().getSessionById(sessionId) ?: return false
        val fromStatus = WorkoutSessionStatus.fromValue(current.status)
        val toStatus = WorkoutSessionStatus.fromValue(status)
        if (!com.example.data.model.WorkoutSessionStateMachine.isValidTransition(fromStatus, toStatus)) {
            android.util.Log.w("MAX_PERFORMANCE_DEBUG", "Transição de status rejeitada para $sessionId: $fromStatus -> $toStatus")
            return false
        }
        val validDuration = maxOf(0L, durationSeconds)
        val affected = db.workoutSessionDao().updateStatusAndDurationVersioned(
            sessionId = sessionId,
            newStatus = toStatus.rawValue,
            expectedVersion = current.version + 1,
            completedAt = endTime,
            durationSeconds = validDuration
        )
        affected > 0
    }

    suspend fun updateWorkoutSessionStatusVersioned(
        sessionId: String,
        status: String,
        newVersion: Int,
        completedAt: Long?,
        durationSeconds: Long
    ): Boolean = sessionWriteMutex.withLock {
        val current = db.workoutSessionDao().getSessionById(sessionId) ?: return false
        val fromStatus = WorkoutSessionStatus.fromValue(current.status)
        val toStatus = WorkoutSessionStatus.fromValue(status)
        if (!com.example.data.model.WorkoutSessionStateMachine.isValidTransition(fromStatus, toStatus)) {
            android.util.Log.w("MAX_PERFORMANCE_DEBUG", "Transição de status versionada rejeitada para $sessionId: $fromStatus -> $toStatus")
            return false
        }
        val validDuration = maxOf(0L, durationSeconds)
        val affected = db.workoutSessionDao().updateStatusAndDurationVersioned(
            sessionId = sessionId,
            newStatus = toStatus.rawValue,
            expectedVersion = newVersion,
            completedAt = completedAt,
            durationSeconds = validDuration
        )
        affected > 0
    }

    suspend fun archiveOldActiveSessions(
        sessionsToArchive: List<WorkoutSessionEntity>,
        archiveTimestamp: Long
    ): Int = sessionWriteMutex.withLock {
        db.workoutSessionDao().archiveMultipleSessionsAtomically(sessionsToArchive, archiveTimestamp)
    }

    suspend fun deleteWorkoutSession(sessionId: String) = sessionWriteMutex.withLock {
        db.workoutSessionDao().deleteSession(sessionId)
    }

    suspend fun purgeExpiredSessions(thresholdDays: Long = 30): Int = sessionWriteMutex.withLock {
        val thresholdTime = System.currentTimeMillis() - (thresholdDays * 24L * 60L * 60L * 1000L)
        db.workoutSessionDao().deleteExpiredSessions(thresholdTime)
    }

    // --- SYNCHRONIZATION AND CONFLICT RESOLUTION ---

    // Simulates syncing pending offline actions to the cloud
    suspend fun syncPendingActions(): Boolean {
        val actions = db.syncDao().getPendingActionsList()
        if (actions.isEmpty()) return true

        // Simulate syncing delay
        kotlinx.coroutines.delay(1200)

        // Process each action
        for (action in actions) {
            // Under real circumstances, send to Retrofit/API
            // Here, we successfully processed them and clear from DB
            db.syncDao().deleteActionById(action.id)
        }
        return true
    }

    // --- WRITE OPERATIONS (OFFLINE-FIRST FALLBACK) ---

    suspend fun saveProfile(profile: UserProfileData) {
        com.example.utils.WeightCalculator.activeUserProfile = profile
        db.userDao().insertProfile(profile)
        
        // Push sync
        val payload = moshi.adapter(UserProfileData::class.java).toJson(profile)
        queueSyncAction("UPDATE_PROFILE", payload)
    }

    suspend fun createWorkout(workout: Workout) {
        db.workoutDao().insertWorkout(workout)
        
        // Push sync
        val payload = moshi.adapter(Workout::class.java).toJson(workout)
        queueSyncAction("CREATE_WORKOUT", payload)
    }

    suspend fun deleteWorkout(id: String) {
        db.workoutDao().deleteWorkoutById(id)
        
        // Push sync
        queueSyncAction("DELETE_WORKOUT", id)
    }

    suspend fun addHistoryEntry(entry: WorkoutHistoryEntry) {
        db.historyDao().insertHistoryEntry(entry)
        ExerciseHistoryRepository.clearCache()
        
        // Push sync
        val payload = moshi.adapter(WorkoutHistoryEntry::class.java).toJson(entry)
        queueSyncAction("ADD_HISTORY", payload)
    }

    suspend fun deleteHistoryEntry(id: String) {
        db.historyDao().deleteHistoryById(id)
        ExerciseHistoryRepository.clearCache()
        queueSyncAction("DELETE_HISTORY", id)
    }

    suspend fun clearHistory() {
        db.historyDao().clearAllHistory()
        ExerciseHistoryRepository.clearCache()
        queueSyncAction("CLEAR_HISTORY", "")
    }

    suspend fun addCustomExercise(entity: CustomExerciseEntity) {
        db.customExerciseDao().insertCustomExercise(entity)
        
        // Push sync
        val payload = moshi.adapter(CustomExerciseEntity::class.java).toJson(entity)
        queueSyncAction("ADD_CUSTOM_EXERCISE", payload)
    }

    suspend fun logCardio(session: CardioSession) {
        db.cardioDao().insertCardio(session)
        
        // Push sync
        val payload = moshi.adapter(CardioSession::class.java).toJson(session)
        queueSyncAction("LOG_CARDIO", payload)
    }

    suspend fun deleteCardio(id: String) {
        db.cardioDao().deleteCardioById(id)
    }

    suspend fun joinClassroom(code: String) {
        // Create joined classroom locally
        val newClass = Classroom(
            id = "joined_${UUID.randomUUID()}",
            name = "Classe Código $code",
            professorId = "prof_code",
            professorName = "Professor Codificado",
            studentIdsJson = "[]",
            accessCode = code
        )
        db.classroomDao().insertClassroom(newClass)
        
        // Queue sync
        queueSyncAction("JOIN_CLASSROOM", code)
    }

    suspend fun createClassroom(name: String) {
        val newClass = Classroom(
            id = "c_${UUID.randomUUID()}",
            name = name,
            professorId = "1",
            professorName = "Professor Teste",
            studentIdsJson = "[]",
            accessCode = (10000000..99999999).random().toString()
        )
        db.classroomDao().insertClassroom(newClass)
        queueSyncAction("CREATE_CLASSROOM", name)
    }

    suspend fun deleteClassroom(id: String) {
        db.classroomDao().deleteClassroomById(id)
        queueSyncAction("DELETE_CLASSROOM", id)
    }

    suspend fun sendChatMessage(classroomId: String, text: String, senderName: String) {
        val msg = ChatMessage(
            id = "msg_${UUID.randomUUID()}",
            classroomId = classroomId,
            senderId = "1",
            senderName = senderName,
            recipientId = null,
            text = text,
            createdAt = System.currentTimeMillis()
        )
        db.chatDao().insertMessage(msg)
        
        val payload = moshi.adapter(ChatMessage::class.java).toJson(msg)
        queueSyncAction("SEND_MESSAGE", payload)
    }

    fun getChatMessagesFlow(classroomId: String): Flow<List<ChatMessage>> {
        return db.chatDao().getMessagesForClassroom(classroomId)
    }

    suspend fun addFeedItem(text: String, imageUri: String? = null, authorName: String) {
        val item = FeedItem(
            id = "feed_${UUID.randomUUID()}",
            authorName = authorName,
            text = text,
            imageUri = imageUri,
            likesCount = 0,
            hasUserLiked = false,
            commentsJson = "[]",
            createdAt = System.currentTimeMillis()
        )
        db.feedDao().insertFeedItem(item)
        
        val payload = moshi.adapter(FeedItem::class.java).toJson(item)
        queueSyncAction("ADD_FEED_ITEM", payload)
    }

    suspend fun toggleLikeFeedItem(item: FeedItem) {
        val updated = item.copy(
            hasUserLiked = !item.hasUserLiked,
            likesCount = if (item.hasUserLiked) item.likesCount - 1 else item.likesCount + 1
        )
        db.feedDao().insertFeedItem(updated)
        // Simple local update, skip sync queue for performance
    }

    suspend fun addCommentToFeedItem(item: FeedItem, commentText: String) {
        // Parse comments
        val commentsList = JsonUtils.parseStringList(item.commentsJson).toMutableList()
        commentsList.add(commentText)
        
        val updated = item.copy(commentsJson = JsonUtils.serializeStringList(commentsList))
        db.feedDao().insertFeedItem(updated)
    }

    // --- PRIVATE SYNC QUEUE HELPER ---

    private suspend fun queueSyncAction(type: String, payload: String) {
        val action = PendingSyncAction(
            id = "sync_${UUID.randomUUID()}",
            actionType = type,
            payloadJson = payload,
            timestamp = System.currentTimeMillis()
        )
        db.syncDao().insertAction(action)
    }

    // Initial Database Setup Helper for Testing Mode
    suspend fun initDefaultTestUser() {
        val existing = db.userDao().getUserProfileSync("1")
        if (existing == null) {
            com.example.utils.WeightCalculator.activeUserProfile = UserProfileData.PRESET_ESNAIDER
            db.userDao().insertProfile(UserProfileData.PRESET_ESNAIDER)
        } else {
            com.example.utils.WeightCalculator.activeUserProfile = existing
        }

        // Garante que as novas máquinas e exercícios nativos estejam sincronizados
        runCatching {
            com.example.utils.DeveloperTools.seedExercises(db)
        }

        // Seed oficial do treino de musculação de 30/10/2026 (18:09) - Full Body B
        runCatching {
            val existingHist = db.historyDao().getAllHistory().first()
            val seedHistId = "hist_seed_2026_10_30_full_body_b"
            if (existingHist.none { it.id == seedHistId }) {
                val gymSeed = GymHistoryRepository.createOfficial30Oct2026FieldSession()
                val workoutExs = listOf(
                    WorkoutExercise(
                        id = "ex_seed_3010_1",
                        exerciseId = "costas_puxada_alta_polia",
                        name = "Puxada Alta na Polia",
                        category = "Costas",
                        equipment = "Polia / Torre de Placas",
                        sets = listOf(
                            WorkoutSet("s_3010_1_1", 1, 12, 45.0f, true),
                            WorkoutSet("s_3010_1_2", 2, 12, 52.0f, true),
                            WorkoutSet("s_3010_1_3", 3, 12, 52.0f, true),
                            WorkoutSet("s_3010_1_4", 4, 12, 52.0f, true)
                        )
                    ),
                    WorkoutExercise(
                        id = "ex_seed_3010_2",
                        exerciseId = "peito_total_health_declinado",
                        name = "Supino Declinado Máquina Total Health",
                        category = "Peito",
                        equipment = "Máquina Articulada / Anilhas",
                        sets = listOf(
                            WorkoutSet("s_3010_2_1", 1, 12, 50.0f, true),
                            WorkoutSet("s_3010_2_2", 2, 12, 50.0f, true),
                            WorkoutSet("s_3010_2_3", 3, 12, 50.0f, true)
                        )
                    ),
                    WorkoutExercise(
                        id = "ex_seed_3010_3",
                        exerciseId = "costas_puxada_alta_anilhas",
                        name = "Puxada Alta Máquina Anilhas",
                        category = "Costas",
                        equipment = "Máquina Articulada / Anilhas",
                        sets = listOf(
                            WorkoutSet("s_3010_3_1", 1, 12, 40.0f, true),
                            WorkoutSet("s_3010_3_2", 2, 12, 60.0f, true),
                            WorkoutSet("s_3010_3_3", 3, 12, 60.0f, true)
                        )
                    ),
                    WorkoutExercise(
                        id = "ex_seed_3010_4",
                        exerciseId = "ombros_2",
                        name = "Elevação Lateral com Halteres",
                        category = "Ombros",
                        equipment = "Halteres",
                        sets = listOf(
                            WorkoutSet("s_3010_4_1", 1, 15, 10.0f, true),
                            WorkoutSet("s_3010_4_2", 2, 15, 10.0f, true),
                            WorkoutSet("s_3010_4_3", 3, 15, 10.0f, true)
                        )
                    ),
                    WorkoutExercise(
                        id = "ex_seed_3010_5",
                        exerciseId = "pernas_3",
                        name = "Cadeira Extensora",
                        category = "Pernas",
                        equipment = "Máquina",
                        sets = listOf(
                            WorkoutSet("s_3010_5_1", 1, 12, 75.0f, true),
                            WorkoutSet("s_3010_5_2", 2, 12, 61.0f, true),
                            WorkoutSet("s_3010_5_3", 3, 12, 61.0f, true)
                        )
                    ),
                    WorkoutExercise(
                        id = "ex_seed_3010_6",
                        exerciseId = "pernas_4",
                        name = "Cadeira Flexora",
                        category = "Pernas",
                        equipment = "Máquina",
                        sets = listOf(
                            WorkoutSet("s_3010_6_1", 1, 12, 36.0f, true),
                            WorkoutSet("s_3010_6_2", 2, 12, 36.0f, true),
                            WorkoutSet("s_3010_6_3", 3, 12, 36.0f, true)
                        )
                    ),
                    WorkoutExercise(
                        id = "ex_seed_3010_7",
                        exerciseId = "biceps_5",
                        name = "Rosca Martelo com Halteres",
                        category = "Bíceps",
                        equipment = "Halteres",
                        sets = listOf(
                            WorkoutSet("s_3010_7_1", 1, 12, 14.0f, true),
                            WorkoutSet("s_3010_7_2", 2, 12, 14.0f, true),
                            WorkoutSet("s_3010_7_3", 3, 12, 14.0f, true)
                        )
                    )
                )
                db.historyDao().insertHistoryEntry(
                    WorkoutHistoryEntry(
                        id = seedHistId,
                        workoutId = "workout_from_protocol_protocol_fb_b",
                        workoutName = "Full Body B",
                        date = gymSeed.date,
                        durationSeconds = gymSeed.durationSeconds.toLong(),
                        totalWeight = gymSeed.totalVolumeKg,
                        exercisesCompletedCount = workoutExs.size,
                        exercisesJson = JsonUtils.serializeWorkoutExercises(workoutExs)
                    )
                )
                ExerciseHistoryRepository.clearCache()
            }

            val seedHist31Id = GymHistoryRepository.OFFICIAL_FIELD_SESSION_2026_10_31_ID
            if (existingHist.none { it.id == seedHist31Id }) {
                val gymSeed31 = GymHistoryRepository.createOfficial31Oct2026FieldSession()
                val workoutExs31 = listOf(
                    WorkoutExercise(
                        id = "ex_seed_3110_1",
                        exerciseId = "costas_16",
                        name = "Terra Romeno com Barra (RDL)",
                        category = "Posterior de Coxa",
                        equipment = "Barra / Smith",
                        sets = listOf(
                            WorkoutSet("s_3110_1_1", 1, 10, 50.0f, true),
                            WorkoutSet("s_3110_1_2", 2, 10, 50.0f, true),
                            WorkoutSet("s_3110_1_3", 3, 10, 60.0f, true),
                            WorkoutSet("s_3110_1_4", 4, 10, 60.0f, true)
                        )
                    ),
                    WorkoutExercise(
                        id = "ex_seed_3110_2",
                        exerciseId = "pernas_22",
                        name = "Standing Hip Thrust",
                        category = "Glúteos",
                        equipment = "Máquina Articulada / Anilhas",
                        sets = listOf(
                            WorkoutSet("s_3110_2_1", 1, 12, 50.0f, true),
                            WorkoutSet("s_3110_2_2", 2, 12, 59.1f, true),
                            WorkoutSet("s_3110_2_3", 3, 12, 63.6f, true)
                        )
                    ),
                    WorkoutExercise(
                        id = "ex_seed_3110_3",
                        exerciseId = "pernas_17",
                        name = "Leg Press Horizontal",
                        category = "Quadríceps",
                        equipment = "Máquina",
                        sets = listOf(
                            WorkoutSet("s_3110_3_1", 1, 12, 75.0f, true),
                            WorkoutSet("s_3110_3_2", 2, 12, 75.0f, true),
                            WorkoutSet("s_3110_3_3", 3, 12, 75.0f, true)
                        )
                    ),
                    WorkoutExercise(
                        id = "ex_seed_3110_4",
                        exerciseId = "peito_2",
                        name = "Supino Inclinado com Halteres",
                        category = "Peito",
                        equipment = "Halteres",
                        sets = listOf(
                            WorkoutSet("s_3110_4_1", 1, 12, 22.0f, true),
                            WorkoutSet("s_3110_4_2", 2, 12, 22.0f, true),
                            WorkoutSet("s_3110_4_3", 3, 12, 22.0f, true)
                        )
                    ),
                    WorkoutExercise(
                        id = "ex_seed_3110_5",
                        exerciseId = "costas_2",
                        name = "Remada Curvada com Barra",
                        category = "Costas",
                        equipment = "Barra / Smith",
                        sets = listOf(
                            WorkoutSet("s_3110_5_1", 1, 10, 45.0f, true),
                            WorkoutSet("s_3110_5_2", 2, 10, 45.0f, true),
                            WorkoutSet("s_3110_5_3", 3, 10, 45.0f, true)
                        )
                    ),
                    WorkoutExercise(
                        id = "ex_seed_3110_6",
                        exerciseId = "ombros_3",
                        name = "Desenvolvimento na Máquina",
                        category = "Ombros",
                        equipment = "Máquina",
                        sets = listOf(
                            WorkoutSet("s_3110_6_1", 1, 12, 15.0f, true),
                            WorkoutSet("s_3110_6_2", 2, 12, 20.0f, true),
                            WorkoutSet("s_3110_6_3", 3, 12, 20.0f, true)
                        )
                    ),
                    WorkoutExercise(
                        id = "ex_seed_3110_7",
                        exerciseId = "abdominais_2",
                        name = "Abdominal Infra Solo",
                        category = "Abdominais",
                        equipment = "Peso Corporal",
                        sets = listOf(
                            WorkoutSet("s_3110_7_1", 1, 15, 0.0f, true),
                            WorkoutSet("s_3110_7_2", 2, 15, 0.0f, true),
                            WorkoutSet("s_3110_7_3", 3, 15, 0.0f, true)
                        )
                    )
                )
                db.historyDao().insertHistoryEntry(
                    WorkoutHistoryEntry(
                        id = seedHist31Id,
                        workoutId = "workout_from_protocol_protocol_fb_c",
                        workoutName = "Full Body C - Recalculated Split",
                        date = gymSeed31.date,
                        durationSeconds = gymSeed31.durationSeconds.toLong(),
                        totalWeight = gymSeed31.totalVolumeKg,
                        exercisesCompletedCount = workoutExs31.size,
                        exercisesJson = JsonUtils.serializeWorkoutExercises(workoutExs31)
                    )
                )
                ExerciseHistoryRepository.clearCache()
            }
        }

        // Seed oficial e desduplicação física da sessão de Corrida Livre de 30/10/2026 (1,89 km em 00:12:06 • Pace 6'24"/km • FC 160/176 bpm • Cadência 141 ppm)
        runCatching {
            val existingRuns = db.runningSessionDao().getAllRunningSessions().first()
            val (dedupedRuns, duplicateIdsToRemove) = deduplicateRunningSessions(existingRuns)
            for (dupId in duplicateIdsToRemove) {
                db.runningSessionDao().deleteRunningSessionById(dupId)
            }

            val seedRunId = "run_seed_2026_10_30_free_run"
            val runTs = GymHistoryRepository.buildFieldTimestamp2026Oct30(17, 45)
            val splitsJson = listOf(
                RecordedKmSplit(
                    splitNumber = 1,
                    splitLabel = "KM 1",
                    distanceMeters = 1000,
                    durationSec = 381,
                    paceSecPerKm = 381,
                    avgHr = 158,
                    phaseTag = "RUN"
                ),
                RecordedKmSplit(
                    splitNumber = 2,
                    splitLabel = "0,89 KM FINAL",
                    distanceMeters = 890,
                    durationSec = 345,
                    paceSecPerKm = 387,
                    avgHr = 163,
                    phaseTag = "RUN"
                )
            ).toJsonString()
            val defaultNotes = "Corrida Livre Contínua • 1,89 km em 00:12:06 • Pace 6'24\"/km • FC Média 160 bpm / Pico 176 bpm (Cinta Peitoral BLE) • Cadência 141 ppm"
            val defaultHrTimeline = "138,146,152,156,159,161,163,165,168,172,176,164,158,155"

            val existing30Oct = dedupedRuns.firstOrNull {
                it.id == seedRunId || (it.distanceMeters in 1880..1900 && it.durationSec in 720..732)
            }

            if (existing30Oct == null) {
                val seedRun = RunningSession(
                    id = seedRunId,
                    date = runTs,
                    weekNumber = 0,
                    dayNumber = 1,
                    sessionType = "FREE_RUN",
                    durationSec = 726,
                    distanceMeters = 1890,
                    avgPaceSecPerKm = 384,
                    rpe = 7,
                    notes = defaultNotes,
                    environment = "OUTDOOR",
                    painResponse = null,
                    isGentleMode = false,
                    shoeName = null,
                    avgHr = 160,
                    maxHr = 176,
                    mainRunDurationSec = 726,
                    mainRunDistanceMeters = 1890,
                    mainRunAvgPaceSecPerKm = 384,
                    kmSplitsJson = splitsJson,
                    hrTimeline = defaultHrTimeline
                )
                db.runningSessionDao().insertRunningSession(seedRun)
            } else {
                val consolidated = existing30Oct.copy(
                    weekNumber = 0,
                    sessionType = "FREE_RUN",
                    avgHr = existing30Oct.avgHr?.takeIf { it > 0 } ?: 160,
                    maxHr = existing30Oct.maxHr?.takeIf { it > 0 } ?: 176,
                    kmSplitsJson = existing30Oct.kmSplitsJson?.takeIf { it.isNotBlank() } ?: splitsJson,
                    hrTimeline = existing30Oct.hrTimeline?.takeIf { it.isNotBlank() } ?: defaultHrTimeline,
                    notes = existing30Oct.notes?.takeIf { it.isNotBlank() } ?: defaultNotes
                )
                if (consolidated != existing30Oct) {
                    db.runningSessionDao().insertRunningSession(consolidated)
                }
            }
        }
        
        // Pre-populate 1 initial classroom if empty
        val classes = db.classroomDao().getAllClassrooms().first()
        if (classes.isEmpty()) {
            db.classroomDao().insertClassroom(
                Classroom(
                    id = "class_mestre",
                    name = "Turma Lendários",
                    professorId = "prof_ricardo",
                    professorName = "Prof. Ricardo",
                    studentIdsJson = "[\"1\"]",
                    accessCode = "10293847"
                )
            )
            // Pre-populate some initial messages
            db.chatDao().insertMessage(
                ChatMessage("m1", "class_mestre", "prof_ricardo", "Prof. Ricardo", null, "Olá pessoal! Sejam bem-vindos à nossa turma de alta performance.", System.currentTimeMillis() - 1000000)
            )
            db.chatDao().insertMessage(
                ChatMessage("m2", "class_mestre", "1", "Usuário Teste", null, "Fala professor! Pronto para o treino de hoje.", System.currentTimeMillis() - 500000)
            )
        }

        // Pre-populate feed if empty
        val feedItems = db.feedDao().getFeedItems().first()
        if (feedItems.isEmpty()) {
            db.feedDao().insertFeedItem(
                FeedItem(
                    id = "feed_init1",
                    authorName = "Prof. Ricardo",
                    text = "A constância supera o talento. Treino de peito concluído com sucesso hoje! 🚀 #Foco #MaxPerformance",
                    imageUri = null,
                    likesCount = 4,
                    hasUserLiked = false,
                    commentsJson = "[\"Grande mestre! Inspiração total.\", \"Amanhã sou eu!\"]"
                )
            )
            db.feedDao().insertFeedItem(
                FeedItem(
                    id = "feed_init2",
                    authorName = "Rodrigo Silva",
                    text = "Novo recorde pessoal no Agachamento Livre: 140kg! A planilha de força funciona demais.",
                    imageUri = null,
                    likesCount = 8,
                    hasUserLiked = true,
                    commentsJson = "[\"Monstro demais!\", \"Excelente marca! Pegada pesada.\"]"
                )
            )
        }
    }
}
