package com.example.timer;

import static android.Manifest.permission.POST_NOTIFICATIONS;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;

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

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        askNotificationPermission();
        initializeViews();
        //setupEdgeToEdge();
        initializeFirebase();
        setupListeners();
        getFCMToken();
        subscribeToTopic();
    }

    // Declare the launcher at the top of your Activity/Fragment:
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // FCM SDK (and your app) can post notifications.
                } else {
                    // TODO: Inform user that that your app will not show notifications.
                }
            });

    private void askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
                // FCM SDK (and your app) can post notifications.
            } else if (shouldShowRequestPermissionRationale(POST_NOTIFICATIONS)) {
                // TODO: display an educational UI explaining to the user the features that will be enabled
                //       by them granting the POST_NOTIFICATION permission. This UI should provide the user
                //       "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                //       If the user selects "No thanks," allow the user to continue without notifications.
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(POST_NOTIFICATIONS);
            }
        }
    }

    private void getFCMToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                        return;
                    }

                    // Get new FCM registration token
                    String token = task.getResult();

                    // Save the token to Firebase Realtime Database
                    DatabaseReference tokensRef = FirebaseDatabase.getInstance().getReference("fcm_tokens");
                    tokensRef.child(token).setValue(true);
                });
    }

    private void initializeViews() {
        countdownTextView = findViewById(R.id.countdownTextView);
        selectDateButton = findViewById(R.id.selectDateButton);
    }

    private void initializeFirebase() {
        FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
        countdownRef = firebaseDatabase.getReference();
        Log.d(TAG, "Firebase reference: " + countdownRef);
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

    private void setupEdgeToEdge() {
        View contentView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (view, windowInsets) -> {
            WindowInsetsCompat insets = WindowInsetsCompat.toWindowInsetsCompat(windowInsets.toWindowInsets());
            int systemBars = WindowInsetsCompat.Type.systemBars();

            int systemBarInsetLeft = insets.getInsets(systemBars).left;
            int systemBarInsetTop = insets.getInsets(systemBars).top;
            int systemBarInsetRight = insets.getInsets(systemBars).right;
            int systemBarInsetBottom = insets.getInsets(systemBars).bottom;

            view.setPadding(systemBarInsetLeft, systemBarInsetTop, systemBarInsetRight, systemBarInsetBottom);

            return WindowInsetsCompat.CONSUMED;
        });

        // Make the content appear behind the system bars
        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(getWindow().getDecorView());
        if (controller != null) {
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }
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

        String daysStr = days < 2 ? "day" : "days";
        String hoursStr = days < 2 ? "hour" : "hours";
        String minutesStr = days < 2 ? "minute" : "minutes";
        String secondsStr = days < 2 ? "second" : "seconds";

        String countdownText = getString(R.string.countdown_format,
                days, daysStr, hours % 24, hoursStr, minutes % 60, minutesStr, seconds % 60, secondsStr);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }

    private void saveSelectedDateTime() {
        LocalDateTime selectedDateTime = LocalDateTime.ofInstant(selectedCalendar.toInstant(), ZoneId.systemDefault());
        String formattedDateTime = selectedDateTime.format(DATE_FORMATTER);
        countdownRef.child(FIREBASE_CHILD_SELECTED_DATE).setValue(formattedDateTime)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Date saved successfully");
                    sendNotificationToAll(formattedDateTime);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error saving date", e));
    }

    private void sendNotificationToAll(String newDateTime) {
        DatabaseReference notifyRef = FirebaseDatabase.getInstance().getReference("notify_update");
        notifyRef.setValue(newDateTime);
    }

    private void subscribeToTopic() {
        FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                .addOnCompleteListener(task -> {
                    String msg = task.isSuccessful() ? "Subscribed to all_users topic" : "Failed to subscribe to all_users topic";
                    Log.d(TAG, msg);
                });
    }
}