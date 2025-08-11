package com.example.swiftnote;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class NoteDetailsActivity extends AppCompatActivity {
    EditText titleEditText,contentEditText;
    ImageButton saveNoteBtn;
    TextView pageTitleTextView;
    String title,content,docId;
    boolean isEditMode = false;
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
        titleEditText = findViewById(R.id.notes_title_text);
        contentEditText = findViewById(R.id.notes_content_text);
        saveNoteBtn = findViewById(R.id.save_note_btn);
        pageTitleTextView = findViewById(R.id.page_title);

        title = getIntent().getStringExtra("title");
        content = getIntent().getStringExtra("content");
        docId = getIntent().getStringExtra("docId");
        if(docId!=null && !docId.isEmpty()){
            isEditMode = true;
        }

        titleEditText.setText(title);
        contentEditText.setText(content);
        if(isEditMode){
            pageTitleTextView.setText("Edit Your Note");
        }
        saveNoteBtn.setOnClickListener((v)->saveNote());
    }

    void saveNote(){
        String noteTitle = titleEditText.getText().toString();
        String noteContent = contentEditText.getText().toString();
        if(noteTitle == null || noteTitle.isEmpty()){
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

    void saveNoteToFirebase(Note note){
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DocumentReference documentReference;
        if (isEditMode){
             documentReference =
                    FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(userId)
                            .collection("notes")
                            .document(docId);
        }else{
             documentReference =
                    FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(userId)
                            .collection("notes")
                            .document();  // auto-generated note ID
        }

        documentReference.set(note).addOnCompleteListener(task -> {
            if(task.isSuccessful()){
                Utility.showToast(NoteDetailsActivity.this,"Note Added Successfully");
                finish();
            }else{
                Utility.showToast(NoteDetailsActivity.this,"Failed while adding note");
            }
        });
    }

}