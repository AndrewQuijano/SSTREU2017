package ui;

// Created by Andrew on 7/18/2017.

import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.github.chrisbanes.photoview.OnPhotoTapListener;
import com.github.chrisbanes.photoview.PhotoViewAttacher;

import Localization.ClientThread;
import edu.fiu.reu2017.R;
import sensors.WifiReceiver;
import Localization.structs.SendTrainingArray;

import static android.graphics.Color.BLUE;

public class TrainActivity extends AppCompatActivity implements Runnable
{
    private static final String TAG = "TRAIN ACTIVITY";
    private Bitmap mutableBitmap;
    private Canvas drawFlags;
    protected WifiReceiver wifi_wrapper;
    protected ImageView imageView;
    protected PhotoViewAttacher my_attach;

    //"final" variables
    private int BitMap_Height;
    private int BitMap_Width;
    private int flag_Width;
    private int flag_Height;

    // Collect Existing Flags, Ensure it is not null
    public Double [] existingX = new Double[1];
    public Double [] existingY = new Double[1];
    private Bitmap trained_flag;

    // ***********************************************************************
    public String OS, MODEL, DEVICE, PRODUCT;
    protected Thread get_flags;
    protected TrainActivity train_activity;
    protected ProgressBar loading;
    protected Button scan;

    private boolean scan_complete = false;

    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.train_activity);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        // GUI
        train_activity = this;
        // FLAG CODE = -3
        (get_flags = new Thread(new ClientThread(train_activity))).start();

        imageView = findViewById(R.id.map);
        loading = findViewById(R.id.train_bar);
        scan = findViewById(R.id.scan);

        my_attach = new PhotoViewAttacher(imageView);
        my_attach.setMaximumScale((float)7.0);

        BitmapDrawable draw_TrainingPt = (BitmapDrawable) imageView.getDrawable();
        mutableBitmap = draw_TrainingPt.getBitmap().copy(Bitmap.Config.ARGB_8888, true);
        drawFlags = new Canvas(mutableBitmap);

        wifi_wrapper = new WifiReceiver(this, loading);
        trained_flag = BitmapFactory.decodeResource(getResources(), R.drawable.bluegreen_x);

        // Misc Data
        BitMap_Width = mutableBitmap.getWidth();
        BitMap_Height = mutableBitmap.getHeight();
        OS = System.getProperty("os.version");       // OS version
        DEVICE = android.os.Build.DEVICE;            // Device
        MODEL = getDeviceName();                     // Manufactuer/Model
        PRODUCT = android.os.Build.PRODUCT;          // Product
        Log.d(TAG, "Phone Data: " + OS  +  " " + DEVICE + " " + MODEL + " " + PRODUCT);

        // Listen for new training points...
        scan.setOnClickListener(new scan());
        my_attach.setOnPhotoTapListener(new train());

        // Goal 1- Load all flags
        runOnUiThread(this);
    }

    // Show Progress bar, displaying process to finish Training
    private class scan implements View.OnClickListener
    {
        public void onClick(View v)
        {
            loading.setVisibility(View.VISIBLE);
            if(wifi_wrapper.startScan())
            {
                scan_complete = true;
                Toast.makeText(getApplicationContext(), "Got reading from Wifi Manager", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class train implements OnPhotoTapListener
    {
        public void onPhotoTap(ImageView view, float x, float y)
        {
            if(!scan_complete)
            {
                Toast.makeText(getApplicationContext(), "Please Scan First!", Toast.LENGTH_SHORT).show();
                return;
            }
            Thread t;

            //Location of press
            float drawX = x * BitMap_Width;
            float drawY = y * BitMap_Height;

            // Dimensions of Flag
            flag_Width = trained_flag.getWidth();
            flag_Height = trained_flag.getHeight();

            Log.d(TAG, "Drawing at: " + drawX + "," + "Drawing at: " + drawY);

            // FORCE A WAIT UNTIL SERVER GOT THE DATA!
            (t = new Thread(new ClientThread(new SendTrainingArray((double) drawX, (double) drawY,
                    wifi_wrapper.WifiAP, wifi_wrapper.WifiRSS, OS, DEVICE, MODEL, PRODUCT)))).start();
            try
            {
                t.join();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            // Draw the Training Flag on completion!
            drawFlags.drawBitmap(trained_flag, drawX - (float)(flag_Width/2), drawY - (float)(flag_Height/2), new Paint(BLUE));

            // Update map
            imageView.post(new Runnable()
            {
                public void run()
                {
                    imageView.setImageBitmap(mutableBitmap);
                }
            });
            my_attach.update();
            Toast.makeText(getApplicationContext(), "Training Complete! Carry on!", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "***************************************TRAINING COMPLETE!*********************************");
            scan_complete = false;
        }
    }

    // source: https://stackoverflow.com/questions/1995439/get-android-phone-model-programmatically
    // Better way to get model
    public String getDeviceName()
    {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.toLowerCase().startsWith(manufacturer.toLowerCase()))
        {
            return capitalize(model);
        }
        else
        {
            return capitalize(manufacturer) + " " + model;
        }
    }

    private String capitalize(String s)
    {
        if (s == null || s.length() == 0)
        {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first))
        {
            return s;
        }
        else
        {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }

    // Finish pre-populating the floor map with currently trained points!
    public void run()
    {
        BitMap_Width = mutableBitmap.getWidth();
        BitMap_Height = mutableBitmap.getHeight();

        // Dimensions of Flag
        flag_Width = trained_flag.getWidth();
        flag_Height = trained_flag.getHeight();

        try
        {
            get_flags.join();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        if (existingX == null)
        {
            Log.d(TAG, "EXISTING X IS NULL!!");
            return;
        }
        else
        {
            Log.d(TAG, "Retrieved Flags: size of coordinate arrays: " + existingX.length);
        }

        for (int i = 0; i < existingX.length; i++)
        {
            float drawX = existingX[i].floatValue();
            float drawY = existingY[i].floatValue();

            //Draw the Training Flag, the image is centered on where it is pressed
            drawFlags.drawBitmap(trained_flag, drawX - (float)(flag_Width/2), drawY - (float)(flag_Height/2),
                    new Paint(BLUE));
        }
        imageView.post(new Runnable()
        {
            public void run()
            {
                imageView.setImageBitmap(mutableBitmap);
            }
        });
        my_attach.update();
    }

    protected void onResume()
    {
        super.onResume();
        wifi_wrapper.registerReceiver(this);
    }

    protected void onPause()
    {
        super.onPause();
        wifi_wrapper.unregisterReceiver(this);
    }
}