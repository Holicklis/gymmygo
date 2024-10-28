package hku.cs.comp3330.section1a2024.group19.gymmygo;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CalendarView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String FILE_NAME = "workout_records.json";
    private Gson gson = new Gson();
    private Calendar selectedDate = Calendar.getInstance();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    // Use String as the key instead of Date
    private Map<String, List<Workout>> workoutRecords = new HashMap<>();
    private WorkoutAdapter adapter;
    private List<Workout> workoutList = new ArrayList<>(); // For the RecyclerView

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Ensure this layout file exists
        loadWorkoutRecords();

        // Initialize selectedDate to current date without time
        selectedDate.setTime(getDateWithoutTime(new Date()));

        Button btnShowCalendar = findViewById(R.id.btnShowCalendar);
        btnShowCalendar.setOnClickListener(v -> showCalendarDialog());

        // Apply window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize RecyclerView
        RecyclerView rvWorkoutList = findViewById(R.id.rvWorkoutList);

        // Initialize workoutList (if not already done)
        workoutList = new ArrayList<>();

        // Initialize adapter and set it to RecyclerView
        adapter = new WorkoutAdapter(workoutList);
        rvWorkoutList.setLayoutManager(new LinearLayoutManager(this));
        rvWorkoutList.setAdapter(adapter);

        // Load workouts for today's date
        String today = dateFormat.format(selectedDate.getTime());
        loadWorkoutsForDate(today);

        // Add a listener for the Add New Workout button
        Button btnAddWorkout = findViewById(R.id.btnAddWorkout);
        btnAddWorkout.setOnClickListener(v -> addNewWorkout());
    }

    // Method to add a new workout to the list and notify the adapter
    private void addNewWorkout() {
        Date currentDate = getDateWithoutTime(selectedDate.getTime());
        Workout newWorkout = new Workout("New Exercise", currentDate);

        workoutList.add(newWorkout);
        adapter.notifyItemInserted(workoutList.size() - 1);

        String currentDateStr = dateFormat.format(currentDate);
        workoutRecords.put(currentDateStr, new ArrayList<>(workoutList));
        saveWorkoutRecords(); // Save to internal storage
    }

    private void loadWorkoutsForDate(String dateStr) {
        // Save the current date's workouts before switching dates
        if (!workoutList.isEmpty()) {
            String currentDateStr = dateFormat.format(getDateWithoutTime(workoutList.get(0).getWorkoutDate()));
            workoutRecords.put(currentDateStr, new ArrayList<>(workoutList)); // Save the workouts for the current date
        }

        // Now load workouts for the newly selected date
        List<Workout> workoutsForDate = workoutRecords.get(dateStr);
        if (workoutsForDate != null) {
            workoutList.clear();
            workoutList.addAll(workoutsForDate);
        } else {
            workoutList.clear(); // If no workouts exist for this date, start with an empty list
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged(); // Refresh the RecyclerView with the new date's workouts
        }
    }

    private void showCalendarDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_calendar, null);
        CalendarView calendarView = dialogView.findViewById(R.id.dialogCalendarView);

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        // Set the CalendarView's date to the last selected date
        calendarView.setDate(selectedDate.getTimeInMillis(), false, true);

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            // Save the current workouts before switching dates
            if (!workoutList.isEmpty()) {
                String currentDateStr = dateFormat.format(getDateWithoutTime(workoutList.get(0).getWorkoutDate()));
                workoutRecords.put(currentDateStr, new ArrayList<>(workoutList)); // Save current date's workouts
            }

            // Now load the workouts for the newly selected date
            selectedDate.set(year, month, dayOfMonth);
            Date newDate = getDateWithoutTime(selectedDate.getTime());
            String newDateStr = dateFormat.format(newDate);

            loadWorkoutsForDate(newDateStr); // Load workouts for the selected date
            dialog.dismiss(); // Close the dialog after selecting a date
        });

        dialog.show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save the current workouts before the activity pauses
        if (!workoutList.isEmpty()) {
            String currentDateStr = dateFormat.format(getDateWithoutTime(workoutList.get(0).getWorkoutDate()));
            workoutRecords.put(currentDateStr, new ArrayList<>(workoutList));
        }
        saveWorkoutRecords();
    }

    private void saveWorkoutRecords() {
        try {
            FileOutputStream fos = openFileOutput(FILE_NAME, Context.MODE_PRIVATE);
            OutputStreamWriter writer = new OutputStreamWriter(fos);
            String json = gson.toJson(workoutRecords); // Convert to JSON
            writer.write(json);
            writer.close();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadWorkoutRecords() {
        try {
            FileInputStream fis = openFileInput(FILE_NAME);
            InputStreamReader reader = new InputStreamReader(fis);
            Type type = new TypeToken<Map<String, List<Workout>>>() {}.getType();
            workoutRecords = gson.fromJson(reader, type); // Convert from JSON
            reader.close();
            fis.close();
        } catch (Exception e) {
            e.printStackTrace();
            workoutRecords = new HashMap<>(); // Initialize if file doesn't exist
        }
    }

    // Utility method to normalize dates by removing the time components
    private Date getDateWithoutTime(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        // Set time components to zero
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }
}
