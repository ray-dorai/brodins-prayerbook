package com.brodinsprayerbook.data

/**
 * Seeds the full default exercise library, organized by muscle group.
 * Modeled after the standard exercise databases in apps like FitNotes.
 * Users can always add their own.
 */
object ExerciseLibrary {

    fun seed(dao: PrayerBookDao) {
        // ===== ABS =====
        ex(dao, "Ab Wheel Rollout", "Abs", "bodyweight")
        ex(dao, "Cable Crunch", "Abs", "cable")
        ex(dao, "Crunch", "Abs", "bodyweight")
        ex(dao, "Decline Crunch", "Abs", "bodyweight")
        ex(dao, "Dragon Flag", "Abs", "bodyweight")
        ex(dao, "Hanging Knee Raise", "Abs", "bodyweight")
        ex(dao, "Hanging Leg Raise", "Abs", "bodyweight")
        ex(dao, "Leg Raise", "Abs", "bodyweight")
        ex(dao, "Mountain Climber", "Abs", "bodyweight")
        ex(dao, "Plank", "Abs", "bodyweight")
        ex(dao, "Russian Twist", "Abs", "bodyweight")
        ex(dao, "Sit-Up", "Abs", "bodyweight")
        ex(dao, "Toes to Bar", "Abs", "bodyweight")
        ex(dao, "Weighted Crunch", "Abs", "barbell")
        ex(dao, "Woodchop", "Abs", "cable")

        // ===== BACK =====
        ex(dao, "Barbell Row", "Back", "barbell", isMain = true)
        ex(dao, "Bent Over Dumbbell Row", "Back", "dumbbell")
        ex(dao, "Cable Row", "Back", "cable")
        ex(dao, "Chin-Up", "Back", "bodyweight")
        ex(dao, "Deadlift", "Back", "barbell", isMain = true)
        ex(dao, "Deficit Deadlift", "Back", "barbell")
        ex(dao, "Dumbbell Row", "Back", "dumbbell")
        ex(dao, "Good Morning", "Back", "barbell")
        ex(dao, "Hyperextension", "Back", "bodyweight")
        ex(dao, "Inverted Row", "Back", "bodyweight")
        ex(dao, "Kroc Row", "Back", "dumbbell")
        ex(dao, "Lat Pulldown", "Back", "cable")
        ex(dao, "Meadows Row", "Back", "barbell")
        ex(dao, "Pendlay Row", "Back", "barbell")
        ex(dao, "Pull-Up", "Back", "bodyweight")
        ex(dao, "Rack Pull", "Back", "barbell")
        ex(dao, "Romanian Deadlift", "Back", "barbell")
        ex(dao, "Seated Cable Row", "Back", "cable")
        ex(dao, "Single Arm Dumbbell Row", "Back", "dumbbell")
        ex(dao, "Straight Arm Pulldown", "Back", "cable")
        ex(dao, "Sumo Deadlift", "Back", "barbell")
        ex(dao, "T-Bar Row", "Back", "barbell")

        // ===== BICEPS =====
        ex(dao, "Barbell Curl", "Biceps", "barbell")
        ex(dao, "Cable Curl", "Biceps", "cable")
        ex(dao, "Concentration Curl", "Biceps", "dumbbell")
        ex(dao, "Dumbbell Curl", "Biceps", "dumbbell")
        ex(dao, "EZ-Bar Curl", "Biceps", "barbell")
        ex(dao, "EZ-Bar Preacher Curl", "Biceps", "barbell")
        ex(dao, "Hammer Curl", "Biceps", "dumbbell")
        ex(dao, "Incline Dumbbell Curl", "Biceps", "dumbbell")
        ex(dao, "Preacher Curl", "Biceps", "barbell")
        ex(dao, "Reverse Barbell Curl", "Biceps", "barbell")
        ex(dao, "Spider Curl", "Biceps", "dumbbell")
        ex(dao, "Zottman Curl", "Biceps", "dumbbell")

        // ===== CHEST =====
        ex(dao, "Bench Press", "Chest", "barbell", isMain = true)
        ex(dao, "Cable Crossover", "Chest", "cable")
        ex(dao, "Close Grip Bench Press", "Chest", "barbell")
        ex(dao, "Decline Barbell Bench Press", "Chest", "barbell")
        ex(dao, "Decline Dumbbell Bench Press", "Chest", "dumbbell")
        ex(dao, "Dip", "Chest", "bodyweight")
        ex(dao, "Dumbbell Bench Press", "Chest", "dumbbell")
        ex(dao, "Dumbbell Fly", "Chest", "dumbbell")
        ex(dao, "Floor Press", "Chest", "barbell")
        ex(dao, "Incline Barbell Bench Press", "Chest", "barbell")
        ex(dao, "Incline Dumbbell Bench Press", "Chest", "dumbbell")
        ex(dao, "Incline Dumbbell Fly", "Chest", "dumbbell")
        ex(dao, "Machine Chest Press", "Chest", "machine")
        ex(dao, "Pec Deck", "Chest", "machine")
        ex(dao, "Push-Up", "Chest", "bodyweight")
        ex(dao, "Weighted Dip", "Chest", "bodyweight")

        // ===== FOREARMS =====
        ex(dao, "Barbell Wrist Curl", "Forearms", "barbell")
        ex(dao, "Farmers Walk", "Forearms", "dumbbell")
        ex(dao, "Plate Pinch", "Forearms", "barbell")
        ex(dao, "Reverse Wrist Curl", "Forearms", "barbell")
        ex(dao, "Wrist Roller", "Forearms", "barbell")

        // ===== LEGS =====
        ex(dao, "Barbell Calf Raise", "Legs", "barbell")
        ex(dao, "Barbell Lunge", "Legs", "barbell")
        ex(dao, "Box Squat", "Legs", "barbell")
        ex(dao, "Bulgarian Split Squat", "Legs", "dumbbell")
        ex(dao, "Front Squat", "Legs", "barbell")
        ex(dao, "Glute Bridge", "Legs", "barbell")
        ex(dao, "Glute-Ham Raise", "Legs", "bodyweight")
        ex(dao, "Goblet Squat", "Legs", "dumbbell")
        ex(dao, "Hack Squat", "Legs", "machine")
        ex(dao, "Hip Thrust", "Legs", "barbell")
        ex(dao, "Leg Curl", "Legs", "machine")
        ex(dao, "Leg Extension", "Legs", "machine")
        ex(dao, "Leg Press", "Legs", "machine")
        ex(dao, "Paused Squat", "Legs", "barbell")
        ex(dao, "Pistol Squat", "Legs", "bodyweight")
        ex(dao, "Romanian Deadlift", "Legs", "barbell")
        ex(dao, "Seated Calf Raise", "Legs", "machine")
        ex(dao, "Squat", "Legs", "barbell", isMain = true)
        ex(dao, "Standing Calf Raise", "Legs", "machine")
        ex(dao, "Step-Up", "Legs", "dumbbell")
        ex(dao, "Stiff Leg Deadlift", "Legs", "barbell")
        ex(dao, "Sumo Squat", "Legs", "barbell")
        ex(dao, "Walking Lunge", "Legs", "dumbbell")

        // ===== SHOULDERS =====
        ex(dao, "Arnold Press", "Shoulders", "dumbbell")
        ex(dao, "Barbell Front Raise", "Shoulders", "barbell")
        ex(dao, "Bent Over Reverse Fly", "Shoulders", "dumbbell")
        ex(dao, "Cable Face Pull", "Shoulders", "cable")
        ex(dao, "Dumbbell Front Raise", "Shoulders", "dumbbell")
        ex(dao, "Dumbbell Lateral Raise", "Shoulders", "dumbbell")
        ex(dao, "Dumbbell Shoulder Press", "Shoulders", "dumbbell")
        ex(dao, "Machine Reverse Fly", "Shoulders", "machine")
        ex(dao, "Machine Shoulder Press", "Shoulders", "machine")
        ex(dao, "Overhead Press", "Shoulders", "barbell", isMain = true)
        ex(dao, "Push Press", "Shoulders", "barbell")
        ex(dao, "Seated Dumbbell Press", "Shoulders", "dumbbell")
        ex(dao, "Upright Row", "Shoulders", "barbell")

        // ===== TRICEPS =====
        ex(dao, "Cable Pushdown", "Triceps", "cable")
        ex(dao, "Close Grip Bench Press", "Triceps", "barbell")
        ex(dao, "Diamond Push-Up", "Triceps", "bodyweight")
        ex(dao, "Dumbbell Kickback", "Triceps", "dumbbell")
        ex(dao, "EZ-Bar Skull Crusher", "Triceps", "barbell")
        ex(dao, "Overhead Cable Extension", "Triceps", "cable")
        ex(dao, "Overhead Dumbbell Extension", "Triceps", "dumbbell")
        ex(dao, "Rope Pushdown", "Triceps", "cable")
        ex(dao, "Skull Crusher", "Triceps", "barbell")
        ex(dao, "Tricep Dip", "Triceps", "bodyweight")

        // ===== TRAPS =====
        ex(dao, "Barbell Shrug", "Traps", "barbell")
        ex(dao, "Dumbbell Shrug", "Traps", "dumbbell")
        ex(dao, "Face Pull", "Traps", "cable")
        ex(dao, "Power Clean", "Traps", "barbell")
        ex(dao, "Rack Pull Shrug", "Traps", "barbell")

        // ===== CARDIO =====
        ex(dao, "Cycling", "Cardio", "cardio")
        ex(dao, "Elliptical", "Cardio", "cardio")
        ex(dao, "Jump Rope", "Cardio", "cardio")
        ex(dao, "Rowing Machine", "Cardio", "cardio")
        ex(dao, "Running (Outdoor)", "Cardio", "cardio")
        ex(dao, "Running (Treadmill)", "Cardio", "cardio")
        ex(dao, "Stair Climber", "Cardio", "cardio")
        ex(dao, "Swimming", "Cardio", "cardio")
        ex(dao, "Walking", "Cardio", "cardio")
    }

    private fun ex(dao: PrayerBookDao, name: String, category: String, equipment: String, isMain: Boolean = false) {
        dao.insertExercise(Exercise(name = name, category = category, isMainLift = isMain))
    }
}
