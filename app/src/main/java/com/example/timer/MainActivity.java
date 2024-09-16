package com.example.timer;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String FIREBASE_CHILD_SELECTED_DATE = "selectedDate";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private FloatingActionButton selectDateButton;
    private TextView countdownTextView;
    private DatabaseReference countdownRef;
    private CountDownTimer countDownTimer;
    private Calendar selectedCalendar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        initializeFirebase();
        setupListeners();
    }

    private void initializeViews() {
        countdownTextView = findViewById(R.id.countdownTextView);
        selectDateButton = findViewById(R.id.selectDateButton);
    }

    private void initializeFirebase() {
        FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
        countdownRef = firebaseDatabase.getReference();
        Log.d(TAG, "Firebase reference: " + countdownRef.toString());
    }

    private void setupListeners() {
        selectDateButton.setOnClickListener(v -> showDatePickerDialog());
        setupFirebaseListener();
        fetchInitialDate();
    }

    private void setupFirebaseListener() {
        countdownRef.child(FIREBASE_CHILD_SELECTED_DATE).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                String dateStr = dataSnapshot.getValue(String.class);
                updateCountdownUI(dateStr);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase listener cancelled", error.toException());
            }
        });
    }

    private void fetchInitialDate() {
        countdownRef.child(FIREBASE_CHILD_SELECTED_DATE).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String selectedDate = task.getResult().getValue(String.class);
                updateCountdownUI(selectedDate);
            } else {
                Log.e(TAG, "Failed to fetch initial date", task.getException());
            }
        });
    }

    private void updateCountdownUI(@Nullable String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            countdownTextView.setText(getString(R.string.no_timer));
            return;
        }

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        try {
            LocalDateTime date = LocalDateTime.parse(dateStr, DATE_FORMATTER);
            long timeRemaining = date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis();
            startCountdownTimer(timeRemaining);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing date: " + dateStr, e);
            countdownTextView.setText(getString(R.string.invalid_date));
        }
    }

    private void startCountdownTimer(long timeRemaining) {
        countDownTimer = new CountDownTimer(timeRemaining, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                updateCountdownText(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                countdownTextView.setText(getString(R.string.countdown_done));
            }
        }.start();
    }

    private void updateCountdownText(long millisUntilFinished) {
        long seconds = millisUntilFinished / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        String countdownText = getString(R.string.countdown_format,
                days, hours % 24, minutes % 60, seconds % 60);
        countdownTextView.setText(countdownText);
    }

    private void showDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedCalendar = Calendar.getInstance();
                    selectedCalendar.set(year, month, dayOfMonth);
                    showTimePickerDialog();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void showTimePickerDialog() {
        if (selectedCalendar == null) {
            Log.e(TAG, "No date selected");
            return;
        }

        int currentHour = selectedCalendar.get(Calendar.HOUR_OF_DAY);
        int currentMinute = selectedCalendar.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    selectedCalendar.set(Calendar.MINUTE, minute);
                    saveSelectedDateTime();
                },
                currentHour,
                currentMinute,
                true
        );
        timePickerDialog.show();
    }

    private void saveSelectedDateTime() {
        LocalDateTime selectedDateTime = LocalDateTime.ofInstant(selectedCalendar.toInstant(), ZoneId.systemDefault());
        String formattedDateTime = selectedDateTime.format(DATE_FORMATTER);
        countdownRef.child(FIREBASE_CHILD_SELECTED_DATE).setValue(formattedDateTime)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Date saved successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Error saving date", e));
    }
}