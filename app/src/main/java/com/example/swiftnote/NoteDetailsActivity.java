package com.example.swiftnote;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Locale;

public class NoteDetailsActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO = 101;
    private static final int REQUEST_SPEECH_INPUT = 102;

    EditText titleEditText, contentEditText;
    ImageButton saveNoteBtn, micButton;
    TextView pageTitleTextView, deleteNote;

    String title, content, docId;
    boolean isEditMode = false;

    // Track which EditText is currently focused
    EditText currentFocusedField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_note_details);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // --- Views (IDs match your XML) ---
        titleEditText = findViewById(R.id.notes_title_text);
        contentEditText = findViewById(R.id.notes_content_text);
        saveNoteBtn = findViewById(R.id.save_note_btn);
        micButton = findViewById(R.id.btn_mic);
        pageTitleTextView = findViewById(R.id.page_title);
        deleteNote = findViewById(R.id.detete_note_text_view);

        // --- Focus tracking ---
        titleEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) currentFocusedField = titleEditText;
        });
        contentEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) currentFocusedField = contentEditText;
        });

        // If user taps into the field but it wasn't focused initially, ensure we set it when clicked:
        titleEditText.setOnClickListener(v -> currentFocusedField = titleEditText);
        contentEditText.setOnClickListener(v -> currentFocusedField = contentEditText);

        // --- Load intent extras (existing behavior) ---
        title = getIntent().getStringExtra("title");
        content = getIntent().getStringExtra("content");
        docId = getIntent().getStringExtra("docId");
        if (docId != null && !docId.isEmpty()) {
            isEditMode = true;
        }

        titleEditText.setText(title);
        contentEditText.setText(content);
        if (isEditMode) {
            pageTitleTextView.setText("Edit Your Note");
            deleteNote.setVisibility(TextView.VISIBLE);
        }

        // --- Existing save/delete button behavior ---
        saveNoteBtn.setOnClickListener((v) -> saveNote());
        deleteNote.setOnClickListener(v -> deleteNoteFromFireBase());

        // --- Mic click: check permission then start speech ---
        micButton.setOnClickListener(v -> checkMicPermissionAndStart());
    }

    // Check runtime permission
    private void checkMicPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            // Request permission
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO);
        } else {
            startSpeechToText();
        }
    }

    // Permission result callback
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSpeechToText();
            } else {
                Toast.makeText(this, "Microphone permission is required for speech input", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Start the Google speech recognition UI
    private void startSpeechToText() {
        // If no field is focused, default to content field
        if (currentFocusedField == null) {
            currentFocusedField = contentEditText;
            contentEditText.requestFocus();
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        // Helpful prompt
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now for: " +
                (currentFocusedField == titleEditText ? "Title" : "Content"));

        // Prefer online recognition for better accuracy
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);

        // Get more possible matches
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        // Try to get partial results (may be ignored by some devices)
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        try {
            startActivityForResult(intent, REQUEST_SPEECH_INPUT);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Speech recognition not supported on this device", Toast.LENGTH_SHORT).show();
        }
    }

    // Handle speech result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SPEECH_INPUT) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (result != null && !result.isEmpty()) {
                    String spokenText = result.get(0);
                    // Append to existing text rather than overwrite
                    if (currentFocusedField != null) {
                        int curPos = currentFocusedField.getSelectionStart();
                        if (curPos < 0) curPos = currentFocusedField.getText().length();
                        currentFocusedField.getText().insert(curPos, (currentFocusedField.getText().length() == 0 ? "" : " ") + spokenText);
                    } else {
                        // fallback - should not usually happen (we set default above)
                        contentEditText.append(spokenText);
                    }
                } else {
                    // No results returned
                    Toast.makeText(this, "Didn't catch that. Try speaking again.", Toast.LENGTH_SHORT).show();
                }
            } else {
                // User cancelled or recognition failed
                Toast.makeText(this, "Speech input cancelled or failed. Try again.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // --- Existing Firebase delete/save functions kept intact ---
    private void deleteNoteFromFireBase() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DocumentReference documentReference =
                FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(userId)
                        .collection("notes")
                        .document(docId);

        documentReference.delete().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Utility.showToast(NoteDetailsActivity.this, "Note deleted Successfully");
                finish();
            } else {
                Utility.showToast(NoteDetailsActivity.this, "Failed while deleting note");
            }
        });
    }

    void saveNote() {
        String noteTitle = titleEditText.getText().toString();
        String noteContent = contentEditText.getText().toString();

        if (noteTitle == null || noteTitle.isEmpty()) {
            titleEditText.setError("Title is required");
            return;
        }

        Note note = new Note();
        note.setTitle(noteTitle);
        note.setContent(noteContent);
        note.setTimestamp(Timestamp.now());
        note.setUserId(FirebaseAuth.getInstance().getCurrentUser().getUid());

        saveNoteToFirebase(note);
    }

    void saveNoteToFirebase(Note note) {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DocumentReference documentReference;

        if (isEditMode) {
            documentReference =
                    FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(userId)
                            .collection("notes")
                            .document(docId);
        } else {
            documentReference =
                    FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(userId)
                            .collection("notes")
                            .document();
        }

        documentReference.set(note).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Utility.showToast(NoteDetailsActivity.this, "Note Added Successfully");
                finish();
            } else {
                Utility.showToast(NoteDetailsActivity.this, "Failed while adding note");
            }
        });
    }
}
