package com.example.translateapp;

import static android.app.PendingIntent.getActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.icu.text.Transliterator;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.odml.image.MlImage;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    
    Spinner sourceSpinner;
    Spinner targetSpinner;
    Button translateBtn;
    EditText sourceInput;
    TextView targetOutput;
    HashMap<String, String> languages = new HashMap<String, String>();
    TextView cues;
    ImageButton swapBtn;
    ImageButton micInput;
    ImageButton cameraBtn;
    ImageButton clearBtn;
    Toolbar toolbar;
    int CAMERA_ACCESS_REQUEST_CODE = 77;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sourceInput = findViewById(R.id.sourceInput);
        targetOutput = findViewById(R.id.targetOutput);
        cues = findViewById(R.id.cues);
        swapBtn = findViewById(R.id.swapBtn);
        translateBtn = findViewById(R.id.translateBtn);
        micInput = findViewById(R.id.micInput);
        cameraBtn = findViewById(R.id.cameraBtn);
        clearBtn = findViewById(R.id.clearBtn);
        toolbar = findViewById(R.id.toolbar);

        toolbar.inflateMenu(R.menu.menu);

        populateHashMap(languages);

        // Populate spinners
        sourceSpinner = findViewById(R.id.sourceSpinner);
        targetSpinner = findViewById(R.id.targetSpinner);
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.supported_languages,
                android.R.layout.simple_spinner_item
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sourceSpinner.setAdapter(spinnerAdapter);
        targetSpinner.setAdapter(spinnerAdapter);

        // clear button action
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sourceInput.setText("");
            }
        });

        // swap button action
        swapBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int sourceItem = sourceSpinner.getSelectedItemPosition();
                int targetItem = targetSpinner.getSelectedItemPosition();
                String temp = sourceInput.getText().toString();
                sourceInput.setText(targetOutput.getText().toString());
                targetOutput.setText(temp);
                targetSpinner.setSelection(sourceItem);
                sourceSpinner.setSelection(targetItem);
            }
        });

        // speech input activity launcher
        ActivityResultLauncher<Intent> getSpeechActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if(result.getResultCode() == MainActivity.RESULT_OK) {
                            Intent data = result.getData();
                            sourceInput.setText(data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).get(0));
                        }
                    }
                }
        );

        // speech input action
        micInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
                i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Speak to convert into text");
                getSpeechActivityLauncher.launch(i);
            }
        });

        // camera action
        cameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_ACCESS_REQUEST_CODE);
            }
        });

        

        // translate button action
        translateBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String sourceLangCode = getLangCode(sourceSpinner.getSelectedItem().toString().trim());
                        String targetLangCode = getLangCode(targetSpinner.getSelectedItem().toString().trim());

                        translateText(sourceLangCode, targetLangCode, sourceInput.getText().toString());
                    }
                });
    }

    private void translateText(String sourceCode, String targetCode, String inputText) {
        cues.setText("Downloading model...");

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceCode)
                .setTargetLanguage(targetCode)
                .build();

        final Translator translator = Translation.getClient(options);
        getLifecycle().addObserver(translator);

        DownloadConditions conditions = new DownloadConditions.Builder()
                .requireWifi()
                .build();

        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        cues.setText("Translating...");
                        translator.translate(inputText)
                                .addOnSuccessListener(new OnSuccessListener<String>() {
                                    @Override
                                    public void onSuccess(String result) {
                                        cues.setText("");
                                        targetOutput.setText(result);
                                    }
                                });
                    }
                });

    }

    private void populateHashMap(HashMap<String, String> languages) {
        languages.put("AFRIKAANS", "af");
        languages.put("ALBANIAN", "sq");
        languages.put("ARABIC", "ar");
        languages.put("BELARUSIAN", "be");
        languages.put("BULGARIAN", "bg");
        languages.put("BENGALI", "bn");
        languages.put("CATALAN", "ca");
        languages.put("CHINESE", "zh");
        languages.put("CROATIAN", "hr");
        languages.put("CZECH", "cs");
        languages.put("DANISH", "da");
        languages.put("DUTCH", "nl");
        languages.put("ENGLISH", "en");
        languages.put("ESPERANTO", "eo");
        languages.put("ESTONIAN", "et");
        languages.put("FINNISH", "fi");
        languages.put("FRENCH", "fr");
        languages.put("GALICIAN", "gl");
        languages.put("GEORGIAN", "ka");
        languages.put("GERMAN", "de");
        languages.put("GREEK", "el");
        languages.put("GUJARATI", "gu");
        languages.put("HAITIAN_CREOLE", "ht");
        languages.put("HEBREW", "he");
        languages.put("HINDI", "hi");
        languages.put("HUNGARIAN", "hu");
        languages.put("ICELANDIC", "is");
        languages.put("INDONESIAN", "id");
        languages.put("IRISH", "ga");
        languages.put("ITALIAN", "it");
        languages.put("JAPANESE", "ja");
        languages.put("KANNADA", "kn");
        languages.put("KOREAN", "ko");
        languages.put("LITHUANIAN", "lt");
        languages.put("LATVIAN", "lv");
        languages.put("MACEDONIAN", "mk");
        languages.put("MARATHI", "mr");
        languages.put("MALAY", "ms");
        languages.put("MALTESE", "mt");
        languages.put("NORWEGIAN", "no");
        languages.put("PERSIAN", "fa");
        languages.put("POLISH", "pl");
        languages.put("PORTUGUESE", "pt");
        languages.put("ROMANIAN", "ro");
        languages.put("RUSSIAN", "ru");
        languages.put("SLOVAK", "sk");
        languages.put("SLOVENIAN", "sl");
        languages.put("SPANISH", "es");
        languages.put("SWEDISH", "sv");
        languages.put("SWAHILI", "sw");
        languages.put("TAGALOG", "tl");
        languages.put("TAMIL", "ta");
        languages.put("TELUGU", "te");
        languages.put("THAI", "th");
        languages.put("TURKISH", "tr");
        languages.put("UKRAINIAN", "uk");
        languages.put("URDU", "ur");
        languages.put("VIETNAMESE", "vi");
        languages.put("WELSH", "cy");
    }

    private String getLangCode(String name) {
        return languages.get(name);
    }

    private void startCamera() {}

    // camera input activity adapter
    ActivityResultLauncher<Intent> getImageActivityLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
        @Override
        public void onActivityResult(ActivityResult result) {
            if(result.getResultCode() == RESULT_OK && result.getData() != null) {
                Bundle bundle = result.getData().getExtras();
                Bitmap bitmap = (Bitmap) bundle.get("data");
                InputImage image = InputImage.fromBitmap(bitmap, 0);
                getTextFromImage(image);
            }
        }
    });

    private void getTextFromImage(InputImage image) {
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        Task<Text> result = recognizer.process(image)
                .addOnSuccessListener(new OnSuccessListener<Text>() {
                    @Override
                    public void onSuccess(Text text) {
                        if(text.getText().isEmpty()){
                            Toast.makeText(MainActivity.this, "Could not detect text. Please try again.", Toast.LENGTH_LONG).show();
                        }else{
                            StringBuilder resultText = new StringBuilder();
                            for(Text.TextBlock block: text.getTextBlocks()) {
                                String blockText = block.getText();
                                for(Text.Line line : block.getLines()) {
                                    resultText.append(line.getText());
                                    resultText.append("\n");
                                }
                            }
                            sourceInput.setText(resultText);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(MainActivity.this, "Some error occurred", Toast.LENGTH_LONG).show();
                    }
                });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == CAMERA_ACCESS_REQUEST_CODE && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            getImageActivityLauncher.launch(cameraIntent);
        }else{
            Toast.makeText(MainActivity.this, "Camera permissions not granted.", Toast.LENGTH_LONG).show();
        }

    }
}