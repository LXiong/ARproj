/*===============================================================================
Copyright (c) 2016-2017 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.mango.arproj.activity.ar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.RelativeLayout;

import com.mango.arproj.R;
import com.mango.arproj.vuforia.SampleApplicationControl;
import com.mango.arproj.vuforia.SampleApplicationException;
import com.mango.arproj.vuforia.SampleApplicationSession;
import com.mango.arproj.vuforia.utils.LoadingDialogHandler;
import com.mango.arproj.vuforia.utils.SampleApplicationGLView;
import com.mango.arproj.vuforia.utils.Texture;
import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.ImageTargetBuilder;
import com.vuforia.ObjectTracker;
import com.vuforia.State;
import com.vuforia.Trackable;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;

import java.util.ArrayList;
import java.util.Vector;


// The main activity for the ARCameraActivity sample.
public class ARCameraActivity extends Activity implements
        SampleApplicationControl
{
    private static final String LOGTAG = "ARCameraActivity";
    
    private SampleApplicationSession vuforiaAppSession;
    
    // Our OpenGL view:
    private SampleApplicationGLView mGlView;
    
    // Our renderer:
    private UserDefinedTargetRenderer mRenderer;
    
    // The textures we will use for rendering:
    private Vector<Texture> mTextures;
    
    // View overlays to be displayed in the Augmented View
    private RelativeLayout mUILayout;
    private View mBottomBar;
    private View mCameraButton;
    
    // Alert dialog for displaying SDK errors
    private AlertDialog mDialog;
    
    int targetBuilderCounter = 1;
    
    DataSet dataSetUserDef = null;
    
    private GestureDetector mGestureDetector;
    
    private ArrayList<View> mSettingsAdditionalViews;
    
    private boolean mExtendedTracking = false;
    
    private LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(
        this);
    
    RefFreeFrame refFreeFrame;
    
    // Alert Dialog used to display SDK errors
    private AlertDialog mErrorDialog;
    
    boolean mIsDroidDevice = false;
    
    
    // Called when the activity first starts or needs to be recreated after
    // resuming the application or a configuration change.
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);
        
        vuforiaAppSession = new SampleApplicationSession(this);
        
        vuforiaAppSession
            .initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        // Load any sample specific textures:
        mTextures = new Vector<Texture>();
        loadTextures();
        
        mGestureDetector = new GestureDetector(this, new GestureListener());
        
        mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith(
            "droid");

        addOverlayView(true);
    }

    // Process Single Tap event to trigger autofocus
    private class GestureListener extends
        GestureDetector.SimpleOnGestureListener
    {
        // Used to set autofocus one second after a manual focus is triggered
        private final Handler autofocusHandler = new Handler();
        
        
        @Override
        public boolean onDown(MotionEvent e)
        {
            return true;
        }
        
        
        @Override
        public boolean onSingleTapUp(MotionEvent e)
        {
            boolean result = CameraDevice.getInstance().setFocusMode(
                    CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO);
            if (!result)
                Log.e("SingleTapUp", "Unable to trigger focus");

            // Generates a Handler to trigger continuous auto-focus
            // after 1 second
            autofocusHandler.postDelayed(new Runnable()
            {
                public void run()
                {
                    final boolean autofocusResult = CameraDevice.getInstance().setFocusMode(
                            CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);

                    if (!autofocusResult)
                        Log.e("SingleTapUp", "Unable to re-enable continuous auto-focus");
                }
            }, 1000L);
            
            return true;
        }
    }
    
    
    // We want to load specific textures from the APK, which we will later use
    // for rendering.
    private void loadTextures()
    {
        mTextures.add(Texture.loadTextureFromApk("TextureTeapotBlue.png",
            getAssets()));
    }
    
    
    // Called when the activity will start interacting with the user.
    @Override
    protected void onResume()
    {
        Log.d(LOGTAG, "onResume");
        super.onResume();

        showProgressIndicator(true);
        
        // This is needed for some Droid devices to force portrait
        if (mIsDroidDevice)
        {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        vuforiaAppSession.onResume();
    }
    
    
    // Called when the system is about to start resuming a previous activity.
    @Override
    protected void onPause()
    {
        Log.d(LOGTAG, "onPause");
        super.onPause();
        
        if (mGlView != null)
        {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }
        
        try
        {
            vuforiaAppSession.pauseAR();
        } catch (SampleApplicationException e)
        {
            Log.e(LOGTAG, e.getString());
        }
    }
    
    
    // The final call you receive before your activity is destroyed.
    @Override
    protected void onDestroy()
    {
        Log.d(LOGTAG, "onDestroy");
        super.onDestroy();
        
        try
        {
            vuforiaAppSession.stopAR();
        } catch (SampleApplicationException e)
        {
            Log.e(LOGTAG, e.getString());
        }
        
        // Unload texture:
        mTextures.clear();
        mTextures = null;
        
        System.gc();
    }
    
    
    // Callback for configuration changes the activity handles itself
    @Override
    public void onConfigurationChanged(Configuration config)
    {
        Log.d(LOGTAG, "onConfigurationChanged");
        super.onConfigurationChanged(config);
        
        vuforiaAppSession.onConfigurationChanged();
        
        // Removes the current layout and inflates a proper layout
        // for the new screen orientation
        
        if (mUILayout != null)
        {
            mUILayout.removeAllViews();
            ((ViewGroup) mUILayout.getParent()).removeView(mUILayout);
            
        }
        
        addOverlayView(false);
    }
    
    
    // Shows error message in a system dialog box
    private void showErrorDialog()
    {
        if (mDialog != null && mDialog.isShowing())
            mDialog.dismiss();
        
        mDialog = new AlertDialog.Builder(ARCameraActivity.this).create();
        DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int which)
            {
                dialog.dismiss();
            }
        };
        
        mDialog.setButton(DialogInterface.BUTTON_POSITIVE,
            "ok", clickListener);
        
        mDialog.setTitle("low quality");
        
        String message = "low quality";
        
        // Show dialog box with error message:
        mDialog.setMessage(message);
        mDialog.show();
    }
    
    
    // Shows error message in a system dialog box on the UI thread
    void showErrorDialogInUIThread()
    {
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                showErrorDialog();
            }
        });
    }
    
    
    // Initializes AR application components.
    private void initApplicationAR()
    {
        // Do application initialization
        refFreeFrame = new RefFreeFrame(this, vuforiaAppSession);
        refFreeFrame.init();
        
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();
        
        mGlView = new SampleApplicationGLView(this);
        mGlView.init(translucent, depthSize, stencilSize);
        
        mRenderer = new UserDefinedTargetRenderer(this, vuforiaAppSession);
        mRenderer.setTextures(mTextures);
        mGlView.setRenderer(mRenderer);
    }
    
    
    // Adds the Overlay view to the GLView
    private void addOverlayView(boolean initLayout)
    {
        // Inflates the Overlay Layout to be displayed above the Camera View
        LayoutInflater inflater = LayoutInflater.from(this);
        mUILayout = (RelativeLayout) inflater.inflate(
            R.layout.activity_ar_camera, null, false);
        
        mUILayout.setVisibility(View.VISIBLE);
        
        // If this is the first time that the application runs then the
        // uiLayout background is set to BLACK color, will be set to
        // transparent once the SDK is initialized and camera ready to draw
        if (initLayout)
        {
            mUILayout.setBackgroundColor(Color.BLACK);
        }
        
        // Adds the inflated layout to the view
        addContentView(mUILayout, new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT));
        
        // Gets a reference to the bottom navigation bar
        mBottomBar = mUILayout.findViewById(R.id.bottom_bar);
        
        // Gets a reference to the Camera button
        mCameraButton = mUILayout.findViewById(R.id.camera_button);
        
        // Gets a reference to the loading dialog container
        loadingDialogHandler.mLoadingDialogContainer = mUILayout
            .findViewById(R.id.loading_layout);

        initializeBuildTargetModeViews();
        
        mUILayout.bringToFront();
    }
    
    
    // Button Camera clicked
    public void onCameraClick(View v)
    {
        if (isUserDefinedTargetsRunning())
        {
            // Shows the loading dialog
            loadingDialogHandler
                .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);
            
            // Builds the new target
            startBuild();
        }
    }
    
    
    // Creates a texture given the filename
    Texture createTexture(String nName)
    {
        return Texture.loadTextureFromApk(nName, getAssets());
    }
    
    
    // Callback function called when the target creation finished
    void targetCreated()
    {
        // Hides the loading dialog
        loadingDialogHandler
            .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
        
        if (refFreeFrame != null)
        {
            refFreeFrame.reset();
        }
        
    }
    
    
    // Initialize views
    private void initializeBuildTargetModeViews()
    {
        // Shows the bottom bar
        mBottomBar.setVisibility(View.VISIBLE);
        mCameraButton.setVisibility(View.VISIBLE);
    }

    
    
    boolean startUserDefinedTargets()
    {
        Log.d(LOGTAG, "startUserDefinedTargets");
        
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) (trackerManager
            .getTracker(ObjectTracker.getClassType()));
        if (objectTracker != null)
        {
            ImageTargetBuilder targetBuilder = objectTracker
                .getImageTargetBuilder();
            
            if (targetBuilder != null)
            {
                // if needed, stop the target builder
                if (targetBuilder.getFrameQuality() != ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_NONE)
                    targetBuilder.stopScan();
                
                objectTracker.stop();
                
                targetBuilder.startScan();
                
            }
        } else
            return false;
        
        return true;
    }
    
    
    boolean isUserDefinedTargetsRunning()
    {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
            .getTracker(ObjectTracker.getClassType());
        
        if (objectTracker != null)
        {
            ImageTargetBuilder targetBuilder = objectTracker
                .getImageTargetBuilder();
            if (targetBuilder != null)
            {
                Log.e(LOGTAG, "Quality> " + targetBuilder.getFrameQuality());
                return (targetBuilder.getFrameQuality() != ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_NONE) ? true
                    : false;
            }
        }
        
        return false;
    }
    
    
    void startBuild()
    {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
            .getTracker(ObjectTracker.getClassType());
        
        if (objectTracker != null)
        {
            ImageTargetBuilder targetBuilder = objectTracker
                .getImageTargetBuilder();
            if (targetBuilder != null)
            {
                if (targetBuilder.getFrameQuality() == ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_LOW)
                {
                     showErrorDialogInUIThread();
                }
                
                String name;
                do
                {
                    name = "UserTarget-" + targetBuilderCounter;
                    Log.d(LOGTAG, "TRYING " + name);
                    targetBuilderCounter++;
                } while (!targetBuilder.build(name, 320.0f));
                
                refFreeFrame.setCreating();
            }
        }
    }
    
    
    void updateRendering()
    {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        refFreeFrame.initGL(metrics.widthPixels, metrics.heightPixels);
    }
    
    
    @Override
    public boolean doInitTrackers()
    {
        // Indicate if the trackers were initialized correctly
        boolean result = true;
        
        // Initialize the image tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        Tracker tracker = trackerManager.initTracker(ObjectTracker
            .getClassType());
        if (tracker == null)
        {
            Log.d(LOGTAG, "Failed to initialize ObjectTracker.");
            result = false;
        } else
        {
            Log.d(LOGTAG, "Successfully initialized ObjectTracker.");
        }
        
        return result;
    }
    
    
    @Override
    public boolean doLoadTrackersData()
    {
        // Get the image tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
            .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
        {
            Log.d(
                LOGTAG,
                "Failed to load tracking data set because the ObjectTracker has not been initialized.");
            return false;
        }
        
        // Create the data set:
        dataSetUserDef = objectTracker.createDataSet();
        if (dataSetUserDef == null)
        {
            Log.d(LOGTAG, "Failed to create a new tracking data.");
            return false;
        }
        
        if (!objectTracker.activateDataSet(dataSetUserDef))
        {
            Log.d(LOGTAG, "Failed to activate data set.");
            return false;
        }
        
        Log.d(LOGTAG, "Successfully loaded and activated data set.");
        return true;
    }
    
    
    @Override
    public boolean doStartTrackers()
    {
        // Indicate if the trackers were started correctly
        boolean result = true;
        
        Tracker objectTracker = TrackerManager.getInstance().getTracker(
            ObjectTracker.getClassType());
        if (objectTracker != null)
            objectTracker.start();
        
        return result;
    }
    
    
    @Override
    public boolean doStopTrackers()
    {
        // Indicate if the trackers were stopped correctly
        boolean result = true;
        
        Tracker objectTracker = TrackerManager.getInstance().getTracker(
            ObjectTracker.getClassType());
        if (objectTracker != null)
            objectTracker.stop();
        
        return result;
    }
    
    
    @Override
    public boolean doUnloadTrackersData()
    {
        // Indicate if the trackers were unloaded correctly
        boolean result = true;
        
        // Get the image tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
            .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
        {
            result = false;
            Log.d(
                LOGTAG,
                "Failed to destroy the tracking data set because the ObjectTracker has not been initialized.");
        }
        
        if (dataSetUserDef != null)
        {
            if (objectTracker.getActiveDataSet(0) != null
                && !objectTracker.deactivateDataSet(dataSetUserDef))
            {
                Log.d(
                    LOGTAG,
                    "Failed to destroy the tracking data set because the data set could not be deactivated.");
                result = false;
            }
            
            if (!objectTracker.destroyDataSet(dataSetUserDef))
            {
                Log.d(LOGTAG, "Failed to destroy the tracking data set.");
                result = false;
            }
            
            Log.d(LOGTAG, "Successfully destroyed the data set.");
            dataSetUserDef = null;
        }
        
        return result;
    }
    
    
    @Override
    public boolean doDeinitTrackers()
    {
        // Indicate if the trackers were deinitialized correctly
        boolean result = true;
        
        if (refFreeFrame != null)
            refFreeFrame.deInit();
        
        TrackerManager tManager = TrackerManager.getInstance();
        tManager.deinitTracker(ObjectTracker.getClassType());
        
        return result;
    }


    @Override
    public void onVuforiaResumed()
    {
        if (mGlView != null)
        {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }
    }
    
    @Override
    public void onInitARDone(SampleApplicationException exception)
    {
        
        if (exception == null)
        {
            initApplicationAR();

            mRenderer.setActive(true);

            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
            
            // Sets the UILayout to be drawn in front of the camera
            mUILayout.bringToFront();
            
            // Hides the Loading Dialog
            loadingDialogHandler
                .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
            
            // Sets the layout background to transparent
            mUILayout.setBackgroundColor(Color.TRANSPARENT);
            
            vuforiaAppSession.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT);
            
        } else
        {
            Log.e(LOGTAG, exception.toString());
            showInitializationErrorMessage(exception.getString());
        }
    }


    @Override
    public void onVuforiaStarted()
    {
        mRenderer.updateRenderingPrimitives();

        startUserDefinedTargets();
        
        // Set camera focus mode
        if(!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO))
        {
            // If continuous autofocus mode fails, attempt to set to a different mode
            if(!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO))
            {
                CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);
            }
        }

        showProgressIndicator(false);
    }

    public void showProgressIndicator(boolean show)
    {
        if (loadingDialogHandler != null)
        {
            if (show)
            {
                loadingDialogHandler
                        .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);
            }
            else
            {
                loadingDialogHandler
                        .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
            }
        }
    }

    // Shows initialization error messages as System dialogs
    public void showInitializationErrorMessage(String message)
    {
        final String errorMessage = message;
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                if (mErrorDialog != null)
                {
                    mErrorDialog.dismiss();
                }
                
                // Generates an Alert Dialog to show the error message
                AlertDialog.Builder builder = new AlertDialog.Builder(
                    ARCameraActivity.this);
                builder
                    .setMessage(errorMessage)
                    .setTitle("初始化失败")
                    .setCancelable(false)
                    .setIcon(0)
                    .setPositiveButton("ok",
                        new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int id)
                            {
                                finish();
                            }
                        });
                
                mErrorDialog = builder.create();
                mErrorDialog.show();
            }
        });
    }
    
    
    @Override
    public void onVuforiaUpdate(State state)
    {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
            .getTracker(ObjectTracker.getClassType());
        
        if (refFreeFrame.hasNewTrackableSource())
        {
            Log.d(LOGTAG,
                "Attempting to transfer the trackable source to the dataset");
            
            // Deactivate current dataset
            objectTracker.deactivateDataSet(objectTracker.getActiveDataSet(0));
            
            // Clear the oldest target if the dataset is full or the dataset
            // already contains five user-defined targets.
            if (dataSetUserDef.hasReachedTrackableLimit()
                || dataSetUserDef.getNumTrackables() >= 5)
                dataSetUserDef.destroy(dataSetUserDef.getTrackable(0));
            
            if (mExtendedTracking && dataSetUserDef.getNumTrackables() > 0)
            {
                // We need to stop the extended tracking for the previous target
                // so we can enable it for the new one
                int previousCreatedTrackableIndex = 
                    dataSetUserDef.getNumTrackables() - 1;
                
                objectTracker.resetExtendedTracking();
                dataSetUserDef.getTrackable(previousCreatedTrackableIndex)
                    .stopExtendedTracking();
            }
            
            // Add new trackable source
            Trackable trackable = dataSetUserDef
                .createTrackable(refFreeFrame.getNewTrackableSource());
            
            // Reactivate current dataset
            objectTracker.activateDataSet(dataSetUserDef);
            
            if (mExtendedTracking)
            {
                trackable.startExtendedTracking();
            }
            
        }
    }

    
}
