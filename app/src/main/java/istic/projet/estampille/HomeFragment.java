package istic.projet.estampille;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HomeFragment extends Fragment implements View.OnClickListener {

    private static final int REQUEST_IMAGE1_CAPTURE = 1;
    private static final Pattern normalStamp = Pattern.compile("[0-9][0-9][.][0-9][0-9][0-9][.][0-9][0-9][0-9]");
    private static final Pattern domTomStamp = Pattern.compile("[0-9][0-9][0-9][.][0-9][0-9][0-9][.][0-9][0-9][0-9]");
    private static final Pattern corsicaStamp = Pattern.compile("[0-9](A|B)[.][0-9][0-9][0-9][.][0-9][0-9][0-9]");
    protected String mCurrentPhotoPath;
    private ProgressDialog mProgressDialog;
    private Context context;
    private Uri photoURI1;
    private Uri oldPhotoURI;
    private ViewGroup containerView;
    private boolean success;
    private ViewPager viewPager;
    private int OCRcounter = 0;
    private ImageButton scanTile;
    private ImageButton searchTile;
    private ImageButton historyTile;
    private ImageButton lookAroundTile;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        ViewGroup rootView = (ViewGroup) inflater.inflate(
                R.layout.fragment_home, container, false);
        context = rootView.getContext();
        this.containerView = rootView;
        viewPager = getActivity().findViewById(R.id.pager);
        scanTile = rootView.findViewById(R.id.tile_scan);
        searchTile = rootView.findViewById(R.id.tile_search);
        historyTile = rootView.findViewById(R.id.tile_history);
        lookAroundTile = rootView.findViewById(R.id.tile_look_around);
        scanTile.setOnClickListener(this);
        searchTile.setOnClickListener(this);
        historyTile.setOnClickListener(this);
        lookAroundTile.setOnClickListener(this);


        return rootView;
    }

    @Override
    public void onClick(View view) {
        if(view.getId() == R.id.tile_scan) {
            if (this.getActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                PermissionsUtils.checkPermission(this, containerView, new String[]{Manifest.permission.CAMERA}, "La caméra est nécessaire pour scanner les estampilles", PermissionsUtils.REQUEST_CODE_PERMISSION_CAMERA);
            } else {
                openCamera();
            }
        }
        else if(view.getId() == R.id.tile_search) {
            viewPager.setCurrentItem(1);
        }
        else if(view.getId() == R.id.tile_history) {
            viewPager.setCurrentItem(2);
        }
        else if(view.getId() == R.id.tile_look_around) {
            viewPager.setCurrentItem(3);
        }
    }

    /**
     * Opens camera.
     */
    private void openCamera() {
        //Intent to open the camera
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(context.getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(context, R.string.file_creation_fail_toast, Toast.LENGTH_SHORT).show();
                Log.i("File error", ex.toString());
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                oldPhotoURI = photoURI1;
                photoURI1 = Uri.fromFile(photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI1);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE1_CAPTURE);
            }
        }
    }

    /**
     * Creates the image file.
     *
     * @return A file which represent the image of the stamp
     * @throws IOException Throws an {@link IOException} if something goes wrong in the file creation process.
     */
    public File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("MMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //Calls after that user takes a photo
        if (resultCode == Activity.RESULT_OK) {
            Bitmap bmp = null;
            try {
                //Creates a bitmap from the stamp image
                InputStream is = context.getContentResolver().openInputStream(photoURI1);
                BitmapFactory.Options options = new BitmapFactory.Options();
                bmp = BitmapFactory.decodeStream(is, null, options);
                is.close();

            } catch (Exception ex) {
                Log.i(getClass().getSimpleName(), ex.getMessage());
                Toast.makeText(context, R.string.conversion_fail_toast, Toast.LENGTH_SHORT).show();
            }
            //Starts the stamp recognition
            doOCR(bmp);
            OutputStream os;
            try {
                os = new FileOutputStream(photoURI1.getPath());
                if (bmp != null) {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 100, os);
                }
                os.flush();
                os.close();
            } catch (Exception ex) {
                Log.e(getClass().getSimpleName(), ex.getMessage());
                Toast.makeText(context, R.string.file_creation_fail_toast, Toast.LENGTH_SHORT).show();
            }
        } else {
            photoURI1 = oldPhotoURI;
        }
    }


    /**
     * Recognizes the text from the bitmap in parameter.
     *
     * @param bitmap the stamp image
     */
    private void doOCR(final Bitmap bitmap) {
        //Open a waiting pop up during the treatment
        OCRcounter = 0;
        mProgressDialog = new ProgressDialog(getActivity(), R.style.FoodOriginAlertDialog);
        mProgressDialog.setMessage(getString(R.string.ocr_dialog_message));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.show();
        success = false;
        int rotationDegree = 90;
        TextRecognizer recognizer = TextRecognition.getClient();
        for (int i = 0; i < 4; i++) {
            InputImage image = InputImage.fromBitmap(bitmap, rotationDegree * i);
            final Task<Text> result =
                    recognizer.process(image)
                            .addOnSuccessListener(new OnSuccessListener<Text>() {
                                @Override
                                public void onSuccess(Text visionText) {
                                    List<Text.TextBlock> recognizedText = visionText.getTextBlocks();
                                    success = extractCode(recognizedText);
                                    mProgressDialog.cancel();
                                    imageResult(success);
                                }
                            })
                            .addOnFailureListener(
                                    new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                        }
                                    });

        }
    }

    /**
     * Sets the ocr in the text input inside {@link WritePackagingNumberFragment} if the recognition has been successful.
     *
     * @param ocrSuccess indicates if the recognition has been successful (true) or not (false).
     */
    private void imageResult(boolean ocrSuccess) {

        if (!ocrSuccess) {
            OCRcounter++;
            if (OCRcounter == 4) {
                Toast.makeText(context, R.string.recognition_fail_toast, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Extracts the packaging code from the text recognized on the picture.
     *
     * @param recognizedText recognized text
     * @return true if a character chain matching with the packaging number regex has been found, false otherwise
     */
    private boolean extractCode(List<Text.TextBlock> recognizedText) {
        boolean found = false;
        Text.TextBlock t;
        Iterator it = recognizedText.iterator();
        String tempText = null;
        //Finds the group of text containing the string
        while (!found && it.hasNext()) {
            t = (Text.TextBlock) it.next();
            tempText = t.getText().replace("(", "");
            tempText = tempText.replace(")", "");
            if (tempText.matches("(?s).*[0-9][0-9][.][0-9][0-9][0-9][.][0-9][0-9][0-9].*") || tempText.matches("(?s).*[0-9][0-9][0-9][.][0-9][0-9][0-9][.][0-9][0-9][0-9].*") || tempText.matches("(?s).*[0-9](A|B)[.][0-9][0-9][0-9][.][0-9][0-9][0-9].*")) {
                found = true;
            }
        }
        if (found) {
            //Removes spare characters from recognized string
            Matcher normalMatcher = normalStamp.matcher(tempText);
            Matcher domTomMatcher = domTomStamp.matcher(tempText);
            Matcher corsicaMatcher = corsicaStamp.matcher(tempText);
            if(normalMatcher.find()) {
                this.readCsv(normalMatcher.group(0));
                //editText.setText(normalMatcher.group(0));
            }
            else if (domTomMatcher.find()) {
                this.readCsv(domTomMatcher.group(0));
                //editText.setText(domTomMatcher.group(0));
            }
            else if (corsicaMatcher.find()) {
                this.readCsv(corsicaMatcher.group(0));
                //editText.setText(corsicaMatcher.group(0));
            }
        }
        return found;
    }

    private void readCsv(String result) {
//        InputStream is = getResources().openRawResource(R.raw.bdd_test);
        InputStream is = null;
        try {
            is = new FileInputStream(getActivity().getApplicationContext().getFilesDir().toString()
                    + "/foodorigin_datagouv.txt");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        boolean find = false;
        String txt = "";

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8)
        );

        //Recover the stamp in the text field
        txt = result;

        String line = "";

        try {
            while ((line = reader.readLine()) != null) {
                String[] tab = line.split(";");
                if (txt.equals(tab[0])) {
                    String fileName = "historyFile.txt";
                    try {
                        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(getActivity().openFileOutput(fileName, Context.MODE_APPEND)));
                        bw.write(tab[0] + ";" + tab[1]+ ";" + tab[2]+";" + tab[3]+";" + tab[4]+ ";" + tab[5] + ";" + tab[6] + "\n");
                        bw.close();
                    } catch (Exception e) {
                        Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                    }

                    Intent intent = new Intent(context, DisplayMap.class);
                    Bundle mapBundle = new Bundle();
                    mapBundle.putStringArray("Infos", tab);
                    intent.putExtras(mapBundle);
                    startActivity(intent);
                    find = true;
                }
            }

        } catch (IOException e){
            Log.wtf("Erreur dans la lecture du CSV " + line, e);
            e.printStackTrace();
        }

        //If the stamp has no similarity in the CSV, a message error appears
        if (!find) {
            Toast.makeText(context, context.getString(R.string.no_match_toast), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PermissionsUtils.REQUEST_CODE_PERMISSION_CAMERA) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else if (!shouldShowRequestPermissionRationale(permissions[0])) {
                PermissionsUtils.displayOptions(this.getActivity(), containerView, PermissionsUtils.permission_camera_params);
            } else {
                PermissionsUtils.explain(this.getActivity(), containerView, permissions[0], requestCode, PermissionsUtils.permission_camera_explain);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}