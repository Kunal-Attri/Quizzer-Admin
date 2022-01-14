package com.example.quizzeradmin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class QuestionsActivity extends AppCompatActivity {

    private Button add, excel;
    private RecyclerView recyclerView;
    private QuestionsAdapter adapter;
    public static List<QuestionModel> list;
    private Dialog loadingDialog;
    private DatabaseReference myRef;
    private String setId;
    private String categoryName;
    public static final int CELL_COUNT = 6;
    private TextView loadingText;
    private int pos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_questions);
        Toolbar toolbar = findViewById(R.id.toolbar);

        myRef = FirebaseDatabase.getInstance().getReference();

        loadingDialog = new Dialog(this);
        loadingDialog.setContentView(R.layout.loading);
        loadingDialog.getWindow().setBackgroundDrawable(getDrawable(R.drawable.rounded_corners));
        loadingDialog.getWindow().setLayout(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        loadingDialog.setCancelable(false);
        loadingText = loadingDialog.findViewById(R.id.loadingText);

        setSupportActionBar(toolbar);

        categoryName = getIntent().getStringExtra("category");
        setId = getIntent().getStringExtra("setId");
        pos = getIntent().getIntExtra("position", 1);
        getSupportActionBar().setTitle(categoryName + " / Set " + pos);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        add = findViewById(R.id.add_btn);
        excel = findViewById(R.id.excel_btn);
        recyclerView = findViewById(R.id.recycler_view);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(RecyclerView.VERTICAL);

        recyclerView.setLayoutManager(layoutManager);

        list = new ArrayList<>();
        adapter = new QuestionsAdapter(list, categoryName, new QuestionsAdapter.DeleteListener() {
            @Override
            public void onLongClick(final int position, final String id) {
                new AlertDialog.Builder(QuestionsActivity.this, R.style.Theme_AppCompat_Light_Dialog)
                        .setTitle("Delete Question")
                        .setMessage("Are you sure, you want to delete this question?")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                loadingText.setText("Deleting...");
                                loadingDialog.show();
                                myRef.child("SETS").child(setId).child(id).removeValue().addOnCompleteListener(new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Void> task) {
                                        if (task.isSuccessful()) {
                                            final QuestionModel deletedQuestion = list.get(position);

                                            list.remove(position);

                                            adapter.notifyItemRemoved(position);
                                            adapter.notifyItemRangeChanged(position, list.size()-position);

                                            Toast.makeText(QuestionsActivity.this, "Question successfully deleted", Toast.LENGTH_SHORT).show();

                                            Snackbar snackbar = Snackbar.make(recyclerView , "Question removed!", Snackbar.LENGTH_LONG);
                                            snackbar.setAction("UNDO", new View.OnClickListener() {
                                                @Override
                                                public void onClick(View view) {
                                                    final HashMap<String,Object> map = new HashMap<>();
                                                    map.put("correctAns", deletedQuestion.getAnswer());
                                                    map.put("optionA", deletedQuestion.getA());
                                                    map.put("optionB", deletedQuestion.getB());
                                                    map.put("optionC", deletedQuestion.getC());
                                                    map.put("optionD", deletedQuestion.getD());
                                                    map.put("question", deletedQuestion.getQuestion());
                                                    map.put("setId", deletedQuestion.getSet());
                                                    // undo is selected, restore the deleted item
                                                    loadingText.setText("Adding question again...");
                                                    loadingDialog.show();

                                                    FirebaseDatabase.getInstance().getReference()
                                                            .child("SETS").child(setId).child(id)
                                                            .setValue(map).addOnCompleteListener(new OnCompleteListener<Void>() {
                                                        @Override
                                                        public void onComplete(@NonNull Task<Void> task) {
                                                            list.add(position, deletedQuestion);
                                                            adapter.notifyItemInserted(position);
                                                            adapter.notifyItemRangeChanged(position, list.size()-position);;
                                                            loadingDialog.dismiss();
                                                        }
                                                    });
                                                }
                                            });
                                            snackbar.setActionTextColor(Color.LTGRAY);
                                            snackbar.show();

                                        } else {
                                            Toast.makeText(QuestionsActivity.this, "Error Q118: Failed to delete", Toast.LENGTH_SHORT).show();
                                        }
                                        loadingDialog.dismiss();
                                        loadingText.setText("Loading...");
                                    }
                                });
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
            }
        });

        recyclerView.setAdapter(adapter);

        getData(categoryName, setId);

        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent addquestion = new Intent(QuestionsActivity.this, AddQuestionActivity.class);
                addquestion.putExtra("categoryName", categoryName);
                addquestion.putExtra("setId", setId);
                startActivity(addquestion);
            }
        });

        excel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (ActivityCompat.checkSelfPermission(QuestionsActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    selectFile();
                } else {
                    ActivityCompat.requestPermissions(QuestionsActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 101);
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 101) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                selectFile();
            } else {
                Toast.makeText(this, "Error Q167: Kindly grant permissions", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void selectFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select an Excel File"), 102);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 102) {
            if (resultCode == RESULT_OK) {

                String filePath = data.getData().getPath();
                if (filePath.endsWith(".xlsx") || filePath.endsWith(".xls")) {
                    readFile(data.getData());
                } else {
                    Toast.makeText(this, "Error Q190: Kindly select an excel file", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private void getData(String categoryName, final String setId) {
        loadingDialog.show();

        myRef.child("SETS").child(setId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    String id = dataSnapshot.getKey();
                    String question = dataSnapshot.child("question").getValue().toString();
                    String a = dataSnapshot.child("optionA").getValue().toString();
                    String b = dataSnapshot.child("optionB").getValue().toString();
                    String c = dataSnapshot.child("optionC").getValue().toString();
                    String d = dataSnapshot.child("optionD").getValue().toString();
                    String correctAns = dataSnapshot.child("correctAns").getValue().toString();

                    list.add(new QuestionModel(question, a, b, c, d, correctAns, id, setId));
                }
                loadingDialog.dismiss();
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(QuestionsActivity.this, "Error Q228: Something went wrong.", Toast.LENGTH_SHORT).show();
                loadingDialog.dismiss();
                finish();
            }
        });
    }

    private void readFile(Uri fileUri) {
        loadingText.setText("Scanning questions...");
        loadingDialog.show();

        HashMap<String, Object> parentMap = new HashMap<>();
        final List<QuestionModel> tempList = new ArrayList<>();

        try {
            InputStream inputStream = getContentResolver().openInputStream(fileUri);
            XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
            XSSFSheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();

            int rowsCount = sheet.getPhysicalNumberOfRows();

            if (rowsCount > 0) {

                for (int r = 0; r < rowsCount; r++) {
                    Row row = sheet.getRow(r);

                    if (row.getPhysicalNumberOfCells() == CELL_COUNT) {
                        String question = getCellData(row, 0, formulaEvaluator);
                        String a = getCellData(row, 1, formulaEvaluator);
                        String b = getCellData(row, 2, formulaEvaluator);
                        String c = getCellData(row, 3, formulaEvaluator);
                        String d = getCellData(row, 4, formulaEvaluator);
                        String correctAns = getCellData(row, 5, formulaEvaluator);

                        question = question.substring(0, question.length()/2);
                        a = a.substring(0, a.length()/2);
                        b = b.substring(0, b.length()/2);
                        c = c.substring(0, c.length()/2);
                        d = d.substring(0, d.length()/2);
                        correctAns = correctAns.substring(0, correctAns.length()/2);

                        if (correctAns.equals(a) || correctAns.equals(b) || correctAns.equals(c) || correctAns.equals(d)) {

                            HashMap<String, Object> questionMap = new HashMap<>();
                            questionMap.put("question", question);
                            questionMap.put("optionA", a);
                            questionMap.put("optionB", b);
                            questionMap.put("optionC", c);
                            questionMap.put("optionD", d);
                            questionMap.put("correctAns", correctAns);
                            questionMap.put("setId", setId);

                            String id = UUID.randomUUID().toString();

                            parentMap.put(id, questionMap);
                            tempList.add(new QuestionModel(question, a, b, c, d, correctAns, id, setId));
                        } else {
                            loadingDialog.dismiss();
                            loadingText.setText("Loading...");
                            Toast.makeText(this, "Error Q288: Row no. " + (r+1) + " has no correct answer", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } else {
                        loadingDialog.dismiss();
                        loadingText.setText("Loading...");
                        Toast.makeText(this, "Error Q294: Row no. " + (r+1) + " has incorrect data", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                loadingText.setText("Uploading...");

                FirebaseDatabase.getInstance().getReference()
                        .child("SETS").child(setId).updateChildren(parentMap).addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            list.addAll(tempList);
                            adapter.notifyDataSetChanged();
                        } else {
                            Toast.makeText(QuestionsActivity.this, "Error Q309: Something went wrong!", Toast.LENGTH_SHORT).show();
                        }
                        loadingDialog.dismiss();
                        loadingText.setText("Loading...");
                    }
                });
            } else {
                loadingDialog.dismiss();
                loadingText.setText("Loading...");
                Toast.makeText(this, "Error Q318: File empty!", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            loadingDialog.dismiss();
            loadingText.setText("Loading...");
            Toast.makeText(this, "Error Q325: File not found", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            loadingDialog.dismiss();
            loadingText.setText("Loading...");
            Toast.makeText(this, "Error Q330: IO Error", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        adapter.notifyDataSetChanged();
    }

    private String getCellData(Row row, int cellPosition, FormulaEvaluator formulaEvaluator) {

        String value = "";
        Cell cell = row.getCell(cellPosition);

        switch (cell.getCellType()) {

            case Cell.CELL_TYPE_BOOLEAN:
                return value + cell.getBooleanCellValue();

            case Cell.CELL_TYPE_NUMERIC:
                return value + cell.getNumericCellValue();

            case Cell.CELL_TYPE_STRING:
                return cell + cell.getStringCellValue();

            default:
                return value;
        }
    }
}