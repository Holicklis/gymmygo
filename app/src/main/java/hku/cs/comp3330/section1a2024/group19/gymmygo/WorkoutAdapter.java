package hku.cs.comp3330.section1a2024.group19.gymmygo;

import android.app.DatePickerDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class WorkoutAdapter extends RecyclerView.Adapter<WorkoutAdapter.WorkoutViewHolder> {
    private List<Workout> workoutList;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public WorkoutAdapter(List<Workout> workoutList) {
        this.workoutList = workoutList;
    }

    @NonNull
    @Override
    public WorkoutViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_workout, parent, false);
        return new WorkoutViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WorkoutViewHolder holder, int position) {
        Workout workout = workoutList.get(position);
        holder.etExerciseName.setText(workout.getExerciseName());

        // Listener to update workout name when edited
        holder.etExerciseName.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                workout.setExerciseName(holder.etExerciseName.getText().toString());
            }
        });

        holder.btnAddSet.setOnClickListener(v -> {
            String repsInput = holder.etReps.getText().toString();
            String weightInput = holder.etWeight.getText().toString();

            if (!repsInput.isEmpty() && !weightInput.isEmpty()) {
                try {
                    int reps = Integer.parseInt(repsInput);
                    float weight = Float.parseFloat(weightInput);

                    workout.addSet(new Workout.Set(reps, weight)); // Add the set to the workout

                    holder.etReps.setText(""); // Clear input fields after adding
                    holder.etWeight.setText("");

                    holder.tvSetsList.setText(formatSets(workout.getSets())); // Update sets list display

                } catch (NumberFormatException e) {
                    Toast.makeText(holder.itemView.getContext(), "Invalid input", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(holder.itemView.getContext(), "Please fill in both fields", Toast.LENGTH_SHORT).show();
            }
        });

        holder.tvSetsList.setText(formatSets(workout.getSets()));

        // Delete workout button listener
        holder.btnDeleteWorkout.setOnClickListener(v -> {
            workoutList.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, workoutList.size());
        });
    }

    // Method to format the sets as a readable string
    private String formatSets(List<Workout.Set> sets) {
        StringBuilder setsFormatted = new StringBuilder();
        for (int i = 0; i < sets.size(); i++) {
            Workout.Set set = sets.get(i);
            setsFormatted.append("Set ").append(i + 1).append(": ")
                    .append(set.getReps()).append(" reps, ")
                    .append(set.getWeight()).append(" kg\n");
        }
        return setsFormatted.toString();
    }

    @Override
    public int getItemCount() {
        return workoutList.size();
    }

    public class WorkoutViewHolder extends RecyclerView.ViewHolder {
        EditText etExerciseName, etReps, etWeight;
        TextView tvSetsList;
        Button btnAddSet, btnDeleteWorkout;

        public WorkoutViewHolder(@NonNull View itemView) {
            super(itemView);
            etExerciseName = itemView.findViewById(R.id.etExerciseName);
            etReps = itemView.findViewById(R.id.etReps);
            etWeight = itemView.findViewById(R.id.etWeight);
            tvSetsList = itemView.findViewById(R.id.tvSetsList);
            btnAddSet = itemView.findViewById(R.id.btnAddSet);
            btnDeleteWorkout = itemView.findViewById(R.id.btnDeleteWorkout);
        }
    }
}
