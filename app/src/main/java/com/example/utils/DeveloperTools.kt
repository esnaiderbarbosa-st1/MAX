package com.example.utils

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.constants.TemplatesAndExercises
import java.util.UUID

object DeveloperTools {

    // 1. Reset Environment
    suspend fun resetEnvironment(db: AppDatabase, context: Context) {
        db.clearAllTables()
        
        // Save first launch pref to false to enter directly to home
        val sharedPrefs = context.getSharedPreferences("max_performance_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("first_launch", false).apply()

        // Create Default Developer Profile
        val defaultProfile = UserProfileData.PRESET_ESNAIDER
        db.userDao().insertProfile(defaultProfile)

        // Seed default classroom and feed as done in initDefaultTestUser
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
        db.chatDao().insertMessage(
            ChatMessage("m1", "class_mestre", "prof_ricardo", "Prof. Ricardo", null, "Olá pessoal! Sejam bem-vindos à nossa turma de alta performance.", System.currentTimeMillis() - 1000000)
        )
        db.chatDao().insertMessage(
            ChatMessage("m2", "class_mestre", "1", "Usuário Teste", null, "Fala professor! Pronto para o treino de hoje.", System.currentTimeMillis() - 500000)
        )

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

        // Seed Exercises
        seedExercises(db)
    }

    // 2. Seed Exercises database table
    suspend fun seedExercises(db: AppDatabase) {
        val exercises = TemplatesAndExercises.ALL_EXERCISES
        for (ex in exercises) {
            db.customExerciseDao().insertCustomExercise(
                CustomExerciseEntity(
                    id = ex.id,
                    name = ex.name,
                    category = ex.category,
                    description = ex.description,
                    difficulty = ex.difficulty
                )
            )
        }
    }

    // 3. Generate History
    suspend fun generateHistory(db: AppDatabase, months: Int, totalSessions: Int = 0) {
        val now = System.currentTimeMillis()
        val totalDays = months * 30
        val count = if (totalSessions > 0) totalSessions else (totalDays * 0.75).toInt() // Spaced ~5 days out of 7

        val templates = TemplatesAndExercises.PRE_MADE_TEMPLATES
        if (templates.isEmpty()) return

        for (i in 1..count) {
            val daysAgo = (totalDays * (i.toFloat() / count.toFloat())).toLong()
            val sessionDate = now - (daysAgo * 24 * 60 * 60 * 1000L) - ((0..12).random() * 60 * 60 * 1000L)
            
            // Alternate between template_push, template_pull, template_legs
            val templateIndex = i % templates.size
            val template = templates[templateIndex]

            // Deserialize exercises
            val exercises = JsonUtils.parseWorkoutExercises(template.exercisesJson)
            // Ensure sets are marked as completed for history
            val completedExercises = exercises.map { ex ->
                val updatedSets = ex.sets.mapIndexed { idx, set ->
                    // Gradually increase weights as sessions progress in time (i increases)
                    val progressionWeightBonus = (i.toFloat() / count.toFloat()) * 15f
                    val baseWeight = set.weight
                    val weight = if (baseWeight > 0) baseWeight + (progressionWeightBonus.toInt() / 2 * 2) else 0f
                    set.copy(completed = true, weight = weight)
                }
                ex.copy(sets = updatedSets)
            }

            val durationSeconds = (30..75).random().toLong() * 60
            val totalWeight = completedExercises.sumOf { ex ->
                ex.sets.filter { it.completed }.sumOf { it.weight.toDouble() }
            }.toFloat()

            db.historyDao().insertHistoryEntry(
                WorkoutHistoryEntry(
                    id = "hist_dev_${UUID.randomUUID()}",
                    workoutId = template.id,
                    workoutName = template.name,
                    date = sessionDate,
                    durationSeconds = durationSeconds,
                    totalWeight = totalWeight,
                    exercisesCompletedCount = completedExercises.size,
                    exercisesJson = JsonUtils.serializeWorkoutExercises(completedExercises)
                )
            )
        }
    }

    // 4. Insert Fake Bioimpedance Evolution
    suspend fun insertFakeBioimpedance(db: AppDatabase) {
        val profile = db.userDao().getUserProfileSync("1") ?: UserProfileData()
        val now = System.currentTimeMillis()

        // Generate 8 records spaced 15 days apart in the past
        val records = mutableListOf<BioimpedanceRecord>()
        for (i in 0..7) {
            val daysAgo = (7 - i) * 15L
            val date = now - (daysAgo * 24 * 60 * 60 * 1000L)
            
            // Progressive weight loss / muscle gain
            val peso = 85.5f - (i * 0.5f) + ((0..4).random() * 0.1f) // 85.5kg down to ~82kg
            val imcVal = peso / (1.78f * 1.78f)
            val percentualGordura = 18f - (i * 0.5f) + ((0..4).random() * 0.1f) // 18% down to ~14.5%
            val taxaMuscular = 47f + (i * 0.4f) // 47% up to ~50%
            val massaMagra = peso * (1f - (percentualGordura / 100f))
            
            records.add(
                BioimpedanceRecord(
                    id = "bio_rec_${UUID.randomUUID()}",
                    date = date,
                    peso = peso,
                    imc = imcVal,
                    percentualGordura = percentualGordura,
                    taxaMuscular = taxaMuscular,
                    massaMagra = massaMagra,
                    gorduraSubcutanea = 17f - (i * 0.3f),
                    gorduraVisceral = 8f - (i * 0.4f),
                    aguaCorporal = 53f + (i * 0.4f),
                    massaMuscularEsqueletica = 32f + (i * 0.4f),
                    massaMuscular = massaMagra * 0.88f,
                    massaOssea = 3f + (i * 0.02f),
                    proteina = 18f,
                    tmb = 1600,
                    idadeMetabolica = 26
                )
            )
        }

        // Adapter helper to serialize
        val adapter = JsonUtils.moshi.adapter(UserProfileData::class.java)
        
        // Latest record defines the current profile metrics
        val latest = records.last()
        val updatedProfile = profile.copy(
            peso = latest.peso,
            imc = latest.imc,
            percentualGordura = latest.percentualGordura,
            taxaMuscular = latest.taxaMuscular,
            massaMagra = latest.massaMagra,
            gorduraSubcutanea = latest.gorduraSubcutanea,
            gorduraVisceral = latest.gorduraVisceral,
            aguaCorporal = latest.aguaCorporal,
            massaMuscularEsqueletica = latest.massaMuscularEsqueletica,
            massaMuscular = latest.massaMuscular,
            massaOssea = latest.massaOssea,
            bioimpedanciaHistoricoJson = JsonUtils.moshi.adapter<List<BioimpedanceRecord>>(
                com.squareup.moshi.Types.newParameterizedType(List::class.java, BioimpedanceRecord::class.java)
            ).toJson(records)
        )

        db.userDao().insertProfile(updatedProfile)
    }

    // 5. Insert Fake Cardio
    suspend fun insertFakeCardio(db: AppDatabase) {
        val now = System.currentTimeMillis()
        val types = listOf("Esteira", "Bicicleta", "Elíptico", "Corda")
        
        for (i in 0..5) {
            val daysAgo = (5 - i) * 4L + (1..3).random()
            val date = now - (daysAgo * 24 * 60 * 60 * 1000L)
            val type = types[i % types.size]
            val durationMinutes = (20..45).random()
            val calories = durationMinutes * (8..12).random()
            val distance = if (type == "Esteira" || type == "Bicicleta") durationMinutes * 0.15f else 0f

            db.cardioDao().insertCardio(
                CardioSession(
                    id = "cardio_dev_${UUID.randomUUID()}",
                    type = type,
                    durationMinutes = durationMinutes,
                    calories = calories,
                    date = date,
                    distance = distance,
                    durationSeconds = durationMinutes * 60,
                    avgHr = (130..165).random(),
                    steps = if (type == "Esteira") durationMinutes * 110 else 0,
                    pace = if (distance > 0) String.format("%.2f km/h", (distance / (durationMinutes / 60f))) else "",
                    archived = false
                )
            )
        }
    }

    // 6. Simulate Low Recovery (Full body trained 1 hour ago)
    suspend fun simulateLowRecovery(db: AppDatabase) {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - 3600000L

        // Include exercises targeting all muscles
        val allMusclesExercises = listOf(
            WorkoutExercise("w_ex_low1", "peito_1", "Supino Reto Barra", "Peito", (1..3).map { WorkoutSet("ws_low1_$it", it, 10, 40f, true) }),
            WorkoutExercise("w_ex_low2", "costas_1", "Puxada Frontal Pulley", "Costas", (1..3).map { WorkoutSet("ws_low2_$it", it, 10, 50f, true) }),
            WorkoutExercise("w_ex_low3", "pernas_1", "Agachamento Livre Barra", "Pernas", (1..3).map { WorkoutSet("ws_low3_$it", it, 10, 60f, true) }),
            WorkoutExercise("w_ex_low4", "ombros_2", "Elevação Lateral", "Ombros", (1..3).map { WorkoutSet("ws_low4_$it", it, 12, 10f, true) }),
            WorkoutExercise("w_ex_low5", "biceps_1", "Rosca Direta", "Bíceps", (1..3).map { WorkoutSet("ws_low5_$it", it, 10, 15f, true) }),
            WorkoutExercise("w_ex_low6", "triceps_1", "Tríceps Pulley", "Tríceps", (1..3).map { WorkoutSet("ws_low6_$it", it, 12, 20f, true) }),
            WorkoutExercise("w_ex_low7", "trapezio_1", "Encolhimento", "Trapézio", (1..3).map { WorkoutSet("ws_low7_$it", it, 12, 24f, true) }),
            WorkoutExercise("w_ex_low8", "abdominais_1", "Abdominal Crunch", "Abdominais", (1..3).map { WorkoutSet("ws_low8_$it", it, 15, 0f, true) })
        )

        db.historyDao().insertHistoryEntry(
            WorkoutHistoryEntry(
                id = "hist_low_rec",
                workoutId = "template_fullbody",
                workoutName = "Full Body Destruidor",
                date = oneHourAgo,
                durationSeconds = 3600,
                totalWeight = 2400f,
                exercisesCompletedCount = allMusclesExercises.size,
                exercisesJson = JsonUtils.serializeWorkoutExercises(allMusclesExercises)
            )
        )
    }

    // 7. Unlock All Trophies
    suspend fun unlockAllTrophies(db: AppDatabase) {
        // Condition 1: At least 5 workouts in history
        val now = System.currentTimeMillis()
        val templates = TemplatesAndExercises.PRE_MADE_TEMPLATES
        val pushEx = templates.find { it.id == "template_push" }?.exercisesJson ?: "[]"
        for (i in 1..5) {
            db.historyDao().insertHistoryEntry(
                WorkoutHistoryEntry(
                    id = "trophy_hist_$i",
                    workoutId = "template_push",
                    workoutName = "Push (Empurrar)",
                    date = now - (i * 24 * 60 * 60 * 1000L),
                    durationSeconds = 2700,
                    totalWeight = 1500f,
                    exercisesCompletedCount = 3,
                    exercisesJson = pushEx
                )
            )
        }

        // Condition 2: Lifted cumulative > 1,000kg (already met by above: 5 * 1500f = 7500kg)

        // Condition 3: At least 3 cardio sessions
        for (i in 1..3) {
            db.cardioDao().insertCardio(
                CardioSession(
                    id = "trophy_cardio_$i",
                    type = "Esteira",
                    durationMinutes = 30,
                    calories = 300,
                    date = now - (i * 24 * 60 * 60 * 1000L),
                    distance = 4.5f,
                    archived = false
                )
            )
        }

        // Condition 4: Created at least 1 custom workout template
        db.workoutDao().insertWorkout(
            Workout(
                id = "custom_template_trophy",
                templateId = null,
                name = "Meu Treino de Peito",
                description = "Criado para quebrar platôs de peito.",
                exercisesJson = "[]",
                createdAt = now
            )
        )

        // Condition 5: Joined at least 1 classroom
        db.classroomDao().insertClassroom(
            Classroom(
                id = "classroom_trophy",
                name = "Sala de Elite",
                professorId = "prof_elite",
                professorName = "Prof. Elite",
                studentIdsJson = "[\"1\"]",
                accessCode = "99887766"
            )
        )
    }

    // 8. Create Workout Routine Templates
    suspend fun createWorkoutRoutine(db: AppDatabase, type: String) {
        val now = System.currentTimeMillis()
        val templates = TemplatesAndExercises.PRE_MADE_TEMPLATES
        
        when (type) {
            "ABCDE" -> {
                val names = listOf("A - Peito & Tríceps", "B - Costas & Bíceps", "C - Pernas Completo", "D - Ombros & Trapézio", "E - Membros Superiores Foco")
                names.forEachIndexed { idx, name ->
                    val template = templates.getOrNull(idx % templates.size) ?: templates.first()
                    db.workoutDao().insertWorkout(
                        Workout(
                            id = "routine_abcde_${idx}_${UUID.randomUUID()}",
                            templateId = null,
                            name = name,
                            description = "Treino $name criado pelo menu desenvolvedor.",
                            exercisesJson = template.exercisesJson,
                            createdAt = now - (idx * 10)
                        )
                    )
                }
            }
            "PushPullLegs" -> {
                val p = templates.find { it.id == "template_push" } ?: templates.first()
                val pu = templates.find { it.id == "template_pull" } ?: templates.first()
                val l = templates.find { it.id == "template_legs" } ?: templates.first()

                db.workoutDao().insertWorkout(Workout("routine_ppl_1_${UUID.randomUUID()}", null, "Push (Empurrar) - Dev", "Foco em Peito, Ombros e Tríceps", p.exercisesJson, now - 10))
                db.workoutDao().insertWorkout(Workout("routine_ppl_2_${UUID.randomUUID()}", null, "Pull (Puxar) - Dev", "Foco em Costas, Bíceps e Trapézio", pu.exercisesJson, now - 20))
                db.workoutDao().insertWorkout(Workout("routine_ppl_3_${UUID.randomUUID()}", null, "Legs (Pernas) - Dev", "Foco em Quadríceps, Isquiotibiais e Panturrilhas", l.exercisesJson, now - 30))
            }
            "FullBody" -> {
                val fb = templates.find { it.id == "template_fullbody" } ?: templates.first()
                db.workoutDao().insertWorkout(Workout("routine_fb_1_${UUID.randomUUID()}", null, "Full Body (Corpo Inteiro) - Dev", "Treino completo focado em grandes grupos musculares.", fb.exercisesJson, now))
            }
            "UpperLower" -> {
                val u = templates.find { it.id == "template_upper" } ?: templates.first()
                val lo = templates.find { it.id == "template_lower" } ?: templates.first()
                db.workoutDao().insertWorkout(Workout("routine_ul_1_${UUID.randomUUID()}", null, "Upper (Membros Superiores) - Dev", "Ombros, Peito, Costas e Braços", u.exercisesJson, now - 10))
                db.workoutDao().insertWorkout(Workout("routine_ul_2_${UUID.randomUUID()}", null, "Lower (Membros Inferiores) - Dev", "Pernas, Glúteos e Abdominais", lo.exercisesJson, now - 20))
            }
        }
    }

    // 9. Clear Workout templates
    suspend fun clearWorkouts(db: AppDatabase) {
        db.workoutDao().clearAllWorkouts()
    }

    // 10. Clear Bioimpedance
    suspend fun clearBioimpedance(db: AppDatabase) {
        val profile = db.userDao().getUserProfileSync("1")
        if (profile != null) {
            val updated = profile.copy(bioimpedanciaHistoricoJson = "[]")
            db.userDao().insertProfile(updated)
        }
    }

    // 11. Clear Recovery
    suspend fun clearRecovery(db: AppDatabase) {
        db.historyDao().clearAllHistory()
    }
}
