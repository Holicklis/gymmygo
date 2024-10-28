package hku.cs.comp3330.section1a2024.group19.gymmygo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Workout {
    private String exerciseName;
    private List<Set> sets;
    private Date workoutDate;

    public Workout(String exerciseName, Date workoutDate) {
        this.exerciseName = exerciseName;
        this.sets = new ArrayList<>();
        this.workoutDate = workoutDate;
    }

    public Date getWorkoutDate() {
        return workoutDate;
    }

    public void setWorkoutDate(Date workoutDate) {
        this.workoutDate = workoutDate;
    }

    public String getExerciseName() {
        return exerciseName;
    }

    public void setExerciseName(String exerciseName) {
        this.exerciseName = exerciseName;
    }

    public List<Set> getSets() {
        return sets;
    }

    public void addSet(Set set) {
        sets.add(set);
    }

    public void removeSet(int index) {
        if (index >= 0 && index < sets.size()) {
            sets.remove(index);
        }
    }

    public static class Set {
        private int reps;
        private float weight;

        public Set(int reps, float weight) {
            this.reps = reps;
            this.weight = weight;
        }

        public int getReps() {
            return reps;
        }

        public void setReps(int reps) {
            this.reps = reps;
        }

        public float getWeight() {
            return weight;
        }

        public void setWeight(float weight) {
            this.weight = weight;
        }
    }
}
